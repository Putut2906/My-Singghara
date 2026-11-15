package com.aistudio.singghara.shared

/**
 * Fase 2 toolchain probe. `expect` in commonMain, `actual` per platform.
 * This is the pattern later phases use for Context/preferences/network/etc.
 */
expect class Platform() {
    val name: String
}

class Greeting {
    fun greet(): String = "Halo dari ${Platform().name}!"
}
