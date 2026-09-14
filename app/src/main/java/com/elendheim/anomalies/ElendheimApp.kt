package com.elendheim.anomalies

import android.app.Application

class ElendheimApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Short hand used by every view model factory to reach the shared wiring. */
val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as ElendheimApp).container
