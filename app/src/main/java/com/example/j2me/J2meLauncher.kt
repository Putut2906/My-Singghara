package com.example.j2me

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

/**
 * Public entry point for the embedded J2ME loader.
 *
 * [launchJar] mirrors launching the old "JarLauncher" activity: the path of the .jar is
 * passed as an Intent extra, then copied and installed inside the app's filesDir before
 * the MIDlet starts.
 */
object J2meLauncher {

    fun gameIntent(context: Context, gameId: String): Intent =
        Intent(context, J2meGameActivity::class.java)
            .putExtra(J2meGameActivity.EXTRA_GAME_ID, gameId)

    fun jarIntent(context: Context, jarPath: String): Intent =
        Intent(context, J2meGameActivity::class.java)
            .putExtra(J2meGameActivity.EXTRA_JAR_PATH, jarPath)

    fun launchGame(context: Context, gameId: String) {
        start(context, gameIntent(context, gameId))
    }

    /** Launches a .jar by absolute path; the file is installed on a background thread. */
    fun launchJar(context: Context, jarPath: String) {
        require(File(jarPath).isFile) { "Berkas .jar tidak ditemukan: $jarPath" }
        start(context, jarIntent(context, jarPath))
    }

    suspend fun importJar(context: Context, uri: Uri, displayName: String? = null): J2meStore.Game =
        J2meInstaller.install(context, uri, displayName) { }

    suspend fun importAndLaunch(context: Context, uri: Uri, displayName: String? = null) {
        val game = importJar(context, uri, displayName)
        launchGame(context, game.id)
    }

    private fun start(context: Context, intent: Intent) {
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
