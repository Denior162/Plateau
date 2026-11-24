package com.zghonnyk.plateau.ui.state.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zghonnyk.domain.ActiveDryingPhase
import com.zghonnyk.domain.AnalyzedPhase
import com.zghonnyk.domain.DryingConfig
import com.zghonnyk.domain.DryingForecast
import com.zghonnyk.domain.Measurement
import com.zghonnyk.domain.MeasurementId
import com.zghonnyk.domain.PlantId
import com.zghonnyk.domain.PlantTemporalEvent
import com.zghonnyk.domain.PotId
import com.zghonnyk.domain.PruningEvent
import com.zghonnyk.domain.PruningEventId
import com.zghonnyk.domain.RepottingEvent
import com.zghonnyk.domain.RepottingEventId
import com.zghonnyk.domain.Tare
import com.zghonnyk.domain.WateringEvent
import com.zghonnyk.domain.WateringEventId
import com.zghonnyk.domain.allMeasurementsFlattened
import com.zghonnyk.domain.analyzeDrying
import com.zghonnyk.domain.daysUntilPlateauAt
import com.zghonnyk.domain.latestMoisture
import com.zghonnyk.domain.predictWeight
import com.zghonnyk.domain.splitIntoPhases
import com.zghonnyk.domain.timeline
import com.zghonnyk.domain.withRates
import com.zghonnyk.plateau.PlateauApplication
import com.zghonnyk.plateau.data.db.PlateauDatabase
import com.zghonnyk.plateau.data.repository.PotChoice.New
import com.zghonnyk.plateau.data.repository.deleteMeasurement
import com.zghonnyk.plateau.data.repository.exportRows
import com.zghonnyk.plateau.data.repository.fetchAllSpecies
import com.zghonnyk.plateau.data.repository.insertNewMeasurement
import com.zghonnyk.plateau.data.repository.insertNewPruning
import com.zghonnyk.plateau.data.repository.insertNewRepotting
import com.zghonnyk.plateau.data.repository.insertNewWatering
import com.zghonnyk.plateau.data.repository.observeAllMeasurements
import com.zghonnyk.plateau.data.repository.observeAllPhotos
import com.zghonnyk.plateau.data.repository.observeAllPrunings
import com.zghonnyk.plateau.data.repository.observeAllRepottings
import com.zghonnyk.plateau.data.repository.observeAllWaterings
import com.zghonnyk.plateau.data.repository.observePlantsWithSpecies
import com.zghonnyk.plateau.data.repository.removePlant
import com.zghonnyk.plateau.data.repository.updateMeasurement
import com.zghonnyk.plateau.data.repository.updatePlant
import com.zghonnyk.plateau.di.PlantDeps
import com.zghonnyk.plateau.domain.importBackup
import com.zghonnyk.plateau.domain.parseBackup
import com.zghonnyk.plateau.domain.toCsv
import com.zghonnyk.plateau.feature.main.R
import com.zghonnyk.plateau.feature.main.ui.components.ChartPoint
import com.zghonnyk.plateau.feature.main.ui.extentions.toMonthGroupUiModels
import com.zghonnyk.plateau.feature.main.ui.state.Cmd
import com.zghonnyk.plateau.feature.main.ui.state.PendingDelete
import com.zghonnyk.plateau.feature.main.ui.state.PlantFacts
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent.Creating.AddPhotoCopied
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent.Loaded.CreatingSpecies
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent.Loaded.EditingSpecies
import com.zghonnyk.plateau.feature.main.ui.state.PlantState
import com.zghonnyk.plateau.feature.main.ui.state.PlantUi
import com.zghonnyk.plateau.feature.main.ui.state.applyPlants
import com.zghonnyk.plateau.feature.main.ui.state.update
import com.zghonnyk.plateau.feature.main.ui.state.updateDeletions
import com.zghonnyk.plateau.ui.state.viewmodel.PlantsViewModel.PlantEffect.CopyToClipboard
import com.zghonnyk.plateau.ui.state.viewmodel.PlantsViewModel.PlantEffect.ShowMessage
import com.zghonnyk.plateau.ui.state.viewmodel.PlantsViewModel.UiText.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.Instant.now
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration


class PlantsViewModel(
    private val database: PlateauDatabase,
    private val deps: PlantDeps,
    initialNow: Instant,
) : ViewModel() {
    private val _now = MutableStateFlow(initialNow)
    private val _uiState = MutableStateFlow<PlantState>(PlantState.DBUninitialized)
    val uiState: StateFlow<PlantState> = _uiState.asStateFlow()

    sealed interface UiText {
        data class DynamicString(val value: String) : UiText
        data class Resource(
            val resId: Int, val args: List<Any> = emptyList()
        ) : UiText

        fun asString(context: android.content.Context): String = when (this) {
            is DynamicString -> value
            is Resource -> context.getString(resId, *args.toTypedArray())
        }
    }

    sealed interface PlantEffect {
        data class ShowMessage(val text: UiText) : PlantEffect
        data class CopyToClipboard(val label: String, val text: String) : PlantEffect
    }

    private val _effects = Channel<PlantEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onIntent(intent: PlantIntent, now: Instant) {
        _now.value = now.truncatedTo(ChronoUnit.MINUTES)
        handleIntent(intent)
    }
    private val _pendingDeletes = MutableStateFlow<List<PendingDelete>>(emptyList())
    val pendingDeletes: StateFlow<List<PendingDelete>> = _pendingDeletes.asStateFlow()
    private val timers = mutableMapOf<PlantId, Job>()

    fun handleIntent(intent: PlantIntent) =
        if (intent is PlantIntent.Overview.Deletion) dispatchDeletion(intent) else dispatch(intent)

    private fun dispatchDeletion(intent: PlantIntent.Overview.Deletion) {
        val (pending, cmd) = updateDeletions(_pendingDeletes.value, intent, now().toEpochMilli())
        _pendingDeletes.value = pending
        run(cmd)
    }
    private fun exportCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg: UiText = try {
                val out = deps.openOut(uri) ?: return@launch _effects.send(
                    ShowMessage(
                        Resource(R.string.error_cannot_open_file)
                    )
                )
                out.bufferedWriter(Charsets.UTF_8).use { it.write(toCsv(exportRows(database))) }
                Resource(R.string.export_ready)
            } catch (e: Exception) {
                Resource(R.string.export_error, listOf(e.message.orEmpty()))
            }
            _effects.send(ShowMessage(msg))
        }
    }

    fun importCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg: UiText = try {
                val input = deps.openIn(uri) ?: return@launch _effects.send(
                    ShowMessage(
                        Resource(R.string.error_cannot_open_file)
                    )
                )
                val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val summary = importBackup(database, parseBackup(text))
                Resource(
                    R.string.import_success, listOf(
                        summary.plantsAdded, summary.measurementsAdded, summary.measurementsSkipped
                    )
                )
            } catch (e: Exception) {
                Resource(R.string.import_error, listOf(e.message.orEmpty()))
            }
            _effects.send(ShowMessage(msg))
        }
    }

    private val phaseAnalysisCache =
        java.util.concurrent.ConcurrentHashMap<List<Measurement>, DryingForecast>()

    private fun analyzeCached(measurements: List<Measurement>): DryingForecast {
        if (measurements.isEmpty()) return DryingForecast.InsufficientData(null)
        return phaseAnalysisCache.getOrPut(measurements) {
            ActiveDryingPhase(measurements).analyzeDrying(DryingConfig())
        }
    }
    private val plantsWithPhotos = combine(
        observePlantsWithSpecies(database),
        observeAllPhotos(database),
    ) { plants, photos -> plants to photos.groupBy { it.plant_id } }

    private val plants: Flow<List<PlantFacts>> = combine(
        plantsWithPhotos,
        observeAllMeasurements(database),
        observeAllWaterings(database),
        observeAllPrunings(database),
        observeAllRepottings(database),
    ) { (rawPlants, photosByPlant), allMeasurements, allWaterings, allPrunings, allRepottings ->
        val measurementsByPlant = allMeasurements.groupBy { it.plant_id }
        val wateringByPlant = allWaterings.groupBy { it.plant_id }
        val pruningByPlant = allPrunings.groupBy { it.plant_id }
        val repottingsByPlant = allRepottings.groupBy { it.plant_id }
        rawPlants.map { plant ->
            val plantId = PlantId(plant.id)
            val measurements = measurementsByPlant[plant.id].orEmpty().map { dbMeasurement ->
                Measurement(
                    id = MeasurementId(dbMeasurement.id),
                    plantId = plantId,
                    timestamp = Instant.ofEpochMilli(dbMeasurement.timestamp),
                    weightGrams = dbMeasurement.weight_grams
                )
            }
            val waterings: List<PlantTemporalEvent> = wateringByPlant[plant.id].orEmpty().map {
                WateringEvent(
                    id = WateringEventId(it.id),
                    plantId = plantId,
                    timestamp = Instant.ofEpochMilli(it.timestamp)
                )
            }

            val prunings: List<PlantTemporalEvent> = pruningByPlant[plant.id].orEmpty().map {
                PruningEvent(
                    id = PruningEventId(it.id),
                    plantId = plantId,
                    timestamp = Instant.ofEpochMilli(it.timestamp),
                    removedGrams = it.removed_grams
                )
            }

            val repottings: List<PlantTemporalEvent> = repottingsByPlant[plant.id].orEmpty().map {
                RepottingEvent(
                    RepottingEventId(it.id),
                    plantId,
                    Instant.ofEpochMilli(it.timestamp),
                    PotId(it.pot_id)
                )
            }
            val events = (waterings + prunings + repottings).sortedBy { it.timestamp }
            val hasWatering = waterings.isNotEmpty()

            val phases = splitIntoPhases(measurements, events)

            val analyzedPhases = phases.map { phase ->
                AnalyzedPhase(phase, analyzeCached(phase.measurements))
            }
            val pot: Tare =
                repottingsByPlant[plant.id]?.lastOrNull()?.empty_mass_grams?.let { Tare.Known(it) }
                    ?: Tare.Unknown
            val currentAnalyzed = analyzedPhases.lastOrNull()
            val activeMeasurements = currentAnalyzed?.phase?.measurements.orEmpty()
            val forecast = currentAnalyzed?.forecast ?: DryingForecast.InsufficientData(null)

            val lastWeighedAt = phases.allMeasurementsFlattened().lastOrNull()?.timestamp

            val records = if (activeMeasurements.isNotEmpty()) {
                activeMeasurements.withRates().toMonthGroupUiModels(ZoneId.systemDefault())
            } else {
                emptyList()
            }

            val plantPhoto = photosByPlant[plant.id]?.maxByOrNull { it.timestamp }?.file_path

            PlantFacts(
                id = plantId,
                name = plant.plant_name,
                latinName = plant.species_name ?: "Incertae sedis",
                records = records,
                dryingForecast = forecast,
                lastWeighedAt = lastWeighedAt,
                speciesId = plant.species_id,
                hasWatering = hasWatering,
                activeMeasurementPoints = activeMeasurements.map {
                    ChartPoint(timestamp = it.timestamp.toEpochMilli(), grams = it.weightGrams)
                },
                activeMeasurements = activeMeasurements,
                readyAt = readyAt(lastWeighedAt, forecast.recommendedInterval),

                analyzedPhases.latestMoisture(pot),
                timeline(phases, events),
                plantPhoto = plantPhoto
            )
        }
            .sortedWith(compareBy<PlantFacts> { it.records.isNotEmpty() }.thenBy(nullsLast()) { it.readyAt }
                .thenBy {
                    (it.dryingForecast as? DryingForecast.Active)?.daysUntilPlateau ?: Int.MAX_VALUE
                })
    }.flowOn(Dispatchers.Default)

    init {
        viewModelScope.launch {
            combine(plants, _now) { facts, now ->
                facts.map {
                    it.withTime(
                        now, DryingConfig().epsilon
                    )
                }
            }.collect { uiPlants ->
                _uiState.update { current -> applyPlants(current, uiPlants) }
            }
        }
    }

    private fun PlantFacts.withTime(now: Instant, epsilon: Double) = PlantUi(
        id = id,
        name = name,
        latinName = latinName,
        records = records,
        dryingForecast = dryingForecast,
        lastWeighedAt = lastWeighedAt,
        speciesId = speciesId,
        hasWatering = hasWatering,
        activeMeasurementPoints = activeMeasurementPoints,
        activeMeasurements = activeMeasurements,
        hoursLeft = readyAt?.let { hoursBetween(now, it) },
        isReadyForAction = readyAt?.let { !it.isAfter(now) } ?: false,
        plateauInDays = (dryingForecast as? DryingForecast.Active)?.expFit?.daysUntilPlateauAt(
            now, epsilon
        ),
        moisture = moisture,
        prediction = predictWeight(history = activeMeasurements, at = now, DryingConfig()),
        timeline,
        plantPhoto)

    private fun hoursBetween(from: Instant, to: Instant) =
        java.time.Duration.between(from, to).toHours()

    private fun readyAt(lastWeighedAt: Instant?, interval: Duration?): Instant? =
        lastWeighedAt?.let { last -> interval?.let { last.plus(it.toJavaDuration()) } }


    private fun dispatch(intent: PlantIntent) {
        val s = _uiState.value as? PlantState.Active ?: return
        val (next, cmd) = update(s, intent, now())
        _uiState.value = next
        run(cmd)
    }

    private fun run(cmd: Cmd) {
        when (cmd) {
            Cmd.None -> Unit
            is Cmd.ExportCsv -> exportCsv(cmd.uri)
            is Cmd.ImportCsv -> importCsv(cmd.uri)
            is Cmd.SavePruning -> viewModelScope.launch(Dispatchers.IO) {
                insertNewPruning(database, cmd.plantId.value, cmd.timestampMs, cmd.removedGrams)
            }

            is Cmd.SaveRepotting -> viewModelScope.launch(Dispatchers.IO) {
                insertNewRepotting(
                    db = database,
                    plantId = cmd.plantId.value,
                    timestamp = cmd.timestampMs,
                    pot = New(cmd.potName, null),
                    weightAfterGrams = cmd.weightAfterGrams,
                )
            }

            is Cmd.SaveMeasurement -> viewModelScope.launch(Dispatchers.IO) {
                insertNewMeasurement(database, cmd.plantId.value, cmd.timestampMs, cmd.weightGrams)
            }

            is Cmd.SaveWatering -> viewModelScope.launch(Dispatchers.IO) {
                insertNewWatering(
                    database, cmd.plantId.value, cmd.timestampMs, cmd.volume, cmd.weightGrams
                )
            }

            is Cmd.SavePlant -> viewModelScope.launch(Dispatchers.IO) {
                deps.insertPlant(cmd.name, cmd.speciesId, cmd.createdAtMs, cmd.photoPath)
            }

            is Cmd.CopyPhoto -> viewModelScope.launch {
                runCatching { deps.copyPhoto(cmd.uri) }.onSuccess { path ->
                        dispatch(
                            AddPhotoCopied(
                                path
                            )
                        )
                    }.onFailure {
                        _effects.send(ShowMessage(Resource(R.string.error_failed_to_add_photo)))
                    }
            }

            is Cmd.DeletePhoto -> viewModelScope.launch(Dispatchers.IO) { deps.deletePhoto(cmd.path) }
            is Cmd.UpdatePlant -> viewModelScope.launch(Dispatchers.IO) {
                updatePlant(database, cmd.plantId.value, cmd.name, cmd.speciesId)
            }

            Cmd.LoadEditingSpecies -> viewModelScope.launch {
                dispatch(EditingSpecies(loadSpecies()))
            }

            Cmd.LoadCreatingSpecies -> viewModelScope.launch {
                dispatch(CreatingSpecies(loadSpecies()))
            }

            is Cmd.UpdateMeasurement -> persist(
                errorText = Resource(R.string.error_measurement_exists_at_time),
                onSuccess = { dispatch(PlantIntent.EditingMeasurement.Saved) },
                block = {
                    updateMeasurement(database, cmd.measurementId.value, cmd.weightGrams, cmd.timestampMs)
                },
            )
            is Cmd.UpdateWatering -> persist(
                errorText = Resource(R.string.error_failed_to_save_watering),
                onSuccess = { dispatch(PlantIntent.EditingWatering.Saved) },
                block = {
                    updateWatering(
                        db = database,
                        id = cmd.wateringId.value,
                        plantId = cmd.plantId.value,
                        oldTs = cmd.oldTimestampMs,
                        newTs = cmd.newTimestampMs,
                        volumeMl = cmd.volumeMl,
                    )
                },
            )

            is Cmd.DeleteMeasurement -> viewModelScope.launch(Dispatchers.IO) {
                deleteMeasurement(database, cmd.id.value)
            }
            is Cmd.DeleteWatering -> viewModelScope.launch(Dispatchers.IO) {
                database.plateauDatabaseQueries.deleteWatering(cmd.id.value)
            }
            is Cmd.CopyToClipboard -> _effects.trySend(CopyToClipboard(cmd.label, cmd.text))
            is Cmd.StartDeleteTimer -> {
                timers[cmd.plantId] = viewModelScope.launch {
                    delay(cmd.delayMs.milliseconds)
                    dispatchDeletion(PlantIntent.Overview.Deletion.Commit(cmd.plantId))
                }
            }
            is Cmd.CancelDeleteTimer -> timers.remove(cmd.plantId)?.cancel()
            is Cmd.DeletePlant -> {
                timers.remove(cmd.plantId)
                viewModelScope.launch(Dispatchers.IO) { removePlant(database, cmd.plantId.value) }
            }
        }
    }

    private suspend fun loadSpecies(): List<com.zghonnyk.domain.Species> =
        withContext(Dispatchers.IO) {
            fetchAllSpecies(database).map { db ->
                com.zghonnyk.domain.Species(
                    id = com.zghonnyk.domain.SpeciesId(db.id),
                    latinName = db.name,
                )
            }
        }

    private fun persist(
        errorText: UiText,
        onSuccess: () -> Unit,
        block: () -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { block() } }.onSuccess { onSuccess() }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _effects.send(ShowMessage(errorText))
                }
        }
    }

    fun updateWatering(
        db: PlateauDatabase, id: Long, plantId: Long, oldTs: Long, newTs: Long, volumeMl: Double?
    ) {
        val q = db.plateauDatabaseQueries
        q.transaction {
            q.updateWateringTimestamp(newTs, volumeMl, id)
            q.moveMeasurementAt(newTs, plantId, oldTs)
        }
    }



    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PlateauApplication
                PlantsViewModel(
                    database = app.container.database,
                    deps = app.container.plantDeps,
                    initialNow = now(),
                )
            }
        }
    }
}