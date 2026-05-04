package com.example.cowall.utilities

import com.example.cowall.EmojiAnimationOverlay

object EmojiUtils {

    fun isLoveEmoji(emoji: String) = emoji.trim() in EmojiAnimationOverlay.LOVE_EMOJI_SET

    fun isEmojiOnly(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        var i = 0
        while (i < trimmed.length) {
            val cp = trimmed.codePointAt(i)
            if (!isEmojiCodePoint(cp) && !Character.isWhitespace(cp)) return false
            i += Character.charCount(cp)
        }
        return true
    }

    // Returns distinct emoji strings, preserving VS-16 and ZWJ sequences.
    fun extractUniqueEmojis(text: String): List<String> {
        val result = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        var i = 0
        val trimmed = text.trim()
        while (i < trimmed.length) {
            val cp = trimmed.codePointAt(i)
            val isBase = isEmojiCodePoint(cp)
                    && cp != 0x200D && cp != 0xFE0F && cp != 0x20E3
                    && cp !in 0xFE00..0xFE0F
                    && !Character.isWhitespace(cp)
            if (isBase) {
                val sb = StringBuilder()
                sb.appendCodePoint(cp)
                var j = i + Character.charCount(cp)
                // Collect trailing modifier / VS-16 / ZWJ sequences
                while (j < trimmed.length) {
                    val next = trimmed.codePointAt(j)
                    if (next == 0xFE0F || next == 0x20E3 || next in 0xFE00..0xFE0F || next == 0x200D) {
                        sb.appendCodePoint(next)
                        j += Character.charCount(next)
                        // Include the emoji that follows a ZWJ
                        if (next == 0x200D && j < trimmed.length) {
                            val afterZwj = trimmed.codePointAt(j)
                            if (isEmojiCodePoint(afterZwj)) {
                                sb.appendCodePoint(afterZwj)
                                j += Character.charCount(afterZwj)
                            }
                        }
                    } else {
                        break
                    }
                }
                val emoji = sb.toString()
                if (emoji !in seen) {
                    seen.add(emoji)
                    result.add(emoji)
                }
                i = j
            } else {
                i += Character.charCount(cp)
            }
        }
        return result
    }

    private fun isEmojiCodePoint(cp: Int): Boolean = when {
        cp == 0x200D -> true                         // ZWJ
        cp == 0xFE0F -> true                         // Variation Selector-16
        cp == 0x20E3 -> true                         // Combining Enclosing Keycap
        cp in 0xFE00..0xFE0F -> true                 // Variation Selectors
        cp in 0x1F000..0x1FFFF -> true               // Emoji supplementary plane
        cp in 0x2600..0x27BF -> true                 // Miscellaneous Symbols & Dingbats
        cp in 0x2300..0x23FF -> true                 // Miscellaneous Technical
        cp == 0xA9 || cp == 0xAE -> true             // © ®
        Character.getType(cp) == Character.OTHER_SYMBOL.toInt() -> true
        Character.getType(cp) == Character.MODIFIER_SYMBOL.toInt() -> true
        else -> false
    }
}
