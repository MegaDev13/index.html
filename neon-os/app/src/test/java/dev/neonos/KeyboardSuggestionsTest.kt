package dev.neonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardSuggestionsTest {
    private val index = KeyboardSuggestions(sequenceOf("você", "obrigado", "café", "cafeteria", "correção", "legal"))

    @Test fun suggestsAccentsAndKeepsCapitalization() {
        assertEquals("você", index.suggest("voce").first())
        assertEquals("Café", index.suggest("Cafe").first())
        assertEquals("CORREÇÃO", index.suggest("CORRECAO").first())
    }

    @Test fun supportsPrefixAndCommonTypos() {
        assertEquals("obrigado", index.suggest("obri").first())
        assertEquals("legal", index.suggest("leagl").first())
    }

    @Test fun ignoresNonWordsAndLongTokens() {
        assertTrue(index.suggest("a@b").isEmpty())
        assertTrue(index.suggest("x".repeat(30)).isEmpty())
    }
}
