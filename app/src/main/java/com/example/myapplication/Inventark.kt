package com.example.myapplication

import android.app.Application

class Inventark : Application() {

    val databaseInitializer by lazy { DatabaseInitializer(this) }

    override fun onCreate() {
        super.onCreate()
    }
}
