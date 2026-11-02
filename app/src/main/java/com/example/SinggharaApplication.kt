package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class SinggharaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initFirebase()
    }

    private fun initFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                try {
                    FirebaseApp.initializeApp(this)
                } catch (e: Exception) {
                    Log.w("SinggharaApplication", "Default FirebaseApp init skipped, trying explicit options: ${e.message}")
                }
            }

            // Fallback dengan opsi eksplisit jika getApps masih kosong
            if (FirebaseApp.getApps(this).isEmpty()) {
                val config = com.example.firebase.FirebaseClientConfig.load(this)
                val options = FirebaseOptions.Builder()
                    .setApplicationId(config.appId)
                    .setApiKey(config.apiKey)
                    .setProjectId(config.projectId)
                    .setStorageBucket(config.storageBucket)
                    .build()
                FirebaseApp.initializeApp(this, options)
                Log.i("SinggharaApplication", "FirebaseApp successfully initialized with explicit options")
            }
        } catch (e: Exception) {
            Log.e("SinggharaApplication", "Failed to initialize FirebaseApp", e)
        }
    }
}
