package com.example.cowall.dependencyinjection

import com.example.cowall.repositories.DatabasesRepositoryImpl
import com.example.cowall.repositories.EditImageRepository
import com.example.cowall.viewmodel.ChatRoomViewModel
import com.example.cowall.viewmodel.EditImageViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module{
    viewModel{ EditImageViewModel(editImageRepository = get()) }
    viewModel { ChatRoomViewModel(databaseRepository = get()) }
}