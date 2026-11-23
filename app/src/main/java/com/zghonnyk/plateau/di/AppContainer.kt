package com.zghonnyk.plateau.di

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.zghonnyk.plateau.data.copyPhotoToInternal
import com.zghonnyk.plateau.data.db.PlateauDatabase
import com.zghonnyk.plateau.data.repository.insertNewPlant
import com.zghonnyk.plateau.data.repository.updateWatering
import java.io.File

class AppContainer(private val context: Context) {
    val database: PlateauDatabase by lazy {
        PlateauDatabase(
            driver = AndroidSqliteDriver(
                schema = PlateauDatabase.Schema,
                context = context,
                name = "plateau.db",
                callback = object : AndroidSqliteDriver.Callback(PlateauDatabase.Schema) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {
                        super.onConfigure(db)
                        db.setForeignKeyConstraintsEnabled(true)
                    }
                }
            )
        )
    }

    val plantDeps: PlantDeps by lazy {
        PlantDeps(
            insertPlant = insertNewPlant(database),
            copyPhoto = { uri -> copyPhotoToInternal(context, uri) },
            deletePhoto = { path -> File(context.filesDir, path).delete() },
            openOut = { context.contentResolver.openOutputStream(it) },
            openIn = { context.contentResolver.openInputStream(it) },
        )
    }

    val wateringDeps by lazy {
        WateringDeps(
            update = { id, plantId, oldTs, newTs, vol ->
                updateWatering(database, id, plantId, oldTs, newTs, vol)
            }
        )
    }
}