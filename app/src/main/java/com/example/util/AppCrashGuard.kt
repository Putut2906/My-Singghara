package com.example.util

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

/**
 * CoroutineScope yang dibuat lepas (bukan viewModelScope) tidak punya handler, jadi
 * exception yang lolos dari try/catch di dalam launch diteruskan ke uncaught handler
 * sistem dan proses aplikasi dimatikan Android. Guard ini mencatatnya tanpa menjatuhkan app.
 */
object AppCrashGuard {

    val handler = CoroutineExceptionHandler { _, throwable ->
        Log.e("AppCrashGuard", "Uncaught coroutine exception dibiarkan aman", throwable)
    }

    fun scope(vararg extras: CoroutineContext): CoroutineScope {
        val base: CoroutineContext = Dispatchers.IO + Job() + handler
        return CoroutineScope(extras.fold(base) { acc, extra -> acc + extra })
    }

    fun io(): CoroutineScope = scope()

    fun main(): CoroutineScope = scope(Dispatchers.Main)
}
