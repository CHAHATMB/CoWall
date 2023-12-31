package com.example.cowall.dependencyinjection

import android.content.Context
import android.content.SharedPreferences
import com.example.cowall.repositories.DatabaseRepository
import com.example.cowall.repositories.DatabasesRepositoryImpl
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val firebaseModule = module {
    single { provideFirebaseStorage() }
    single { provideFirebaseDatabase() }
    single { provideSharedPreferences(androidContext()) }
    single { provideDatabaseRepository(androidContext(), get(), get(), get()) }
}

private fun provideFirebaseStorage(): FirebaseStorage {
    return Firebase.storage
}

private fun provideFirebaseDatabase(): FirebaseDatabase {
    return FirebaseDatabase.getInstance()
}
private fun provideSharedPreferences(context: Context): SharedPreferences {
//    return context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)
    return context.getSharedPreferences("cowall", Context.MODE_PRIVATE)
}

private fun provideDatabaseRepository(context:Context, database: FirebaseDatabase, storage: FirebaseStorage, sharedPreferences: SharedPreferences): DatabaseRepository {
    return DatabasesRepositoryImpl(context ,storage, database, sharedPreferences)
}