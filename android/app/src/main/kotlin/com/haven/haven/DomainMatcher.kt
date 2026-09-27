package com.haven.haven

object DomainMatcher {
    fun isBlocked(host: String, rules: Set<String>): Boolean {
        val hostname = host.lowercase().trimEnd('.')
        if (hostname.isEmpty()) return false

        for (rule in rules) {
            val normalized = rule.lowercase().trim()
            if (normalized.isEmpty()) continue

            if (normalized.startsWith("*.")) {
                val base = normalized.removePrefix("*.")
                if (hostname == base || hostname.endsWith(".$base")) {
                    return true
                }
            } else if (hostname == normalized || hostname.endsWith(".$normalized")) {
                return true
            }
        }
        return false
    }

    /** Rules as a lookup set for [isBlockedNormalized]: lowercase, no "*." prefix. */
    fun normalizeRules(rules: Collection<String>): Set<String> =
        rules.mapNotNullTo(HashSet()) { rule ->
            rule.lowercase().trim().removePrefix("*.").trimEnd('.').ifEmpty { null }
        }

    /** Same semantics as [isBlocked], but O(labels) for large lists. */
    fun isBlockedNormalized(host: String, rules: Set<String>): Boolean {
        var candidate = host.lowercase().trimEnd('.')
        while (candidate.isNotEmpty()) {
            if (candidate in rules) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
        return false
    }
}
