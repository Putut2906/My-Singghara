package com.aistudio.singghara.shared

import kotlin.test.Test
import kotlin.test.assertTrue

class GreetingTest {
    @Test
    fun greetIsNotPlatformSpecific() {
        assertTrue(Greeting().greet().startsWith("Halo dari "))
    }
}
