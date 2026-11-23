package com.zghonnyk.plateau.di

import android.net.Uri
import com.zghonnyk.plateau.data.repository.CopyPhoto
import com.zghonnyk.plateau.data.repository.DeletePhoto
import com.zghonnyk.plateau.data.repository.InsertPlant
import com.zghonnyk.plateau.data.repository.PotChoice
import java.io.InputStream
import java.io.OutputStream

class PlantDeps(
    val insertPlant: InsertPlant,
    val copyPhoto: CopyPhoto,
    val deletePhoto: DeletePhoto,
    val openOut: (Uri) -> OutputStream?,
    val openIn: (Uri) -> InputStream?,
)

class WateringDeps(
    val update: (id: Long, plantId: Long, oldTs: Long, newTs: Long, volumeMl: Double?) -> Unit,
)

class RepottingDeps(
    val insert: (plantId: Long, timestamp: Long, pot: PotChoice, weightAfterGrams: Double?) -> Unit,
)