package com.example.j2me

import android.view.KeyEvent
import java.io.File
import org.json.JSONObject

/**
 * Per-game run configuration. Values are auto-filled from the game's manifest on first
 * open and then persisted next to the game so every launch reuses the same settings.
 */
data class J2meProfile(
    val screenWidth: Int = 240,
    val screenHeight: Int = 320,
    val screenBackgroundColor: Int = 0xD0D0D0,
    val scaleRatio: Int = 100,
    val orientation: Int = ORIENTATION_AUTO,
    val scaleType: Int = SCALE_FIT,
    val gravity: Int = GRAVITY_CENTER,
    val keepAspectRatio: Boolean = true,
    val filter: Boolean = true,
    val immediateMode: Boolean = false,
    val graphicsMode: Int = GRAPHICS_STANDARD,
    val shader: Int = SHADER_NONE,
    val forceFullscreen: Boolean = false,
    val showFps: Boolean = false,
    val fpsLimit: Int = 0,
    val fontSizeSmall: Int = 18,
    val fontSizeMedium: Int = 22,
    val fontSizeLarge: Int = 26,
    val fontDimensions: Boolean = false,
    val fontAntialias: Boolean = true,
    val touchInput: Boolean = true,
    val showKeyboard: Boolean = true,
    val virtualKeyboardAlpha: Int = 64,
    val keyUp: Int = KeyEvent.KEYCODE_DPAD_UP,
    val keyDown: Int = KeyEvent.KEYCODE_DPAD_DOWN,
    val keyLeft: Int = KeyEvent.KEYCODE_DPAD_LEFT,
    val keyRight: Int = KeyEvent.KEYCODE_DPAD_RIGHT,
    val keyFire: Int = KeyEvent.KEYCODE_DPAD_CENTER,
    val keySoftLeft: Int = KeyEvent.KEYCODE_SOFT_LEFT,
    val keySoftRight: Int = KeyEvent.KEYCODE_SOFT_RIGHT,
    val systemProperties: String = ""
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put("ScreenWidth", screenWidth)
        put("ScreenHeight", screenHeight)
        put("ScreenBackgroundColor", screenBackgroundColor)
        put("ScreenScaleRatio", scaleRatio)
        put("Orientation", orientation)
        put("ScreenScaleType", scaleType)
        put("ScreenGravity", gravity)
        put("ScreenKeepAspectRatio", keepAspectRatio)
        put("ScreenFilter", filter)
        put("ImmediateMode", immediateMode)
        put("GraphicsMode", graphicsMode)
        put("Shader", shader)
        put("ForceFullscreen", forceFullscreen)
        put("ShowFps", showFps)
        put("FpsLimit", fpsLimit)
        put("FontSizeSmall", fontSizeSmall)
        put("FontSizeMedium", fontSizeMedium)
        put("FontSizeLarge", fontSizeLarge)
        put("FontApplyDimensions", fontDimensions)
        put("FontAntiAlias", fontAntialias)
        put("TouchInput", touchInput)
        put("ShowKeyboard", showKeyboard)
        put("VirtualKeyboardAlpha", virtualKeyboardAlpha)
        put("KeyUp", keyUp)
        put("KeyDown", keyDown)
        put("KeyLeft", keyLeft)
        put("KeyRight", keyRight)
        put("KeyFire", keyFire)
        put("KeySoftLeft", keySoftLeft)
        put("KeySoftRight", keySoftRight)
        put("SystemProperties", systemProperties)
    }

    /** Values from the text area override the manifest ones that MIDlets read. */
    fun propertiesMap(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        systemProperties.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val index = when {
                line.indexOf('=') > 0 -> line.indexOf('=')
                line.indexOf(':') > 0 -> line.indexOf(':')
                else -> -1
            }
            if (index > 0) {
                result[line.substring(0, index).trim()] = line.substring(index + 1).trim()
            }
        }
        return result
    }

    companion object {
        const val ORIENTATION_AUTO = 0
        const val ORIENTATION_PORTRAIT = 1
        const val ORIENTATION_LANDSCAPE = 2

        const val SCALE_NONE = 0
        const val SCALE_FIT = 1
        const val SCALE_STRETCH = 2

        const val GRAVITY_LEFT = 0
        const val GRAVITY_TOP = 1
        const val GRAVITY_CENTER = 2
        const val GRAVITY_RIGHT = 3
        const val GRAVITY_BOTTOM = 4

        const val GRAPHICS_SOFTWARE = 0
        const val GRAPHICS_STANDARD = 1

        const val SHADER_NONE = 0
        const val SHADER_BILINEAR = 1
        const val SHADER_NEAREST = 2

        fun defaultsFor(game: J2meStore.Game): J2meProfile {
            val props = J2meStore.readManifest(File(game.dir, J2meStore.CONF_FILE_NAME))
            val (width, height) = screenSizeFrom(props)
            return J2meProfile(
                screenWidth = width,
                screenHeight = height,
                orientation = orientationFrom(props),
                systemProperties = formatProperties(props)
            )
        }

        fun fromFile(file: File): J2meProfile? {
            if (!file.isFile) return null
            return runCatching { fromJson(JSONObject(file.readText())) }.getOrNull()
        }

        fun fromJson(json: JSONObject): J2meProfile {
            val defaults = J2meProfile()
            return J2meProfile(
                screenWidth = json.optInt("ScreenWidth", defaults.screenWidth),
                screenHeight = json.optInt("ScreenHeight", defaults.screenHeight),
                screenBackgroundColor = json.optInt("ScreenBackgroundColor", defaults.screenBackgroundColor),
                scaleRatio = json.optInt("ScreenScaleRatio", defaults.scaleRatio),
                orientation = json.optInt("Orientation", defaults.orientation),
                scaleType = json.optInt("ScreenScaleType", defaults.scaleType),
                gravity = json.optInt("ScreenGravity", defaults.gravity),
                keepAspectRatio = json.optBoolean("ScreenKeepAspectRatio", defaults.keepAspectRatio),
                filter = json.optBoolean("ScreenFilter", defaults.filter),
                immediateMode = json.optBoolean("ImmediateMode", defaults.immediateMode),
                graphicsMode = json.optInt("GraphicsMode", defaults.graphicsMode),
                shader = json.optInt("Shader", defaults.shader),
                forceFullscreen = json.optBoolean("ForceFullscreen", defaults.forceFullscreen),
                showFps = json.optBoolean("ShowFps", defaults.showFps),
                fpsLimit = json.optInt("FpsLimit", defaults.fpsLimit),
                fontSizeSmall = json.optInt("FontSizeSmall", defaults.fontSizeSmall),
                fontSizeMedium = json.optInt("FontSizeMedium", defaults.fontSizeMedium),
                fontSizeLarge = json.optInt("FontSizeLarge", defaults.fontSizeLarge),
                fontDimensions = json.optBoolean("FontApplyDimensions", defaults.fontDimensions),
                fontAntialias = json.optBoolean("FontAntiAlias", defaults.fontAntialias),
                touchInput = json.optBoolean("TouchInput", defaults.touchInput),
                showKeyboard = json.optBoolean("ShowKeyboard", defaults.showKeyboard),
                virtualKeyboardAlpha = json.optInt("VirtualKeyboardAlpha", defaults.virtualKeyboardAlpha),
                keyUp = json.optInt("KeyUp", defaults.keyUp),
                keyDown = json.optInt("KeyDown", defaults.keyDown),
                keyLeft = json.optInt("KeyLeft", defaults.keyLeft),
                keyRight = json.optInt("KeyRight", defaults.keyRight),
                keyFire = json.optInt("KeyFire", defaults.keyFire),
                keySoftLeft = json.optInt("KeySoftLeft", defaults.keySoftLeft),
                keySoftRight = json.optInt("KeySoftRight", defaults.keySoftRight),
                systemProperties = json.optString("SystemProperties", "")
            )
        }

        private fun screenSizeFrom(props: Map<String, String>): Pair<Int, Int> {
            val raw = props["Nokia-MIDlet-Original-Display-Size"]
                ?: props["Nokia-MIDlet-Target-Display-Size"]
                ?: props["MIDlet-Screen-Size"]
                ?: props["MIDlet-Device-Screen-Size"]
            val numbers = raw?.split(',', 'x', 'X')
                ?.mapNotNull { it.trim().toIntOrNull() }
                .orEmpty()
            if (numbers.size >= 2 && numbers[0] > 0 && numbers[1] > 0) {
                return numbers[0].coerceIn(64, 2048) to numbers[1].coerceIn(64, 2048)
            }
            return 240 to 320
        }

        private fun orientationFrom(props: Map<String, String>): Int {
            val raw = (props["Nokia-MIDlet-App-Orientation"] ?: props["MIDlet-Orientation"])
                ?.lowercase()
                .orEmpty()
            return when {
                raw.contains("landscape") -> ORIENTATION_LANDSCAPE
                raw.contains("portrait") -> ORIENTATION_PORTRAIT
                else -> ORIENTATION_AUTO
            }
        }

        private fun formatProperties(props: Map<String, String>): String =
            props.entries
                .sortedBy { it.key }
                .joinToString("\n") { "${it.key}=${it.value}" }
    }
}
