package com.example.cowall.dependencyinjection

import com.example.cowall.repositories.EditImageRepository
import com.example.cowall.repositories.EditImageRepositoryImpl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val repositoryModule = module {
    factory<EditImageRepository> { EditImageRepositoryImpl(androidContext()) }
}