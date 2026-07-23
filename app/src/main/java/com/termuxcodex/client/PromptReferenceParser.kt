package com.termuxcodex.client

data class PromptReferenceToken(
    val marker: Char,
    val query: String,
    val start: Int,
    val end: Int,
)

fun activePromptReferenceToken(
    text: String,
    cursor: Int,
): PromptReferenceToken? {
    if (cursor !in 0..text.length) return null
    val start = text.lastIndexOfAny(charArrayOf('@', '$', '/'), startIndex = cursor - 1)
    if (start < 0) return null
    if (start > 0 && !text[start - 1].isWhitespace()) return null
    if (text[start] == '/' && text.substring(0, start).isNotBlank()) return null
    val query = text.substring(start + 1, cursor)
    if (query.any(Char::isWhitespace)) return null
    return PromptReferenceToken(text[start], query, start, cursor)
}

fun replacePromptReferenceToken(
    text: String,
    token: PromptReferenceToken,
    label: String,
): Pair<String, Int> {
    val replacement = "${token.marker}$label "
    val updated = text.replaceRange(token.start, token.end, replacement)
    return updated to token.start + replacement.length
}
