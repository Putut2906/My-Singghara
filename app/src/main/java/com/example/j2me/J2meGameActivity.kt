package com.example.j2me

import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.util.Collections
import java.util.TreeMap
import javax.microedition.lcdui.Canvas
import javax.microedition.lcdui.Display
import javax.microedition.lcdui.Font
import javax.microedition.lcdui.MicroActivity
import javax.microedition.lcdui.Screen
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

    private val ui = Handler(Looper.getMainLooper())
    private val logLines = Collections.synchronizedList(ArrayList<String>())

    private var hud: TextView? = null
    private var lastError: String? = null
    private var startInvoked = false
    private var diagnosticsShown = false

    private var gameTitle = ""
    private var midletClass = ""
    private var gameDir: File? = null
    private var profile: J2meProfile? = null
    private var previousUncaughtHandler: Thread.UncaughtExceptionHandler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextHolder.setContext(this)
        installCrashReporting()

        val gameId = intent.getStringExtra(EXTRA_GAME_ID)
        val jarPath = intent.getStringExtra(EXTRA_JAR_PATH)

        when {
            gameId != null && gameId.isNotBlank() -> openGame(gameId)
            jarPath != null && jarPath.isNotBlank() -> installThenStart(jarPath)
            else -> showError(IllegalArgumentException("Game tidak dipilih."))
        }
    }

    override fun setContentView(view: View) {
        val container = FrameLayout(this)
        container.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        val overlay = hud
        if (overlay != null) {
            // The status strip may still belong to the previous content view.
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            container.addView(overlay, hudParams())
        }
        super.setContentView(container)
    }

    private fun hudParams(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        )

    private fun showLoading(message: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(48, 48, 48, 48)
        }
        layout.addView(
            ProgressBar(this).apply { isIndeterminate = true },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        hud = TextView(this).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            isClickable = false
            isFocusable = false
            setPadding(32, 24, 32, 24)
        }
        layout.addView(
            hud,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        super.setContentView(layout)
    }

    private fun setStatus(message: String) {
        trace(message)
        ui.post {
            hud?.let {
                it.text = message
                it.setBackgroundColor(0xCC101010.toInt())
                it.gravity = Gravity.TOP or Gravity.START
            }
        }
    }

    private fun trace(message: String) {
        Log.i(TAG, message)
        logLines.add(message)
        if (logLines.size > 40) logLines.removeAt(0)
    }

    // ---------------------------------------------------------------- launching

    private fun openGame(gameId: String) {
        val dir = File(J2meStore.rootDir(this), gameId)
        gameDir = dir
        if (!File(dir, J2meStore.DEX_FILE_NAME).isFile) {
            showError(IllegalStateException("Data game tidak ditemukan. Tambahkan ulang berkas .jar."))
            return
        }

        val game = J2meStore.toGame(dir)
        gameTitle = game.title
        setTitle(game.title)

        try {
            val loaded = J2meProfiles.load(game)
            profile = loaded
            applyWindow(loaded)
            applyEngineSettings(loaded)
        } catch (t: Throwable) {
            showError(t)
            return
        }

        showLoading("Menyiapkan ${game.title}...")
        startFromDirectory(dir, game)
    }

    private fun installThenStart(jarPath: String) {
        showLoading("Memasang game...")
        setStatus("Memasang berkas .jar...")
        val activity = this
        Thread {
            try {
                val jar = File(jarPath)
                val game = runBlocking {
                    J2meInstaller.installFromJarFile(activity, jar, jar.name) { percent ->
                        setStatus("Memasang game... $percent%")
                    }
                }
                ui.post { openGame(game.id) }
            } catch (t: Throwable) {
                showError(t)
            }
        }.start()
    }

    /**
     * The dex is loaded off the UI thread; the MIDlet is constructed on the UI thread so
     * the engine's default screen and the game canvas are attached in a deterministic
     * order (building it on a worker posted the default screen after the game view and
     * left the activity blank).
     */
    private fun startFromDirectory(dir: File, game: J2meStore.Game) {
        val configured = profile ?: J2meProfiles.load(game).also { profile = it }
        val activity = this

        setStatus("Membaca data game...")
        Thread {
            try {
                val dex = File(dir, J2meStore.DEX_FILE_NAME)
                trace("dex: ${dex.absolutePath} (${dex.length()} byte)")

                val props = TreeMap<String, String>()
                props.putAll(J2meStore.readManifest(File(dir, J2meStore.CONF_FILE_NAME)))
                props.putAll(configured.propertiesMap())
                trace("manifest: ${props.size} properti")

                val mainClass = props["MIDlet-1"]
                    ?.substringAfterLast(',')
                    ?.trim()
                    .orEmpty()
                if (mainClass.isEmpty()) {
                    throw IllegalStateException("MIDlet-1 pada manifest game tidak valid.")
                }
                midletClass = mainClass
                trace("kelas MIDlet: $mainClass")

                Display.updateMetrics()
                ContextHolder.setResourcePath(dir.absolutePath)

                val loader = MyClassLoader(
                    dex.absolutePath,
                    codeCacheDir.absolutePath,
                    null,
                    activity.javaClass.classLoader,
                    File(dir, J2meStore.RES_DIR_NAME).absolutePath + "/"
                )

                setStatus("Memuat kelas game...")
                MIDlet.initTempProps(props)
                val midletType = loader.loadClass(mainClass)
                trace("kelas game dimuat")

                ui.post {
                    try {
                        setStatus("Menjalankan game...")
                        val midlet = midletType.getDeclaredConstructor().newInstance() as MIDlet
                        midlet.setProps(props)
                        applyVirtualKeyboard(midlet, configured)
                        midlet.start(AppItem(0, game.title, game.vendor, game.version).apply {
                            path = dir.absolutePath
                        })
                        startInvoked = true
                        trace("startApp dipanggil")
                        scheduleChecks()
                    } catch (t: Throwable) {
                        showError(t)
                    }
                }
            } catch (t: Throwable) {
                showError(t)
            }
        }.start()
    }

    private fun scheduleChecks() {
        ui.postDelayed({ forceFirstFrame() }, 700)
        ui.postDelayed({ checkRendering() }, 2500)
        ui.postDelayed({ forceFirstFrame() }, 3500)
        ui.postDelayed({ checkRendering() }, 6000)
        ui.postDelayed({ checkRendering() }, 12000)
    }

    /** Some MIDlets wait for the first repaint before they start drawing. */
    private fun forceFirstFrame() {
        if (isFinishing) return
        val current = getCurrent() as? Canvas ?: return
        if (Canvas.getFrameCount() == 0L) {
            trace("memaksa gambar pertama")
            current.repaint()
        }
    }

    private fun checkRendering() {
        if (isFinishing || diagnosticsShown) return

        val current = getCurrent()
        val frames = Canvas.getFrameCount()
        val paintErrors = Canvas.getPaintErrorCount()
        val rendering = current is Screen || (frames > 0L && paintErrors == 0)

        if (rendering) {
            hud?.let { view ->
                view.animate().alpha(0f).setDuration(400).withEndAction {
                    (view.parent as? ViewGroup)?.removeView(view)
                }.start()
            }
            hud = null
            return
        }

        val reason = when {
            paintErrors > 0 -> "Gambar game gagal dibuat: ${Canvas.getLastPaintError()}"
            current == null -> "MIDlet belum menampilkan layar."
            frames == 0L && Canvas.isSurfaceReady() ->
                "Permukaan gambar sudah siap tetapi belum ada frame yang tergambar."
            frames == 0L ->
                "Layar game sudah dipasang tetapi permukaan gambar belum siap."
            else -> "Layar game belum menampilkan apa pun."
        }
        showDiagnostics(reason, current)
    }

    // ---------------------------------------------------------------- settings

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
            trace("Keyboard virtual gagal: ${t.message}")
            Log.w(TAG, "Keyboard virtual tidak bisa diaktifkan", t)
        }
    }

    // ---------------------------------------------------------------- errors

    private fun installCrashReporting() {
        previousUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            trace("Galat pada thread ${thread.name}: ${throwable.javaClass.simpleName}")
            lastError = describe(throwable)
            if (thread.name == "main") {
                previousUncaughtHandler?.uncaughtException(thread, throwable)
            } else {
                ui.post {
                    if (!isFinishing) {
                        showDiagnostics(
                            "Terjadi galat di thread game (${thread.name}).",
                            getCurrent()
                        )
                    }
                }
            }
        }
    }

    private fun describe(t: Throwable): String = buildString {
        append(t.javaClass.name)
        t.message?.let { append(": ").append(it) }
        var cause = t.cause
        var depth = 0
        while (cause != null && cause !== t && depth < 3) {
            append("\n  caused by ")
            append(cause.javaClass.name)
            cause.message?.let { append(": ").append(it) }
            cause = cause.cause
            depth++
        }
    }

    private fun showError(t: Throwable) {
        Log.e(TAG, "Gagal menjalankan game", t)
        lastError = describe(t)
        trace("Galat: $lastError")

        ui.post {
            if (isFinishing) return@post
            AlertDialog.Builder(this)
                .setTitle("Game tidak bisa dijalankan")
                .setMessage(lastError?.take(1800) ?: "Galat tidak diketahui.")
                .setPositiveButton("Lihat detail") { _, _ ->
                    showDiagnostics("Game gagal dijalankan.", getCurrent())
                }
                .setNegativeButton("Tutup") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }

    /**
     * Replaces the blank game surface with a readable report so the failure can be
     * diagnosed without adb. The same text is stored in the game folder.
     */
    private fun showDiagnostics(reason: String, current: javax.microedition.lcdui.Displayable?) {
        if (diagnosticsShown || isFinishing) return
        diagnosticsShown = true

        val dex = gameDir?.let { File(it, J2meStore.DEX_FILE_NAME) }
        val report = buildString {
            appendLine(reason)
            appendLine()
            appendLine("Game: ${gameTitle.ifBlank { "-" }}")
            appendLine("Kelas MIDlet: ${midletClass.ifBlank { "-" }}")
            appendLine("Berkas dex: ${dex?.absolutePath ?: "-"}")
            appendLine("Ukuran dex: ${dex?.length() ?: 0} byte")
            profile?.let {
                appendLine("Layar virtual: ${it.screenWidth}x${it.screenHeight}")
                appendLine("Jenis skala: ${it.scaleType}, mode grafik: ${it.graphicsMode}")
                appendLine("Keyboard virtual: ${if (it.showKeyboard) "aktif" else "nonaktif"}")
            }
            appendLine("MIDlet dimuat: ${if (midletClass.isNotBlank()) "ya" else "belum"}")
            appendLine("startApp dipanggil: ${if (startInvoked) "ya" else "belum"}")
            appendLine("Displayable aktif: ${current?.javaClass?.name ?: "(belum ada)"}")
            appendLine("Permukaan gambar siap: ${if (Canvas.isSurfaceReady()) "ya" else "tidak"}")
            appendLine("Frame tergambar: ${Canvas.getFrameCount()}")
            appendLine("Galat gambar: ${Canvas.getPaintErrorCount()} kali")
            Canvas.getLastPaintError()?.let { appendLine("Galat gambar terakhir: $it") }
            lastError?.let { appendLine("Galat terakhir: $it") }
            appendLine()
            appendLine("Langkah yang bisa dicoba:")
            appendLine("1. Pengaturan game > Mode grafik: Kompatibilitas (software).")
            appendLine("2. Matikan Keyboard virtual.")
            appendLine("3. Ubah Jenis skala atau ukuran layar virtual (misal 240x320).")
            appendLine("4. Hapus lalu tambahkan ulang berkas .jar.")
            appendLine()
            appendLine("--- Log ---")
            synchronized(logLines) { logLines.forEach { appendLine(it) } }
        }

        saveReport(report)

        val content = TextView(this).apply {
            text = report
            textSize = 11.5f
            setTextColor(Color.WHITE)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(32, 32, 32, 32)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF111827.toInt())
            addView(content)
        }
        val close = Button(this).apply {
            text = "Tutup"
            setOnClickListener { finish() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF111827.toInt())
            addView(
                scroll,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
                )
            )
            addView(
                close,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        super.setContentView(root)
    }

    private fun saveReport(report: String) {
        try {
            val dir = gameDir ?: return
            File(dir, "last_run.log").writeText(report)
        } catch (t: Throwable) {
            Log.w(TAG, "Tidak bisa menulis laporan", t)
        }
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        Thread.setDefaultUncaughtExceptionHandler(previousUncaughtHandler)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "J2meGameActivity"
        const val EXTRA_GAME_ID = "com.example.j2me.extra.GAME_ID"
        const val EXTRA_JAR_PATH = "com.example.j2me.extra.JAR_PATH"
    }
}
