package dev.neonos

import java.text.Normalizer
import java.util.Locale

/** Immutable rank index, built off the IME thread. */
internal class KeyboardSuggestions(words: Sequence<String>) {
    private data class Word(val text: String, val rank: Int)
    private val byWord = words.filter { it.isNotBlank() }.distinct().mapIndexed { rank, text -> Word(text, rank) }
        .groupBy { normalize(it.text) }
    private val sorted = byWord.keys.sorted()

    fun suggest(token: String): List<String> {
        val query = normalize(token)
        if (query.length !in 1..24 || !query.all { it in 'a'..'z' }) return emptyList()
        val exact = byWord[query].orEmpty()
        val match = sorted.binarySearch(query)
        val start = if (match >= 0) match else -match - 1
        val completions = sorted.asSequence().drop(start).takeWhile { it.startsWith(query) }
            .filter { it != query }.mapNotNull { byWord[it]?.firstOrNull() }
            .sortedBy { it.rank }.take(3).toList()
        val alternatives = mutableListOf<Word>()
        if (exact.isEmpty() && query.length >= 3 && completions.size < 3) {
            val edits = hashSetOf<String>()
            for (index in query.indices) {
                val left = query.substring(0, index)
                val right = query.substring(index + 1)
                edits.add(left + right)
                if (index + 1 < query.length) edits.add(left + query[index + 1] + query[index] + query.substring(index + 2))
                for (letter in 'a'..'z') edits.add(left + letter + right)
            }
            for (index in 0..query.length) {
                val left = query.substring(0, index)
                val right = query.substring(index)
                for (letter in 'a'..'z') edits.add(left + letter + right)
            }
            alternatives += edits.mapNotNull { byWord[it]?.firstOrNull() }.sortedBy { it.rank }.take(3)
        }
        return (exact + completions + alternatives).asSequence()
            .map { matchCase(it.text, token) }
            .filter { it != token }
            .distinct().take(3).toList()
    }

    private fun matchCase(word: String, token: String) = when {
        token.length > 1 && token.all { it.isUpperCase() } -> word.uppercase(Locale.ROOT)
        token.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.titlecase(Locale.ROOT) }
        else -> word
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(MARKS, "")

    private companion object {
        val MARKS = "\\p{M}+".toRegex()
    }
}
