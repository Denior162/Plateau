package com.zghonnyk.plateau.di

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.zghonnyk.plateau.data.db.PlateauDatabase

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
}