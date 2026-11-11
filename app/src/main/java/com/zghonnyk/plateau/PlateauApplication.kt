package com.zghonnyk.plateau

import android.app.Application
import com.zghonnyk.plateau.di.AppContainer

class PlateauApplication : Application() {
    val container: AppContainer by lazy {
        AppContainer(this)
    }
}