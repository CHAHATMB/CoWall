package com.example.cowall.dependencyinjection

import com.google.firebase.firestore.FirebaseFirestore
import org.koin.dsl.module

val firebaseModule = module {
    single { provideFirestore() }
}

private fun provideFirestore(): FirebaseFirestore {
    return FirebaseFirestore.getInstance()
}
