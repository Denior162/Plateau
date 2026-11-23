package com.zghonnyk.plateau.ui.state.viewmodel

import android.net.Uri
import android.util.Log
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
import com.zghonnyk.domain.MonthlyStatItem
import com.zghonnyk.domain.PlantId
import com.zghonnyk.domain.PlantPeakWeight
import com.zghonnyk.domain.PlantTemporalEvent
import com.zghonnyk.domain.PotId
import com.zghonnyk.domain.PruningEvent
import com.zghonnyk.domain.PruningEventId
import com.zghonnyk.domain.RepottingEvent
import com.zghonnyk.domain.RepottingEventId
import com.zghonnyk.domain.SpeciesMeasurementStats
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
import com.zghonnyk.plateau.data.repository.PotChoice
import com.zghonnyk.plateau.data.repository.deleteMeasurement
import com.zghonnyk.plateau.data.repository.exportRows
import com.zghonnyk.plateau.data.repository.fetchAllSpecies
import com.zghonnyk.plateau.data.repository.fetchMeasurementsForPlant
import com.zghonnyk.plateau.data.repository.fetchWateringsForPlant
import com.zghonnyk.plateau.data.repository.insertNewMeasurement
import com.zghonnyk.plateau.data.repository.insertNewPruning
import com.zghonnyk.plateau.data.repository.insertNewRepotting
import com.zghonnyk.plateau.data.repository.insertNewWatering
import com.zghonnyk.plateau.data.repository.observeAllMeasurements
import com.zghonnyk.plateau.data.repository.observeAllPhotos
import com.zghonnyk.plateau.data.repository.observeAllPrunings
import com.zghonnyk.plateau.data.repository.observeAllRepottings
import com.zghonnyk.plateau.data.repository.observeAllWaterings
import com.zghonnyk.plateau.data.repository.observeGlobalMedianEvaporation
import com.zghonnyk.plateau.data.repository.observeMonthlyStats
import com.zghonnyk.plateau.data.repository.observePlantsWithMinPeakWeight
import com.zghonnyk.plateau.data.repository.observePlantsWithSpecies
import com.zghonnyk.plateau.data.repository.observeSpeciesMeasurementStats
import com.zghonnyk.plateau.data.repository.observeTotalEvaporatedBySpecies
import com.zghonnyk.plateau.data.repository.removePlant
import com.zghonnyk.plateau.data.repository.updateMeasurement
import com.zghonnyk.plateau.data.repository.updatePlant
import com.zghonnyk.plateau.data.toDomain
import com.zghonnyk.plateau.di.PlantDeps
import com.zghonnyk.plateau.domain.importBackup
import com.zghonnyk.plateau.domain.parseBackup
import com.zghonnyk.plateau.domain.toCsv
import com.zghonnyk.plateau.feature.main.R
import com.zghonnyk.plateau.feature.main.ui.components.ChartPoint
import com.zghonnyk.plateau.feature.main.ui.extentions.toClipboardText
import com.zghonnyk.plateau.feature.main.ui.extentions.toMonthGroupUiModels
import com.zghonnyk.plateau.feature.main.ui.state.PendingDelete
import com.zghonnyk.plateau.feature.main.ui.state.PlantFacts
import com.zghonnyk.plateau.feature.main.ui.state.PlantIntent
import com.zghonnyk.plateau.feature.main.ui.state.PlantState
import com.zghonnyk.plateau.feature.main.ui.state.PlantState.Details.Plant
import com.zghonnyk.plateau.feature.main.ui.state.PlantUi
import com.zghonnyk.plateau.feature.main.ui.state.SpeciesSelection
import com.zghonnyk.plateau.feature.main.ui.state.applyPlants
import com.zghonnyk.plateau.feature.main.ui.state.reducePlantState
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
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
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
        ) : UiText {
            constructor(resId: Int, vararg args: Any) : this(resId, args.toList())
        }

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

    private val _pendingDelete = MutableStateFlow<PendingDelete?>(null)
    val pendingDelete: StateFlow<PendingDelete?> = _pendingDelete.asStateFlow()

    private var deleteJob: Job? = null
    private fun exportCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg: UiText = try {
                val out = deps.openOut(uri) ?: return@launch _effects.send(
                    PlantEffect.ShowMessage(
                        UiText.Resource(R.string.error_cannot_open_file)
                    )
                )
                out.bufferedWriter(Charsets.UTF_8).use { it.write(toCsv(exportRows(database))) }
                UiText.Resource(R.string.export_ready)
            } catch (e: Exception) {
                UiText.Resource(R.string.export_error, listOf(e.message.orEmpty()))
            }
            _effects.send(PlantEffect.ShowMessage(msg))
        }
    }

    fun importCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg: UiText = try {
                val input = deps.openIn(uri) ?: return@launch _effects.send(
                    PlantEffect.ShowMessage(
                        UiText.Resource(R.string.error_cannot_open_file)
                    )
                )
                val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val summary = importBackup(database, parseBackup(text))
                UiText.Resource(
                    R.string.import_success, listOf(
                        summary.plantsAdded, summary.measurementsAdded, summary.measurementsSkipped
                    )
                )
            } catch (e: Exception) {
                UiText.Resource(R.string.import_error, listOf(e.message.orEmpty()))
            }
            _effects.send(PlantEffect.ShowMessage(msg))
        }
    }

    fun getGlobalMedian(): Flow<Double> =
        observeGlobalMedianEvaporation(database).flowOn(Dispatchers.IO)

    fun getSpeciesMeasurementStats(): Flow<List<SpeciesMeasurementStats>> =
        observeSpeciesMeasurementStats(database).flowOn(Dispatchers.IO)

    fun getPlantsWithMinPeakWeight(minWeight: Double): Flow<List<PlantPeakWeight>> =
        observePlantsWithMinPeakWeight(database, minWeight).flowOn(Dispatchers.IO)

    fun getTotalEvaporatedBySpecies(speciesId: Long): Flow<Double> =
        observeTotalEvaporatedBySpecies(database, speciesId).flowOn(Dispatchers.IO)

    fun getSpeciesList(): List<com.zghonnyk.domain.Species> =
        fetchAllSpecies(database).map { it.toDomain() }

    fun getMonthlyStats(): Flow<List<MonthlyStatItem>> =
        observeMonthlyStats(database).flowOn(Dispatchers.IO)

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
                activeMeasurements.withRates().toMonthGroupUiModels()
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
        plantPhoto
    )

    private fun hoursBetween(from: Instant, to: Instant) =
        java.time.Duration.between(from, to).toHours()

    private fun readyAt(lastWeighedAt: Instant?, interval: Duration?): Instant? =
        lastWeighedAt?.let { last -> interval?.let { last.plus(it.toJavaDuration()) } }

    fun overdue(lastWeighedAt: Instant, now: Instant, intervalDays: Int): Double {
        val elapsedMillis = now.toEpochMilli() - lastWeighedAt.toEpochMilli()
        val intervalMillis = intervalDays * 86_400_000L
        return if (intervalMillis <= 0L) 0.0 else elapsedMillis.toDouble() / intervalMillis.toDouble()
    }


    fun handleIntent(intent: PlantIntent) {
        when (intent) {
            is PlantIntent.Creating.Save -> savePlant()
            is PlantIntent.AddingMeasurement.Save -> saveMeasurement()
            is PlantIntent.LoggingWatering.Save -> saveWatering()
            is PlantIntent.EditingPlant.Save -> savePlantEdit()
            is PlantIntent.EditingMeasurement.Save -> saveMeasurementEdit()
            is PlantIntent.EditingName.Save -> saveEditingName()
            is PlantIntent.Overview.StartEditingPlantName -> startEditingPlantName(intent)
            is PlantIntent.Details.DeleteMeasurement -> removeMeasurement(intent.id)
            is PlantIntent.Details.CopyMonth -> copyMonth(intent.month)
            is PlantIntent.ExportCsv -> exportCsv(intent.uri)
            is PlantIntent.ImportCsv -> importCsv(intent.uri)
            is PlantIntent.Overview.Deletion.Request -> startDelete(intent.plantId)
            is PlantIntent.Overview.Deletion.Undo -> undoDelete()
            is PlantIntent.Overview.SelectPlant -> if (_pendingDelete.value?.plantId != intent.plantId) reduce(
                intent
            )

            is PlantIntent.Pruning.Save -> savePruning()
            is PlantIntent.Repotting.Save -> saveRepotting()
            is PlantIntent.Overview.OpenAddPlant -> openAddPlant(intent)
            is PlantIntent.Refresh -> Unit
            is PlantIntent.EditingWatering.Save -> saveWateringEdit()
            is PlantIntent.Details.DeleteWatering -> removeWatering(intent.id)
            is PlantIntent.Creating.AddPhoto -> addPhoto(intent.uri)
            else -> reduce(intent)
        }
    }

    private fun persist(
        errorText: UiText,
        onSuccess: () -> Unit,
        block: () -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { block() } }.onSuccess { onSuccess() }
                .onFailure { _effects.send(PlantEffect.ShowMessage(errorText)) }
        }
    }

    private fun addPhoto(uri: Uri) {
        viewModelScope.launch {
            runCatching { deps.copyPhoto(uri) }.onSuccess { path ->
                    Log.d("Photo", "copied: $path")
                    (_uiState.value as? PlantState.Creating)?.form?.photoPath?.let {
                        deps.deletePhoto(
                            it
                        )
                    }
                    reduce(PlantIntent.Creating.AddPhotoCopied(path))
                }.onFailure {
                    Log.e("Photo", "failed to copy photo", it)
                    _effects.send(PlantEffect.ShowMessage(UiText.Resource(R.string.error_failed_to_add_photo)))
                }
        }
    }

    private fun savePlant() {
        val s = _uiState.value as? PlantState.Creating ?: return
        Log.d("Photo", "save, photoPath=${s.form.photoPath}") // ← Лог 2
        val name = s.form.customName.text.toString().trim()
        if (name.isEmpty()) return
        val speciesId = when (val selection = s.form.speciesSelection) {
            is SpeciesSelection.Selected -> selection.species.id.value
            SpeciesSelection.Unknown -> null
            SpeciesSelection.Unselected -> return
        }

        viewModelScope.launch(Dispatchers.IO) {
            deps.insertPlant(name, speciesId, now().toEpochMilli(), s.form.photoPath)
        }
        reduce(PlantIntent.Creating.Save)
    }

    private fun saveWateringEdit() {
        val s = _uiState.value as? PlantState.PlantModal.HistoryModal.EditWatering ?: return
        val plantId = s.parent.plant.id.value
        val volumeMl = s.volumeInput.text.toString().toPositiveDoubleOrNull()

        val newTs = s.timestamp.toEpochMilli()
        val oldTs = s.originalTimestamp.toEpochMilli()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    updateWatering(
                        db = database,
                        id = s.wateringId.value,
                        plantId = plantId,
                        oldTs = oldTs,
                        newTs = newTs,
                        volumeMl = volumeMl
                    )
                }
            }.onSuccess {
                reduce(PlantIntent.EditingWatering.Save)
            }.onFailure {
                _effects.send(PlantEffect.ShowMessage(UiText.Resource(R.string.error_failed_to_save_watering)))
            }
        }
    }

    private fun saveRepotting() {
        val s = _uiState.value as? PlantState.PlantModal.Repotting ?: return

        val weightText = s.weightInput.text.toString()

        val weightAfter = weightText.takeIf { it.isNotBlank() }?.let { text ->
            val parsed = text.replace(',', '.').toDoubleOrNull()
            parsed?.takeIf { it > 0.0 } ?: return
        }

        val potMass = null

        val plantId = s.parent.focus.id.value
        val ts = (s.customTimestamp ?: now()).toEpochMilli()

        val potName = "${s.parent.focus.name} pot"

        viewModelScope.launch(Dispatchers.IO) {
            insertNewRepotting(
                db = database,
                plantId = plantId,
                timestamp = ts,
                pot = PotChoice.New(potName, potMass),
                weightAfterGrams = weightAfter
            )
        }

        closeModal()
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

    private fun copyMonth(month: YearMonth) {
        val s = _uiState.value as? Plant ?: return
        val group = s.zipper.focus.records.find { it.month == month } ?: return
        _effects.trySend(
            PlantEffect.CopyToClipboard(
                label = group.monthNameAndYearTitle, text = group.toClipboardText()
            )
        )
    }

    private fun removeMeasurement(id: MeasurementId) {
        viewModelScope.launch(Dispatchers.IO) { deleteMeasurement(database, id.value) }
    }

    private fun removeWatering(id: WateringEventId) {
        viewModelScope.launch(Dispatchers.IO) {
            database.plateauDatabaseQueries.deleteWatering(id.value)
        }
    }

    /** «Назад» только если сейчас открыта модалка; иначе Back увёл бы с экрана. */
    private fun closeModal() {
        if (_uiState.value is PlantState.Modal) reduce(PlantIntent.Back)
    }

    private fun startDelete(plantId: PlantId) {
        if (_pendingDelete.value?.plantId == plantId) return
        _pendingDelete.value?.let { commitDelete(it.plantId) }
        deleteJob?.cancel()

        _pendingDelete.value = PendingDelete(plantId, 1f)
        deleteJob = viewModelScope.launch {
            val start = TimeSource.Monotonic.markNow()
            while (true) {
                val elapsed = start.elapsedNow().inWholeMilliseconds
                if (elapsed >= DELETE_DELAY_MS) break
                _pendingDelete.value =
                    PendingDelete(plantId, 1f - elapsed / DELETE_DELAY_MS.toFloat())
                delay(TICK_MS.milliseconds)
            }
            _pendingDelete.value = null
            commitDelete(plantId)
        }
    }

    private fun undoDelete() {
        if (_pendingDelete.value == null) return
        deleteJob?.cancel()
        deleteJob = null
        _pendingDelete.value = null
    }

    private fun commitDelete(plantId: PlantId) {
        viewModelScope.launch(Dispatchers.IO) { removePlant(database, plantId.value) }
    }

    private fun startEditingPlantName(intent: PlantIntent.Overview.StartEditingPlantName) {
        reduce(intent)
        if (_uiState.value !is PlantState.EditingName) return

        viewModelScope.launch(Dispatchers.IO) {
            val allSpecies = fetchAllSpecies(database).map { dbSpecies ->
                com.zghonnyk.domain.Species(
                    id = com.zghonnyk.domain.SpeciesId(dbSpecies.id), latinName = dbSpecies.name
                )
            }
            reduce(PlantIntent.Loaded.EditingSpecies(allSpecies))
        }
    }

    private fun openAddPlant(intent: PlantIntent.Overview.OpenAddPlant) {
        reduce(intent)
        if (_uiState.value !is PlantState.Creating) return

        viewModelScope.launch(Dispatchers.IO) {
            val allSpecies = fetchAllSpecies(database).map { it.toDomain() }
            reduce(PlantIntent.Loaded.CreatingSpecies(allSpecies))
        }
    }

    private fun saveEditingName() {
        val s = _uiState.value as? PlantState.EditingName ?: return
        val newName = s.nameInput.text.toString().trim()
        if (newName.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            updatePlant(database, s.plantId.value, newName, s.selectedSpeciesId)
        }
        reduce(PlantIntent.EditingName.Save)
    }

    private fun reduce(intent: PlantIntent) = _uiState.update { s ->
        if (s is PlantState.Active) reducePlantState(s, intent) else s
    }

    private fun savePruning() {
        val s = _uiState.value as? PlantState.PlantModal.Pruning ?: return
        val raw = s.weightInput.text.toString()
        val removed = if (raw.isBlank()) null else raw.toPositiveDoubleOrNull() ?: return
        val plantId = s.parent.focus.id
        val timestamp = (s.customTimestamp ?: now()).toEpochMilli()

        viewModelScope.launch(Dispatchers.IO) {
            insertNewPruning(database, plantId.value, timestamp, removed)
        }
        _uiState.update { s.parent }
    }

    private fun saveMeasurement() {
        val s = _uiState.value as? PlantState.PlantModal.AddMeasurement ?: return
        val weight = s.weightInput.text.toString().toPositiveDoubleOrNull() ?: return
        val plantId = s.parent.focus.id
        val timestamp = (s.customTimestamp ?: now()).toEpochMilli()

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                insertNewMeasurement(database, plantId.value, timestamp, weight)
            }
            reduce(PlantIntent.AddingMeasurement.Save)
        }
    }

    private fun savePlantEdit() {
        val s = _uiState.value as? PlantState.PlantModal.EditPlant ?: return
        val name = s.nameInput.text.toString().trim()
        if (name.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            updatePlant(database, s.plantId.value, name, s.selectedSpeciesId)
        }
        reduce(PlantIntent.EditingPlant.Save)
    }

    private fun saveMeasurementEdit() {
        val (measurementId, weightInput, timestamp) = when (val s = _uiState.value) {
            is PlantState.PlantModal.EditMeasurement -> Triple(
                s.measurementId, s.weightInput, s.timestamp
            )

            is PlantState.PlantModal.HistoryModal.EditMeasurement -> Triple(
                s.measurementId, s.weightInput, s.timestamp
            )

            else -> return
        }
        val weight = weightInput.text.toString().toPositiveDoubleOrNull() ?: return

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    updateMeasurement(
                        database, measurementId.value, weight, timestamp.toEpochMilli()
                    )
                }
            }.onSuccess { reduce(PlantIntent.EditingMeasurement.Save) }
                .onFailure { _effects.send(PlantEffect.ShowMessage(UiText.Resource(R.string.error_measurement_exists_at_time))) }
        }
    }

    private fun String.toPositiveDoubleOrNull(): Double? {
        val cleaned = filter { it.isDigit() || it == '.' || it == ',' }
        return cleaned.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    }

    private fun saveWatering() {
        val s = _uiState.value as? PlantState.PlantModal.LogWatering ?: return
        val volume = s.volumeInput.text.toString().toPositiveDoubleOrNull()
        val weight = s.weightInput.text.toString().toPositiveDoubleOrNull()
        val plantId = s.parent.focus.id
        val timestamp = (s.customTimestamp ?: now()).toEpochMilli()

        viewModelScope.launch(Dispatchers.IO) {
            insertNewWatering(database, plantId.value, timestamp, volume, weight)
        }
        reduce(PlantIntent.LoggingWatering.Save)
    }

    private fun openMeasurementHistory() {
        val s = _uiState.value as? Plant ?: return
        reloadMeasurementHistory(s.focus.id.value)
    }

    private fun openWateringHistory() {
        val s = _uiState.value as? Plant ?: return
        reloadWateringHistory(s.focus.id.value)
    }

    private fun reloadMeasurementHistory(plantId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val measurements = fetchMeasurementsForPlant(database, plantId)
            val dateFormatter =
                DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.getDefault())

            val elements = measurements.map { m ->
                val formattedDate = Instant.ofEpochMilli(m.timestamp).atZone(ZoneId.systemDefault())
                    .format(dateFormatter)
                val weightStr =
                    if (m.weight_grams % 1.0 == 0.0) "${m.weight_grams.toInt()} г" else "${m.weight_grams} г"

                com.zghonnyk.plateau.feature.main.ui.state.MeasurementUiModel(
                    id = MeasurementId(m.id),
                    formattedDate = formattedDate,
                    formattedWeight = weightStr,
                    timestamp = Instant.ofEpochMilli(m.timestamp)
                )
            }
            reduce(PlantIntent.Loaded.MeasurementHistory(PlantId(plantId), elements))
        }
    }

    private fun reloadWateringHistory(plantId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val waterings = fetchWateringsForPlant(database, plantId)
            val dateFormatter =
                DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.getDefault())

            val elements = waterings.map { w ->
                val formattedDate = Instant.ofEpochMilli(w.timestamp).atZone(ZoneId.systemDefault())
                    .format(dateFormatter)
                val volumeStr = when (val v = w.water_volume_ml) {
                    null -> "об'єм не вказано"
                    else -> if (v.rem(1.0) == 0.0) "${v.toInt()} мл" else "$v мл"
                }

                com.zghonnyk.plateau.feature.main.ui.state.WateringUiModel(
                    id = WateringEventId(w.id),
                    formattedDate = formattedDate,
                    formattedVolume = volumeStr,
                    timestamp = Instant.ofEpochMilli(w.timestamp)
                )
            }
            reduce(PlantIntent.Loaded.WateringHistory(PlantId(plantId), elements))
        }
    }

    companion object {
        private const val DELETE_DELAY_MS = 10_000L
        private const val TICK_MS = 50L

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