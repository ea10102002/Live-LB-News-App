package com.usj.news_application

import android.app.Application
import com.google.android.libraries.places.api.Places

class NewsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Places.initialize(applicationContext, "YOUR_GOOGLE_MAPS_API_KEY")
    }
}