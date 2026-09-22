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
}
