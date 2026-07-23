package com.termuxcodex.client

/** App Server filesystem APIs require an absolute, NUL-free path. */
fun isValidWorkspacePath(path: String): Boolean {
    val normalized = path.trim()
    return normalized.isNotEmpty() && normalized.startsWith('/') && '\u0000' !in normalized
}

fun CodexModel.supportsInputModality(modality: String): Boolean =
    inputModalities.any { it.equals(modality, ignoreCase = true) }
