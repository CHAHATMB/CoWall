package com.example.cowall.dependencyinjection

import com.example.cowall.AuthProvider
import com.example.cowall.ChatConnector
import com.example.cowall.MockAuthProvider
import com.example.cowall.MockChatConnector
import org.koin.dsl.module

val dataModule = module {
    single<ChatConnector> { MockChatConnector() }
    single<AuthProvider> { MockAuthProvider() }
}
