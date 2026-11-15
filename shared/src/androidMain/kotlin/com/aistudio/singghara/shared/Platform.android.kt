package com.aistudio.singghara.shared

import android.os.Build

actual class Platform actual constructor() {
    actual val name: String = "Android SDK ${Build.VERSION.SDK_INT}"
}
