package com.example.j2me

import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.util.TreeMap
import javax.microedition.lcdui.Canvas
import javax.microedition.lcdui.Display
import javax.microedition.lcdui.Font
import javax.microedition.lcdui.MicroActivity
import javax.microedition.lcdui.pointer.VirtualKeyboard
import javax.microedition.midlet.MIDlet
import javax.microedition.shell.MyClassLoader
import javax.microedition.util.ContextHolder
import kotlinx.coroutines.runBlocking
import ua.naiksoftware.j2meloader.AppItem

/**
 * Runs one imported J2ME game. The per-game options are read from settings.json, the
 * MIDlet is loaded straight from filesDir/j2me/&lt;id&gt; and every failure is shown to
 * the user instead of being swallowed by the old configuration screen.
 */
class J2meGameActivity : MicroActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The engine reads activity, inflater and display metrics through this holder.
        ContextHolder.setContext(this)

        val gameId = intent.getStringExtra(EXTRA_GAME_ID)
        val jarPath = intent.getStringExtra(EXTRA_JAR_PATH)

        when {
            !gameId.isNullOrBlank() -> startGame(gameId)
            !jarPath.isNullOrBlank() -> installThenStart(jarPath)
            else -> showError(IllegalArgumentException("Game tidak dipilih."))
        }
    }

    private fun installThenStart(jarPath: String) {
        setContentView(loadingView())
        val activity = this
        Thread {
            try {
                val jar = File(jarPath)
                val game = runBlocking {
                    J2meInstaller.installFromJarFile(activity, jar, jar.name)
                }
                runOnUiThread { startGame(game.id) }
            } catch (t: Throwable) {
                showError(t)
            }
        }.start()
    }

    private fun startGame(gameId: String) {
        try {
            val dir = File(J2meStore.rootDir(this), gameId)
            if (!File(dir, J2meStore.DEX_FILE_NAME).isFile) {
                throw IllegalStateException("Data game tidak ditemukan. Tambahkan ulang berkas .jar.")
            }

            val game = J2meStore.toGame(dir)
            val profile = J2meProfiles.load(game)
            applyWindow(profile)
            applyEngineSettings(profile)

            Display.updateMetrics()
            ContextHolder.setResourcePath(dir.absolutePath)

            val props = TreeMap<String, String>()
            props.putAll(J2meStore.readManifest(File(dir, J2meStore.CONF_FILE_NAME)))
            props.putAll(profile.propertiesMap())
            MIDlet.initTempProps(props)

            val midletClass = props["MIDlet-1"]
                ?.substringAfterLast(',')
                ?.trim()
                .orEmpty()
            if (midletClass.isEmpty()) {
                throw IllegalStateException("MIDlet-1 pada manifest game tidak valid.")
            }

            val loader = MyClassLoader(
                File(dir, J2meStore.DEX_FILE_NAME).absolutePath,
                codeCacheDir.absolutePath,
                null,
                javaClass.classLoader,
                File(dir, J2meStore.RES_DIR_NAME).absolutePath + "/"
            )
            Log.i(TAG, "Memuat $midletClass dari ${dir.absolutePath}")
            val midlet = loader.loadClass(midletClass)
                .getDeclaredConstructor()
                .newInstance() as MIDlet
            midlet.setProps(props)

            applyVirtualKeyboard(midlet, profile)

            val item = AppItem(0, game.title, game.vendor, game.version)
            item.path = dir.absolutePath
            setTitle(game.title)
            midlet.start(item)
            Log.i(TAG, "MIDlet berjalan: ${game.title}")
        } catch (t: Throwable) {
            showError(t)
        }
    }

    private fun applyWindow(profile: J2meProfile) {
        when (profile.orientation) {
            J2meProfile.ORIENTATION_PORTRAIT ->
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            J2meProfile.ORIENTATION_LANDSCAPE ->
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else ->
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        if (profile.forceFullscreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    private fun applyEngineSettings(profile: J2meProfile) {
        val scaleType = if (profile.scaleType == J2meProfile.SCALE_FIT && !profile.keepAspectRatio) {
            J2meProfile.SCALE_STRETCH
        } else {
            profile.scaleType
        }

        Canvas.applyProfile(
            profile.screenWidth,
            profile.screenHeight,
            scaleType,
            profile.gravity,
            profile.scaleRatio,
            profile.filter,
            profile.screenBackgroundColor,
            profile.immediateMode,
            profile.showFps,
            profile.fpsLimit,
            profile.touchInput,
            profile.graphicsMode,
            profile.shader
        )

        Font.setSize(Font.SIZE_SMALL, profile.fontSizeSmall.toFloat())
        Font.setSize(Font.SIZE_MEDIUM, profile.fontSizeMedium.toFloat())
        Font.setSize(Font.SIZE_LARGE, profile.fontSizeLarge.toFloat())
        Font.setApplyDimensions(profile.fontDimensions)
        Font.setAntiAlias(profile.fontAntialias)

        Canvas.remapGameAction(Canvas.UP, profile.keyUp, "UP")
        Canvas.remapGameAction(Canvas.DOWN, profile.keyDown, "DOWN")
        Canvas.remapGameAction(Canvas.LEFT, profile.keyLeft, "LEFT")
        Canvas.remapGameAction(Canvas.RIGHT, profile.keyRight, "RIGHT")
        Canvas.remapGameAction(Canvas.FIRE, profile.keyFire, "FIRE")
        Canvas.remapSoftKey(profile.keySoftLeft, true)
        Canvas.remapSoftKey(profile.keySoftRight, false)
    }

    private fun applyVirtualKeyboard(midlet: MIDlet, profile: J2meProfile) {
        if (!profile.showKeyboard) return
        try {
            val keyboard = VirtualKeyboard()
            keyboard.setOverlayAlpha(profile.virtualKeyboardAlpha)
            Display.getDisplay(midlet).setOverlay(keyboard)
        } catch (t: Throwable) {
            Log.w(TAG, "Keyboard virtual tidak bisa diaktifkan", t)
        }
    }

    private fun loadingView(): TextView = TextView(this).apply {
        text = "Menyiapkan game..."
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.BLACK)
    }

    private fun showError(t: Throwable) {
        Log.e(TAG, "Gagal menjalankan game", t)
        val details = buildString {
            append(t.message ?: t.javaClass.simpleName)
            var cause = t.cause
            while (cause != null && cause !== t) {
                append("\n\n")
                append(cause.message ?: cause.javaClass.simpleName)
                cause = cause.cause
            }
        }.take(1800)

        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            AlertDialog.Builder(this)
                .setTitle("Game tidak bisa dijalankan")
                .setMessage(details)
                .setPositiveButton("Tutup") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }

    companion object {
        private const val TAG = "J2meGameActivity"
        const val EXTRA_GAME_ID = "com.example.j2me.extra.GAME_ID"
        const val EXTRA_JAR_PATH = "com.example.j2me.extra.JAR_PATH"
    }
}
