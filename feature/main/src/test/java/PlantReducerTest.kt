package com.zghonnyk.plateau.feature.main.ui.state

import androidx.compose.foundation.text.input.TextFieldState
import com.zghonnyk.domain.MeasurementId
import com.zghonnyk.domain.PlantId
import com.zghonnyk.domain.Species
import com.zghonnyk.domain.SpeciesId
import com.zghonnyk.domain.state.Cmd
import com.zghonnyk.domain.state.DELETE_DELAY_MS
import com.zghonnyk.domain.state.PendingDelete
import com.zghonnyk.domain.state.PlantIntent
import com.zghonnyk.domain.state.PlantState
import com.zghonnyk.domain.state.applyPlants
import com.zghonnyk.domain.state.canGoBack
import com.zghonnyk.domain.state.toPositiveDoubleOrNull
import com.zghonnyk.domain.state.update
import com.zghonnyk.domain.state.updateDeletions
import com.zghonnyk.vector_zipper.VectorZipper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PlantReducerTest {

    private val now = Instant.parse("2025-01-01T12:00:00Z")
    private val plantId = PlantId(1L)
    private val samplePlantUi = PlantUi(
        id = plantId,
        name = "Monstera",
        latinName = "Monstera deliciosa",
        records = emptyList(),
        dryingForecast = com.zghonnyk.domain.DryingForecast.InsufficientData(null),
        lastWeighedAt = null,
        speciesId = null,
        hasWatering = false,
        activeMeasurementPoints = emptyList(),
        activeMeasurements = emptyList(),
        hoursLeft = null,
        isReadyForAction = false,
        plateauInDays = null,
        moisture = null,
        prediction = null,
        timeline = emptyList(),
        plantPhoto = null
    )

    // 1. Валидация числа (toPositiveDoubleOrNull)
    @Test
    fun `toPositiveDoubleOrNull parses valid positive numbers with dot and comma`() {
        assertEquals(123.45, "123.45".toPositiveDoubleOrNull()!!, 0.001)
        assertEquals(123.45, "123,45".toPositiveDoubleOrNull()!!, 0.001)
        assertEquals(10.0, " 10.0 ".toPositiveDoubleOrNull()!!, 0.001)
    }

    @Test
    fun `toPositiveDoubleOrNull returns null for zero, negative or non-numeric strings`() {
        assertNull("0".toPositiveDoubleOrNull())
        assertNull("-5.0".toPositiveDoubleOrNull())
        assertNull("abc".toPositiveDoubleOrNull())
        assertNull("".toPositiveDoubleOrNull())
        assertNull("NaN".toPositiveDoubleOrNull())
    }

    // 2. Навигация Back и canGoBack
    @Test
    fun `Back intent returns parent state when present`() {
        val overview = PlantState.Overview(plants = listOf(samplePlantUi))
        val creating = PlantState.Creating(parent = overview, form = AddPlantForm.empty(), speciesList = emptyList())

        val (nextState, cmd) = update(creating, PlantIntent.Back, now)

        assertEquals(overview, nextState)
        assertEquals(Cmd.None, cmd)
    }

    @Test
    fun `Back intent on Overview returns same state`() {
        val overview = PlantState.Overview(plants = listOf(samplePlantUi))

        val (nextState, cmd) = update(overview, PlantIntent.Back, now)

        assertEquals(overview, nextState)
        assertEquals(Cmd.None, cmd)
    }

    @Test
    fun `canGoBack is false for Overview and true for other states`() {
        val overview = PlantState.Overview(plants = listOf(samplePlantUi))
        val creating = PlantState.Creating(parent = overview, form = AddPlantForm.empty(), speciesList = emptyList())

        assertFalse(overview.canGoBack)
        assertTrue(creating.canGoBack)
    }

    // 3. Переходы в Overview и создание растения
    @Test
    fun `OpenAddPlant opens Creating state and triggers LoadCreatingSpecies`() {
        val overview = PlantState.Overview(plants = listOf(samplePlantUi))

        val (nextState, cmd) = update(overview, PlantIntent.Overview.OpenAddPlant, now)

        assertTrue(nextState is PlantState.Creating)
        assertEquals(overview, (nextState as PlantState.Creating).parent)
        assertEquals(Cmd.LoadCreatingSpecies, cmd)
    }

    @Test
    fun `Creating Save with empty name or unselected species stays in Creating state`() {
        val overview = PlantState.Overview(plants = emptyList())
        val creating = PlantState.Creating(parent = overview, form = AddPlantForm.empty(), speciesList = emptyList())

        // Пустое имя
        val (state1, cmd1) = update(creating, PlantIntent.Creating.Save, now)
        assertEquals(creating, state1)
        assertEquals(Cmd.None, cmd1)

        // Выбран невыбранный вид
        creating.form.customName.edit { append("Ficus") }
        val (state2, cmd2) = update(creating, PlantIntent.Creating.Save, now)
        assertEquals(creating, state2)
        assertEquals(Cmd.None, cmd2)
    }

    @Test
    fun `Creating Save with name and selected species returns parent state and SavePlant command`() {
        val overview = PlantState.Overview(plants = emptyList())
        val species = Species(SpeciesId(10L), "Ficus carica")
        val form = AddPlantForm.empty().copy(speciesSelection = SpeciesSelection.Selected(species))
        form.customName.edit { append("My Ficus") }
        val creating = PlantState.Creating(parent = overview, form = form, speciesList = listOf(species))

        val (nextState, cmd) = update(creating, PlantIntent.Creating.Save, now)

        assertEquals(overview, nextState)
        assertTrue(cmd is Cmd.SavePlant)
        val saveCmd = cmd as Cmd.SavePlant
        assertEquals("My Ficus", saveCmd.name)
        assertEquals(10L, saveCmd.speciesId)
        assertEquals(now.toEpochMilli(), saveCmd.createdAtMs)
    }

    @Test
    fun `Creating AddPhotoCopied updates photoPath and deletes previous photo if existed`() {
        val overview = PlantState.Overview(plants = emptyList())
        val form = AddPlantForm.empty().copy(photoPath = "old/path.jpg")
        val creating = PlantState.Creating(parent = overview, form = form, speciesList = emptyList())

        val (nextState, cmd) = update(
            creating,
            PlantIntent.Creating.AddPhotoCopied("new/path.jpg"),
            now
        )

        assertEquals("new/path.jpg", (nextState as PlantState.Creating).form.photoPath)
        assertTrue(cmd is Cmd.DeletePhoto)
        assertEquals("old/path.jpg", (cmd as Cmd.DeletePhoto).path)
    }

    // 4. Переходы в Details
    @Test
    fun `SelectPlant opens Details Plant with focus on selected plant`() {
        val plant2 = samplePlantUi.copy(id = PlantId(2L), name = "Ficus")
        val overview = PlantState.Overview(plants = listOf(samplePlantUi, plant2))

        val (nextState, cmd) = update(overview, PlantIntent.Overview.SelectPlant(PlantId(2L)), now)

        assertTrue(nextState is PlantState.Details.Plant)
        val detailsPlant = nextState as PlantState.Details.Plant
        assertEquals(PlantId(2L), detailsPlant.focus.id)
        assertEquals(Cmd.None, cmd)
    }

    @Test
    fun `Details GoRight and GoLeft move focus in VectorZipper`() {
        val plant2 = samplePlantUi.copy(id = PlantId(2L), name = "Ficus")
        val zipper = VectorZipper(0, listOf(samplePlantUi, plant2))
        val detailsState = PlantState.Details.Plant(zipper)

        val (stateAfterRight, _) = update(detailsState, PlantIntent.Details.GoRight, now)
        assertEquals(PlantId(2L), (stateAfterRight as PlantState.Details.Plant).focus.id)

        val (stateAfterLeft, _) = update(
            stateAfterRight as PlantState.Active,
            PlantIntent.Details.GoLeft,
            now
        )
        assertEquals(PlantId(1L), (stateAfterLeft as PlantState.Details.Plant).focus.id)
    }

    @Test
    fun `Details Plant opens AddMeasurement modal`() {
        val zipper = VectorZipper(0, listOf(samplePlantUi))
        val detailsState = PlantState.Details.Plant(zipper)

        val (nextState, cmd) = update(detailsState, PlantIntent.Details.OpenAddMeasurement, now)

        assertTrue(nextState is PlantState.PlantModal.AddMeasurement)
        assertEquals(detailsState, (nextState as PlantState.PlantModal.AddMeasurement).parent)
        assertEquals(Cmd.None, cmd)
    }

    @Test
    fun `Details Plant opens LogWatering modal`() {
        val zipper = VectorZipper(0, listOf(samplePlantUi))
        val detailsState = PlantState.Details.Plant(zipper)

        val (nextState, cmd) = update(detailsState, PlantIntent.Details.OpenWateringDialog, now)

        assertTrue(nextState is PlantState.PlantModal.LogWatering)
        assertEquals(detailsState, (nextState as PlantState.PlantModal.LogWatering).parent)
        assertEquals(Cmd.None, cmd)
    }

    @Test
    fun `Details Plant opens Pruning modal`() {
        val zipper = VectorZipper(0, listOf(samplePlantUi))
        val detailsState = PlantState.Details.Plant(zipper)

        val (nextState, cmd) = update(detailsState, PlantIntent.Details.OpenPruning, now)

        assertTrue(nextState is PlantState.PlantModal.Pruning)
        assertEquals(detailsState, (nextState as PlantState.PlantModal.Pruning).parent)
        assertEquals(Cmd.None, cmd)
    }

    @Test
    fun `Details Plant opens Repotting modal`() {
        val zipper = VectorZipper(0, listOf(samplePlantUi))
        val detailsState = PlantState.Details.Plant(zipper)

        val (nextState, cmd) = update(detailsState, PlantIntent.Details.OpenRepottingDialog, now)

        assertTrue(nextState is PlantState.PlantModal.Repotting)
        assertEquals(detailsState, (nextState as PlantState.PlantModal.Repotting).parent)
        assertEquals(Cmd.None, cmd)
    }

    @Test
    fun `Details Plant StartEditingPlant opens EditPlant modal and requests species`() {
        val zipper = VectorZipper(0, listOf(samplePlantUi))
        val detailsState = PlantState.Details.Plant(zipper)

        val (nextState, cmd) = update(detailsState, PlantIntent.Details.StartEditingPlant, now)

        assertTrue(nextState is PlantState.PlantModal.EditPlant)
        val editPlant = nextState as PlantState.PlantModal.EditPlant
        assertEquals(plantId, editPlant.plantId)
        assertEquals("Monstera", editPlant.nameInput.text.toString())
        assertEquals(Cmd.LoadEditingSpecies, cmd)
    }

    // 5. Сохранение в модалках
    @Test
    fun `AddingMeasurement Save with valid weight returns parent and SaveMeasurement cmd`() {
        val zipper = VectorZipper(0, listOf(samplePlantUi))
        val detailsState = PlantState.Details.Plant(zipper)
        val modal = PlantState.PlantModal.AddMeasurement(parent = detailsState)
        modal.weightInput.edit { append("450.5") }

        val (nextState, cmd) = update(modal, PlantIntent.AddingMeasurement.Save, now)

        assertEquals(detailsState, nextState)
        assertTrue(cmd is Cmd.SaveMeasurement)
        val saveCmd = cmd as Cmd.SaveMeasurement
        assertEquals(plantId, saveCmd.plantId)
        assertEquals(450.5, saveCmd.weightGrams, 0.001)
        assertEquals(now.toEpochMilli(), saveCmd.timestampMs)
    }

    @Test
    fun `AddingMeasurement Save with invalid weight stays in modal`() {
        val zipper = VectorZipper(0, listOf(samplePlantUi))
        val detailsState = PlantState.Details.Plant(zipper)
        val modal = PlantState.PlantModal.AddMeasurement(parent = detailsState)
        modal.weightInput.edit { append("abc") }

        val (nextState, cmd) = update(modal, PlantIntent.AddingMeasurement.Save, now)

        assertEquals(modal, nextState)
        assertEquals(Cmd.None, cmd)
    }

    @Test
    fun `LoggingWatering Save with valid volume and weight returns SaveWatering cmd`() {
        val zipper = VectorZipper(0, listOf(samplePlantUi))
        val detailsState = PlantState.Details.Plant(zipper)
        val modal = PlantState.PlantModal.LogWatering(parent = detailsState)
        modal.volumeInput.edit { append("200") }
        modal.weightInput.edit { append("600") }

        val (nextState, cmd) = update(modal, PlantIntent.LoggingWatering.Save, now)

        assertEquals(detailsState, nextState)
        assertTrue(cmd is Cmd.SaveWatering)
        val saveCmd = cmd as Cmd.SaveWatering
        assertEquals(plantId, saveCmd.plantId)
        assertEquals(200.0, saveCmd.volume!!, 0.001)
        assertEquals(600.0, saveCmd.weightGrams!!, 0.001)
    }

    @Test
    fun `Pruning Save with valid weight returns SavePruning cmd`() {
        val zipper = VectorZipper(0, listOf(samplePlantUi))
        val detailsState = PlantState.Details.Plant(zipper)
        val customTs = Instant.parse("2025-01-01T10:00:00Z")
        val modal = PlantState.PlantModal.Pruning(parent = detailsState, customTimestamp = customTs)
        modal.weightInput.edit { append("15.0") }

        val (nextState, cmd) = update(modal, PlantIntent.Pruning.Save, now)

        assertEquals(detailsState, nextState)
        assertTrue(cmd is Cmd.SavePruning)
        val saveCmd = cmd as Cmd.SavePruning
        assertEquals(15.0, saveCmd.removedGrams!!, 0.001)
        assertEquals(customTs.toEpochMilli(), saveCmd.timestampMs)
    }

    @Test
    fun `Repotting Save with valid weight returns SaveRepotting cmd`() {
        val zipper = VectorZipper(0, listOf(samplePlantUi))
        val detailsState = PlantState.Details.Plant(zipper)
        val modal = PlantState.PlantModal.Repotting(parent = detailsState)
        modal.weightInput.edit { append("800") }

        val (nextState, cmd) = update(modal, PlantIntent.Repotting.Save, now)

        assertEquals(detailsState, nextState)
        assertTrue(cmd is Cmd.SaveRepotting)
        val saveCmd = cmd as Cmd.SaveRepotting
        assertEquals(800.0, saveCmd.weightAfterGrams!!, 0.001)
    }

    @Test
    fun `EditingPlant Save with empty name stays in state, with valid name updates plant`() {
        val zipper = VectorZipper(0, listOf(samplePlantUi))
        val detailsState = PlantState.Details.Plant(zipper)
        val modal = PlantState.PlantModal.EditPlant(
            parent = detailsState,
            plantId = plantId,
            nameInput = TextFieldState(""),
            selectedSpeciesId = 5L,
            isDropdownExpanded = false,
            speciesList = emptyList()
        )

        // Пустое имя
        val (state1, cmd1) = update(modal, PlantIntent.EditingPlant.Save, now)
        assertEquals(modal, state1)
        assertEquals(Cmd.None, cmd1)

        // Валидное имя
        modal.nameInput.edit { append("New Name") }
        val (state2, cmd2) = update(modal, PlantIntent.EditingPlant.Save, now)
        assertEquals(detailsState, state2)
        assertTrue(cmd2 is Cmd.UpdatePlant)
        val updateCmd = cmd2 as Cmd.UpdatePlant
        assertEquals(plantId, updateCmd.plantId)
        assertEquals("New Name", updateCmd.name)
        assertEquals(5L, updateCmd.speciesId)
    }

    @Test
    fun `EditingName Save with empty name stays in state, with valid name updates plant`() {
        val overview = PlantState.Overview(plants = listOf(samplePlantUi))
        val modal = PlantState.EditingName(
            parent = overview,
            plantId = plantId,
            nameInput = TextFieldState(""),
            selectedSpeciesId = null,
            speciesList = emptyList()
        )

        val (state1, cmd1) = update(modal, PlantIntent.EditingName.Save, now)
        assertEquals(modal, state1)
        assertEquals(Cmd.None, cmd1)

        modal.nameInput.edit { append("Renamed") }
        val (state2, cmd2) = update(modal, PlantIntent.EditingName.Save, now)
        assertEquals(overview, state2)
        assertTrue(cmd2 is Cmd.UpdatePlant)
        assertEquals("Renamed", (cmd2 as Cmd.UpdatePlant).name)
    }

    @Test
    fun `EditingMeasurement Save issues UpdateMeasurement cmd and Saved returns parent`() {
        val zipper = VectorZipper(0, listOf(samplePlantUi))
        val detailsState = PlantState.Details.Plant(zipper)
        val history = PlantState.Details.History(parent = detailsState)
        val measId = MeasurementId(100L)
        val modal = PlantState.PlantModal.HistoryModal.EditMeasurement(
            parent = history,
            measurementId = measId,
            weightInput = TextFieldState("500"),
            originalTimestamp = now,
            timestamp = now
        )

        val (nextState1, cmd1) = update(modal, PlantIntent.EditingMeasurement.Save, now)
        assertEquals(modal, nextState1)
        assertTrue(cmd1 is Cmd.UpdateMeasurement)
        val updateCmd = cmd1 as Cmd.UpdateMeasurement
        assertEquals(measId, updateCmd.measurementId)
        assertEquals(500.0, updateCmd.weightGrams, 0.001)

        val (nextState2, cmd2) = update(modal, PlantIntent.EditingMeasurement.Saved, now)
        assertEquals(history, nextState2)
        assertEquals(Cmd.None, cmd2)
    }

    // 6. Обработка асинхронной загрузки (Loaded)
    @Test
    fun `Loaded Species updates speciesList in Creating and Editing states`() {
        val overview = PlantState.Overview(plants = listOf(samplePlantUi))
        val species = listOf(Species(SpeciesId(1L), "Monstera"))

        val creating = PlantState.Creating(parent = overview, form = AddPlantForm.empty(), speciesList = emptyList())
        val (creatingNext, _) = update(creating, PlantIntent.Loaded.CreatingSpecies(species), now)
        assertEquals(species, (creatingNext as PlantState.Creating).speciesList)

        val editingName = PlantState.EditingName(parent = overview, plantId = plantId, nameInput = TextFieldState("A"), selectedSpeciesId = null, speciesList = emptyList())
        val (editingNext, _) = update(editingName, PlantIntent.Loaded.EditingSpecies(species), now)
        assertEquals(species, (editingNext as PlantState.EditingName).speciesList)
    }

    // 7. Обновление списка растений (applyPlants)
    @Test
    fun `applyPlants updates Overview and details focus correctly`() {
        val uninit = PlantState.DBUninitialized
        val state1 = applyPlants(uninit, listOf(samplePlantUi))
        assertTrue(state1 is PlantState.Overview)
        assertEquals(1, (state1 as PlantState.Overview).plants.size)

        val plant2 = samplePlantUi.copy(id = PlantId(2L), name = "Ficus")
        val state2 = applyPlants(state1, listOf(samplePlantUi, plant2))
        assertEquals(2, (state2 as PlantState.Overview).plants.size)
    }

    // 8. Логика таймера и отложенных удалений (updateDeletions)
    @Test
    fun `Deletion Request starts delete timer`() {
        val pending = emptyList<PendingDelete>()
        val intent = PlantIntent.Overview.Deletion.Request(plantId)

        val (nextPending, cmd) = updateDeletions(pending, intent, 1000L)

        assertEquals(1, nextPending.size)
        assertEquals(plantId, nextPending.first().plantId)
        assertTrue(cmd is Cmd.StartDeleteTimer)
        assertEquals(plantId, (cmd as Cmd.StartDeleteTimer).plantId)
        assertEquals(DELETE_DELAY_MS, cmd.delayMs)
    }

    @Test
    fun `Deletion Undo cancels timer and removes pending item`() {
        val pending = listOf(PendingDelete(plantId, deadlineMs = 11000L))
        val intent = PlantIntent.Overview.Deletion.Undo(plantId)

        val (nextPending, cmd) = updateDeletions(pending, intent, 2000L)

        assertTrue(nextPending.isEmpty())
        assertTrue(cmd is Cmd.CancelDeleteTimer)
        assertEquals(plantId, (cmd as Cmd.CancelDeleteTimer).plantId)
    }

    @Test
    fun `Deletion Commit removes pending item and issues DeletePlant command`() {
        val pending = listOf(PendingDelete(plantId, deadlineMs = 11000L))
        val intent = PlantIntent.Overview.Deletion.Commit(plantId)

        val (nextPending, cmd) = updateDeletions(pending, intent, 12000L)

        assertTrue(nextPending.isEmpty())
        assertTrue(cmd is Cmd.DeletePlant)
        assertEquals(plantId, (cmd as Cmd.DeletePlant).plantId)
    }
}