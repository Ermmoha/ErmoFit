package com.ermofit.app.ui.components

internal fun imageUrlCandidates(rawUrl: String): List<String> {
    val normalized = rawUrl.trim()
    if (normalized.isBlank()) return emptyList()

    val candidates = linkedSetOf<String>()

    fun add(url: String) {
        if (url.isNotBlank()) candidates += url
    }

    fun withAndWithoutImages(url: String) {
        add(url)
        if (url.contains("/images/")) {
            add(url.replace("/images/", "/"))
        }
    }

    withAndWithoutImages(normalized)

    val jsdelivrPrefix = "https://cdn.jsdelivr.net/gh/yuhonas/free-exercise-db@main/"
    val fastlyPrefix = "https://fastly.jsdelivr.net/gh/yuhonas/free-exercise-db@main/"
    val rawGitHubPrefix = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/"

    if (normalized.startsWith(jsdelivrPrefix)) {
        val tail = normalized.removePrefix(jsdelivrPrefix)
        withAndWithoutImages("$fastlyPrefix$tail")
        withAndWithoutImages("$rawGitHubPrefix$tail")
    }

    if (normalized.startsWith(fastlyPrefix)) {
        val tail = normalized.removePrefix(fastlyPrefix)
        withAndWithoutImages("$jsdelivrPrefix$tail")
        withAndWithoutImages("$rawGitHubPrefix$tail")
    }

    if (normalized.startsWith(rawGitHubPrefix)) {
        val tail = normalized.removePrefix(rawGitHubPrefix)
        withAndWithoutImages("$jsdelivrPrefix$tail")
        withAndWithoutImages("$fastlyPrefix$tail")
    }

    return candidates.toList()
}
