package com.example.cowall.utilities

import android.app.Application
import com.example.cowall.dependencyinjection.repositoryModule
import com.example.cowall.dependencyinjection.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

@Suppress("unsed")
class AppConfig :Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@AppConfig)
            modules(listOf(repositoryModule, viewModelModule))
        }
    }

}