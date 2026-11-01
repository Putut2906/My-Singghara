package com.example.j2me

import android.os.Bundle
import java.io.File
import javax.microedition.lcdui.Display
import javax.microedition.lcdui.MicroActivity
import javax.microedition.midlet.MIDlet
import javax.microedition.shell.ConfScreen
import javax.microedition.util.ContextHolder
import ua.naiksoftware.j2meloader.AppItem

/**
 * Hosts the embedded J2ME engine: shows the per-game run configuration first and hands
 * the screen over to the game once the user taps "Mulai".
 */
class J2meGameActivity : MicroActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The engine reads activity, inflater and display metrics through this holder.
        ContextHolder.setContext(this)

        val gameId = intent.getStringExtra(EXTRA_GAME_ID)
        val gameDir = gameId?.let { File(J2meStore.rootDir(this), it) }
        if (gameDir == null || !File(gameDir, J2meStore.DEX_FILE_NAME).isFile) {
            finish()
            return
        }

        val props = J2meStore.readManifest(File(gameDir, J2meStore.CONF_FILE_NAME))
        val title = props["MIDlet-Name"] ?: gameId
        setTitle(title)

        val item = AppItem(
            0,
            title,
            props["MIDlet-Vendor"].orEmpty(),
            props["MIDlet-Version"].orEmpty()
        )
        item.path = gameDir.absolutePath

        val midlet = ConfigMidlet(item)
        val configScreen = ConfScreen(midlet, item)
        configScreen.setTitle(title)
        Display.getDisplay(midlet).setCurrent(configScreen)
    }

    private class ConfigMidlet(private val app: AppItem) : MIDlet()

    companion object {
        const val EXTRA_GAME_ID = "com.example.j2me.extra.GAME_ID"
    }
}