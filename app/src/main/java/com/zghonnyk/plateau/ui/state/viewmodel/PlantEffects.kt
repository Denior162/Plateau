package com.zghonnyk.plateau.ui.state.viewmodel

import androidx.core.net.toUri
import com.zghonnyk.domain.ActiveDryingPhase
import com.zghonnyk.domain.AnalyzedPhase
import com.zghonnyk.domain.DryingConfig
import com.zghonnyk.domain.DryingForecast
import com.zghonnyk.domain.Measurement
import com.zghonnyk.domain.MeasurementId
import com.zghonnyk.domain.PlantFacts
import com.zghonnyk.domain.PlantId
import com.zghonnyk.domain.Tare
import com.zghonnyk.domain.analyzeDrying
import com.zghonnyk.domain.latestMoisture
import com.zghonnyk.domain.splitIntoPhases
import com.zghonnyk.domain.state.Cmd
import com.zghonnyk.domain.state.PlantIntent
import com.zghonnyk.domain.state.PlantIntent.Creating.AddPhotoCopied
import com.zghonnyk.domain.state.PlantIntent.Loaded.CreatingSpecies
import com.zghonnyk.domain.state.PlantIntent.Loaded.EditingSpecies
import com.zghonnyk.domain.timeline
import com.zghonnyk.plateau.data.db.PlateauDatabase
import com.zghonnyk.plateau.data.repository.PotChoice.New
import com.zghonnyk.plateau.data.repository.deleteMeasurement
import com.zghonnyk.plateau.data.repository.exportRows
import com.zghonnyk.plateau.data.repository.fetchAllSpecies
import com.zghonnyk.plateau.data.repository.insertNewMeasurement
import com.zghonnyk.plateau.data.repository.insertNewPruning
import com.zghonnyk.plateau.data.repository.insertNewRepotting
import com.zghonnyk.plateau.data.repository.insertNewWatering
import com.zghonnyk.plateau.data.repository.insertPhoto
import com.zghonnyk.plateau.data.repository.observeActivePhaseMeasurements
import com.zghonnyk.plateau.data.repository.observePlantOverview
import com.zghonnyk.plateau.data.repository.removePlant
import com.zghonnyk.plateau.data.repository.updateMeasurement
import com.zghonnyk.plateau.data.repository.updatePlant
import com.zghonnyk.plateau.data.repository.updateWatering
import com.zghonnyk.plateau.di.PlantDeps
import com.zghonnyk.plateau.domain.importBackup
import com.zghonnyk.plateau.domain.parseBackup
import com.zghonnyk.plateau.domain.toCsv
import com.zghonnyk.plateau.feature.main.R
import com.zghonnyk.plateau.ui.state.viewmodel.PlantsViewModel.PlantEffect.CopyToClipboard
import com.zghonnyk.plateau.ui.state.viewmodel.PlantsViewModel.PlantEffect.ShowMessage
import com.zghonnyk.plateau.ui.state.viewmodel.PlantsViewModel.UiText.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

interface PlantEffects {
    fun attach(
        scope: CoroutineScope,
        dispatch: (PlantIntent) -> Unit,
        emitEffect: (PlantsViewModel.PlantEffect) -> Unit,
    )

    fun observePlants(): Flow<List<PlantFacts>>

    fun exportCsv(cmd: Cmd.ExportCsv)
    fun importCsv(cmd: Cmd.ImportCsv)
    fun savePruning(cmd: Cmd.SavePruning)
    fun saveRepotting(cmd: Cmd.SaveRepotting)
    fun saveMeasurement(cmd: Cmd.SaveMeasurement)
    fun saveWatering(cmd: Cmd.SaveWatering)
    fun savePlant(cmd: Cmd.SavePlant)
    fun copyPhoto(cmd: Cmd.CopyPhoto)
    fun deletePhoto(cmd: Cmd.DeletePhoto)
    fun updatePlant(cmd: Cmd.UpdatePlant)
    fun loadEditingSpecies(cmd: Cmd.LoadEditingSpecies)
    fun loadCreatingSpecies(cmd: Cmd.LoadCreatingSpecies)
    fun updateMeasurement(cmd: Cmd.UpdateMeasurement)
    fun updateWatering(cmd: Cmd.UpdateWatering)
    fun deleteMeasurement(cmd: Cmd.DeleteMeasurement)
    fun deleteWatering(cmd: Cmd.DeleteWatering)
    fun copyToClipboard(label: String, text: String)
    fun startDeleteTimer(cmd: Cmd.StartDeleteTimer)
    fun cancelDeleteTimer(cmd: Cmd.CancelDeleteTimer)
    fun deletePlant(cmd: Cmd.DeletePlant)

    fun copyPlantPhoto(cmd: Cmd.CopyPlantPhoto)
    fun savePlantPhoto(cmd: Cmd.SavePlantPhoto)
}

class PlantEffectsImpl(
    private val database: PlateauDatabase,
    private val deps: PlantDeps,
) : PlantEffects {
    private lateinit var scope: CoroutineScope
    private lateinit var dispatch: (PlantIntent) -> Unit
    private lateinit var emitEffect: (PlantsViewModel.PlantEffect) -> Unit
    private val timers = mutableMapOf<PlantId, Job>()

    override fun attach(
        scope: CoroutineScope,
        dispatch: (PlantIntent) -> Unit,
        emitEffect: (PlantsViewModel.PlantEffect) -> Unit,
    ) {
        this.scope = scope
        this.dispatch = dispatch
        this.emitEffect = emitEffect
    }

    private val phaseAnalysisCache =
        java.util.concurrent.ConcurrentHashMap<List<Measurement>, DryingForecast>()

    private fun analyzeCached(measurements: List<Measurement>): DryingForecast {
        if (measurements.isEmpty()) return DryingForecast.InsufficientData(null)
        return phaseAnalysisCache.getOrPut(measurements) {
            ActiveDryingPhase(measurements).analyzeDrying(DryingConfig())
        }
    }

    override fun observePlants(): Flow<List<PlantFacts>> =
        combine(
            observePlantOverview(database),
            observeActivePhaseMeasurements(database),
        ) { overview, activeRows ->
            val activeByPlant = activeRows.groupBy { it.plant_id }

            overview.map { row ->
                val plantId = PlantId(row.id)

                val measurements = activeByPlant[row.id].orEmpty().map {
                    Measurement(
                        id = MeasurementId(it.id),
                        plantId = plantId,
                        timestamp = Instant.ofEpochMilli(it.timestamp),
                        weightGrams = it.weight_grams,
                    )
                }

                val phases = splitIntoPhases(measurements, emptyList())
                val analyzedPhases = phases.map { phase ->
                    AnalyzedPhase(phase, analyzeCached(phase.measurements))
                }

                val currentAnalyzed = analyzedPhases.lastOrNull()
                val activeMeasurements = currentAnalyzed?.phase?.measurements.orEmpty()
                val forecast = currentAnalyzed?.forecast ?: DryingForecast.InsufficientData(null)

                val lastWeighedAt = row.last_weighed_at?.let(Instant::ofEpochMilli)
                val pot: Tare = row.tare_grams?.let { Tare.Known(it) } ?: Tare.Unknown

                PlantFacts(
                    id = plantId,
                    name = row.plant_name,
                    latinName = row.species_name ?: "Incertae sedis",
                    dryingForecast = forecast,
                    lastWeighedAt = lastWeighedAt,
                    speciesId = row.species_id,
                    hasWatering = row.has_watering != 0L,
                    activeMeasurements = activeMeasurements,
                    readyAt = readyAt(lastWeighedAt, forecast.recommendedInterval),
                    moisture = analyzedPhases.latestMoisture(pot),
                    timeline = timeline(emptyList(), emptyList()),
                    plantPhoto = row.photo_path,
                )
            }.sortedWith(
                compareBy<PlantFacts> { it.activeMeasurements.isNotEmpty() }
                    .thenBy(nullsLast()) { it.readyAt }
                    .thenBy {
                        (it.dryingForecast as? DryingForecast.Active)?.daysUntilPlateau
                            ?: Int.MAX_VALUE
                    }
            )
        }.flowOn(Dispatchers.Default)

    override fun exportCsv(cmd: Cmd.ExportCsv) {
        scope.launch(Dispatchers.IO) {
            val msg: PlantsViewModel.UiText = try {
                val out = deps.openOut(cmd.uri.value.toUri()) ?: return@launch emitEffect(
                    ShowMessage(
                        Resource(R.string.error_cannot_open_file)
                    )
                )
                out.bufferedWriter(Charsets.UTF_8).use { it.write(toCsv(exportRows(database))) }
                Resource(R.string.export_ready)
            } catch (e: Exception) {
                Resource(R.string.export_error, listOf(e.message.orEmpty()))
            }
            emitEffect(ShowMessage(msg))
        }
    }

    override fun importCsv(cmd: Cmd.ImportCsv) {
        scope.launch(Dispatchers.IO) {
            val msg: PlantsViewModel.UiText = try {
                val input = deps.openIn(cmd.uri.value.toUri()) ?: return@launch emitEffect(
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
            emitEffect(ShowMessage(msg))
        }
    }

    override fun savePruning(cmd: Cmd.SavePruning) {
        scope.launch(Dispatchers.IO) {
            insertNewPruning(database, cmd.plantId.value, cmd.timestampMs, cmd.removedGrams)
        }
    }

    override fun saveRepotting(cmd: Cmd.SaveRepotting) {
        scope.launch(Dispatchers.IO) {
            insertNewRepotting(
                db = database,
                plantId = cmd.plantId.value,
                timestamp = cmd.timestampMs,
                pot = New(cmd.potName, null),
                weightAfterGrams = cmd.weightAfterGrams,
            )
        }
    }

    override fun saveMeasurement(cmd: Cmd.SaveMeasurement) {
        scope.launch(Dispatchers.IO) {
            insertNewMeasurement(database, cmd.plantId.value, cmd.timestampMs, cmd.weightGrams)
        }
    }

    override fun saveWatering(cmd: Cmd.SaveWatering) {
        scope.launch(Dispatchers.IO) {
            insertNewWatering(
                database, cmd.plantId.value, cmd.timestampMs, cmd.volume, cmd.weightGrams
            )
        }
    }

    override fun savePlant(cmd: Cmd.SavePlant) {
        scope.launch(Dispatchers.IO) {
            deps.insertPlant(cmd.name, cmd.speciesId, cmd.createdAtMs, cmd.photoPath)
        }
    }

    override fun copyPhoto(cmd: Cmd.CopyPhoto) {
        scope.launch {
            runCatching { deps.copyPhoto(cmd.uri.value.toUri()) }.onSuccess { path ->
                dispatch(AddPhotoCopied(path))
            }.onFailure {
                emitEffect(ShowMessage(Resource(R.string.error_failed_to_add_photo)))
            }
        }
    }

    override fun copyPlantPhoto(cmd: Cmd.CopyPlantPhoto) {
        scope.launch {
            runCatching { deps.copyPhoto(cmd.uri.value.toUri()) }
                .onSuccess { path ->
                    dispatch(PlantIntent.Overview.PhotoCopied(cmd.plantId, path))
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    emitEffect(ShowMessage(Resource(R.string.error_failed_to_add_photo)))
                }
        }
    }

    override fun deletePhoto(cmd: Cmd.DeletePhoto) {
        scope.launch(Dispatchers.IO) { deps.deletePhoto(cmd.path) }
    }

    override fun updatePlant(cmd: Cmd.UpdatePlant) {
        scope.launch(Dispatchers.IO) {
            updatePlant(database, cmd.plantId.value, cmd.name, cmd.speciesId)
        }
    }

    override fun loadEditingSpecies(cmd: Cmd.LoadEditingSpecies) {
        scope.launch {
            dispatch(EditingSpecies(loadSpecies()))
        }
    }

    override fun loadCreatingSpecies(cmd: Cmd.LoadCreatingSpecies) {
        scope.launch {
            dispatch(CreatingSpecies(loadSpecies()))
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

    override fun updateMeasurement(cmd: Cmd.UpdateMeasurement) {
        persist(
            errorText = Resource(R.string.error_measurement_exists_at_time),
            onSuccess = { dispatch(PlantIntent.EditingMeasurement.Saved) },
            block = {
                updateMeasurement(
                    database, cmd.measurementId.value, cmd.weightGrams, cmd.timestampMs
                )
            },
        )
    }

    override fun updateWatering(cmd: Cmd.UpdateWatering) {
        persist(
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
    }

    private fun persist(
        errorText: PlantsViewModel.UiText,
        onSuccess: () -> Unit,
        block: () -> Unit,
    ) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { block() } }.onSuccess { onSuccess() }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    emitEffect(ShowMessage(errorText))
                }
        }
    }

    override fun deleteMeasurement(cmd: Cmd.DeleteMeasurement) {
        scope.launch(Dispatchers.IO) {
            deleteMeasurement(database, cmd.id.value)
        }
    }

    override fun deleteWatering(cmd: Cmd.DeleteWatering) {
        scope.launch(Dispatchers.IO) {
            database.plateauDatabaseQueries.deleteWatering(cmd.id.value)
        }
    }

    override fun copyToClipboard(label: String, text: String) {
        emitEffect(CopyToClipboard(label, text))
    }

    override fun startDeleteTimer(cmd: Cmd.StartDeleteTimer) {
        timers[cmd.plantId] = scope.launch {
            delay(cmd.delayMs.milliseconds)
            dispatch(PlantIntent.Overview.Deletion.Commit(cmd.plantId))
        }
    }

    override fun cancelDeleteTimer(cmd: Cmd.CancelDeleteTimer) {
        timers.remove(cmd.plantId)?.cancel()
    }

    override fun deletePlant(cmd: Cmd.DeletePlant) {
        timers.remove(cmd.plantId)
        scope.launch(Dispatchers.IO) { removePlant(database, cmd.plantId.value) }
    }

    override fun savePlantPhoto(cmd: Cmd.SavePlantPhoto) {
        scope.launch(Dispatchers.IO) {
            insertPhoto(
                db = database,
                plantId = cmd.plantId.value,
                timestamp = System.currentTimeMillis(),
                filePath = cmd.relativePath,
            )
        }
    }

    private fun readyAt(lastWeighedAt: Instant?, interval: Duration?): Instant? =
        lastWeighedAt?.let { last -> interval?.let { last.plus(it.toJavaDuration()) } }
}