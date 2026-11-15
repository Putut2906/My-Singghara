package com.aistudio.singghara.shared

actual class Platform actual constructor() {
    actual val name: String = "Desktop ${System.getProperty("os.name")} (${System.getProperty("os.arch")})"
}
