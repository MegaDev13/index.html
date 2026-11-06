package dev.neonos

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardConfigTest {
    @Test fun selectedTextStylesUseUnicodeAndPreservePunctuation() {
        assertEquals("Olá!", KeyboardConfig.stylize("Olá!", 0))
        assertEquals("𝐀𝐛 ç 𝟐", KeyboardConfig.stylize("Ab ç 2", 1))
        assertEquals("𝐴ℎ", KeyboardConfig.stylize("Ah", 2))
        assertEquals("𝙰𝚣𝟷", KeyboardConfig.stylize("Az1", 3))
    }
}
