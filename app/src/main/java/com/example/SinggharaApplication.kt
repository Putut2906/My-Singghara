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
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:194078759561:android:b39d13cb8202ff43cfc458")
                    .setApiKey("AIzaSyC-1wqeoghlBosva2liyNCEEbb4IK6yvYs")
                    .setProjectId("mobile-d08aa")
                    .setStorageBucket("mobile-d08aa.firebasestorage.app")
                    .build()
                FirebaseApp.initializeApp(this, options)
                Log.i("SinggharaApplication", "FirebaseApp successfully initialized with explicit options")
            }
        } catch (e: Exception) {
            Log.e("SinggharaApplication", "Failed to initialize FirebaseApp", e)
        }
    }
}
