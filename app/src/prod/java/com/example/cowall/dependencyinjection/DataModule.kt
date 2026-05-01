package com.example.cowall.dependencyinjection

import com.example.cowall.AuthProvider
import com.example.cowall.ChatConnector
import com.example.cowall.FireBaseConnector
import com.example.cowall.GoogleAuthProvider
import org.koin.dsl.module

val dataModule = module {
    single<ChatConnector> { FireBaseConnector() }
    single<AuthProvider> { GoogleAuthProvider() }
}
