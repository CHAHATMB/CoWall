package com.example.cowall.utilities

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.cowall.InactivityWorker
import com.example.cowall.dependencyinjection.dataModule
import com.example.cowall.dependencyinjection.firebaseModule
import com.example.cowall.dependencyinjection.repositoryModule
import com.example.cowall.dependencyinjection.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.util.concurrent.TimeUnit

@Suppress("unused")
class AppConfig : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@AppConfig)
            modules(listOf(repositoryModule, viewModelModule, firebaseModule, dataModule))
        }

        applyTheme()
        scheduleInactivityReminder()
    }

    private fun scheduleInactivityReminder() {
        val inactivityRequest = PeriodicWorkRequestBuilder<InactivityWorker>(
            6, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "InactivityReminder",
            ExistingPeriodicWorkPolicy.KEEP,
            inactivityRequest
        )
    }

    private fun applyTheme() {
        val sharedPref = getSharedPreferences("cowall", Context.MODE_PRIVATE)
        val themeMode = sharedPref.getInt("themeMode", 0)
        val nightMode = when (themeMode) {
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}