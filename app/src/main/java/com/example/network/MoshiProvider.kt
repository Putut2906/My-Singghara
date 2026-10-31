package com.example.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Shared Moshi instance for Retrofit clients.
 *
 * The API models are plain Kotlin classes without generated adapters, so the reflective
 * KotlinJsonAdapterFactory is required. A bare `Moshi.Builder().build()` makes Retrofit
 * fail with "Unable to create converter for class ..." on the first call.
 */
internal val networkMoshi: Moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()
