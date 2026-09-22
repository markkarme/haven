package com.haven.haven

import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.regex.Pattern

/**
 * Reads only browser address-bar nodes to detect visited hosts.
 * This avoids false positives from suggestion rows and page content.
 */
object BrowserUrlInspector {
    private val BROWSER_PACKAGES = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "com.google.android.apps.chrome",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.focus",
        "org.mozilla.fennec_fdroid",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.opera.touch",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.duckduckgo.mobile.android",
        "com.sec.android.app.sbrowser",
        "com.samsung.android.app.sbrowser",
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
        "com.ecosia.android",
        "com.android.browser",
        "com.huawei.browser",
        "com.mi.globalbrowser",
        "com.uc.browser.en",
        "mark.via.gp",
    )

    private val ADDRESS_BAR_ID_HINTS = listOf(
        "url_bar",
        "url_bar_title",
        "location_bar",
        "location_bar_edit_text",
        "omnibox",
        "mozac_browser_toolbar_url",
        "address_bar",
        "addressbar",
        "broker_url",
        "url_field",
        "search_box_text",
    )

    private val URL_PATTERN: Pattern = Pattern.compile(
        "(?i)(?:https?://)?(?:www\\.)?([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+)(?:[:/\\?#].*)?",
    )
    private val ADULT_KEYWORDS = setOf(
        "sex",
        "porn",
        "xxx",
        "hentai",
        "xnxx",
        "xvideos",
        "redtube",
        "pornhub",
        "سكس",
        "جنس",
        "اباحي",
        "اباحية",
        "مواقع سكس",
    )
    private val EMBEDDED_URL_PARAM_KEYS = setOf(
        "url",
        "u",
        "q",
        "target",
        "dest",
        "destination",
        "redirect",
        "redir",
        "to",
        "link",
    )
    private val SEARCH_HOST_HINTS = setOf(
        "google.",
        "bing.com",
        "duckduckgo.com",
        "search.yahoo.com",
        "yandex.",
        "ecosia.org",
    )

    fun isBrowserPackage(packageName: String): Boolean =
        BROWSER_PACKAGES.contains(packageName) ||
            packageName.contains("browser", ignoreCase = true) ||
            packageName.contains("internet", ignoreCase = true) ||
            packageName.contains("webview", ignoreCase = true) ||
            packageName.contains("chrome", ignoreCase = true) ||
            packageName.contains("firefox", ignoreCase = true)

    fun findBlockedHost(root: AccessibilityNodeInfo?, rules: Set<String>): String? {
        if (root == null || rules.isEmpty()) return null
        val bars = ArrayList<AccessibilityNodeInfo>()
        collectAddressBars(root, bars, depth = 0)
        if (bars.isEmpty()) return null

        try {
            val typing = bars.any { it.isFocused || it.isAccessibilityFocused }
            for (bar in bars) {
                if (typing && !bar.isFocused && !bar.isAccessibilityFocused) continue
                val text = nodeText(bar) ?: continue
                val host = findBlockedHostInCandidate(text, rules) ?: continue
                if (DomainMatcher.isBlocked(host, rules)) return host
            }
        } finally {
            for (bar in bars) {
                try {
                    bar.recycle()
                } catch (_: Exception) {
                }
            }
        }
        return null
    }

    /**
     * Returns blocked host only after URL/navigation is committed.
     * If the address bar is focused, user is still typing/suggesting -> no block.
     */
    fun findBlockedHostAfterEnter(root: AccessibilityNodeInfo?, rules: Set<String>): String? {
        if (root == null || rules.isEmpty()) return null
        val bars = ArrayList<AccessibilityNodeInfo>()
        collectAddressBars(root, bars, depth = 0)
        if (bars.isEmpty()) return null

        try {
            val typing = bars.any { it.isFocused || it.isAccessibilityFocused }
            if (typing) return null
            for (bar in bars) {
                val text = nodeText(bar) ?: continue
                val host = findBlockedHostInCandidate(text, rules) ?: continue
                if (DomainMatcher.isBlocked(host, rules)) return host
            }
        } finally {
            for (bar in bars) {
                try {
                    bar.recycle()
                } catch (_: Exception) {
                }
            }
        }
        return null
    }

    /**
     * Fast path for typed/pasted URL text from browser events. This catches
     * deep URLs such as x.com/... even when the UI tree later switches to title text.
     */
    fun findBlockedHostFromEvent(
        event: AccessibilityEvent?,
        rules: Set<String>,
    ): String? {
        if (event == null || rules.isEmpty()) return null
        val source = try {
            event.source
        } catch (_: Exception) {
            null
        } ?: return null

        try {
            if (!isLikelyAddressBarNode(source)) return null
            val candidates = ArrayList<String>(4)
            for (piece in event.text) {
                val raw = piece?.toString()?.trim().orEmpty()
                if (raw.isNotEmpty()) candidates.add(raw)
            }
            val sourceText = nodeText(source)
            if (!sourceText.isNullOrEmpty()) candidates.add(sourceText)
            val eventDesc = event.contentDescription?.toString()?.trim().orEmpty()
            if (eventDesc.isNotEmpty()) candidates.add(eventDesc)

            for (candidate in candidates) {
                val host = findBlockedHostInCandidate(candidate, rules) ?: continue
                if (DomainMatcher.isBlocked(host, rules)) return host
            }
        } finally {
            try {
                source.recycle()
            } catch (_: Exception) {
            }
        }
        return null
    }

    /** True when an accessibility event comes from the actual browser address bar. */
    fun isAddressBarEvent(event: AccessibilityEvent?): Boolean {
        if (event == null) return false
        val source = try {
            event.source
        } catch (_: Exception) {
            null
        } ?: return false
        return try {
            isLikelyAddressBarNode(source)
        } finally {
            try {
                source.recycle()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Reads adult keywords only from direct address-bar text-change events.
     * Suggestions/history rows should not trigger this.
     */
    fun findBlockedAdultKeywordFromEvent(event: AccessibilityEvent?): String? {
        if (event == null) return null
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return null
        val source = try {
            event.source
        } catch (_: Exception) {
            null
        } ?: return null

        try {
            if (!isLikelyAddressBarNode(source)) return null
            val candidates = ArrayList<String>(4)
            for (piece in event.text) {
                val raw = piece?.toString()?.trim().orEmpty()
                if (raw.isNotEmpty()) candidates.add(raw)
            }
            val sourceText = nodeText(source)
            if (!sourceText.isNullOrEmpty()) candidates.add(sourceText)
            val eventDesc = event.contentDescription?.toString()?.trim().orEmpty()
            if (eventDesc.isNotEmpty()) candidates.add(eventDesc)

            for (candidate in candidates) {
                val matched = findAdultKeywordIn(candidate)
                if (matched != null) return matched
            }
        } finally {
            try {
                source.recycle()
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Handles history/suggestion click rows in browser UIs.
     * These nodes are often not editable address-bar nodes, but they usually
     * carry URL/domain text we can parse safely on click events.
     */
    fun findBlockedHostFromClickEvent(
        event: AccessibilityEvent?,
        rules: Set<String>,
    ): String? {
        if (event == null || rules.isEmpty()) return null
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return null

        val candidates = LinkedHashSet<String>()
        for (piece in event.text) {
            val raw = piece?.toString()?.trim().orEmpty()
            if (raw.isNotEmpty()) candidates.add(raw)
        }
        val eventDesc = event.contentDescription?.toString()?.trim().orEmpty()
        if (eventDesc.isNotEmpty()) candidates.add(eventDesc)

        val source = try {
            event.source
        } catch (_: Exception) {
            null
        }
        if (source != null) {
            try {
                collectNodeTexts(source, candidates, depth = 0, maxItems = 16)
            } finally {
                try {
                    source.recycle()
                } catch (_: Exception) {
                }
            }
        }

        for (candidate in candidates) {
            val host = findBlockedHostInCandidate(candidate, rules) ?: continue
            if (DomainMatcher.isBlocked(host, rules)) return host
        }
        return null
    }

    /**
     * Matches explicit adult terms typed/searched in the browser address bar.
     * This restores keyword blocking without relying on noisy page/suggestion text.
     */
    fun findBlockedAdultKeyword(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        val bars = ArrayList<AccessibilityNodeInfo>()
        collectAddressBars(root, bars, depth = 0)
        if (bars.isEmpty()) return null

        try {
            // While typing (address bar focused), do not keyword-block.
            // This prevents suggestion/autocomplete text from blocking too early.
            val typing = bars.any { it.isFocused || it.isAccessibilityFocused }
            if (typing) return null
            for (bar in bars) {
                val text = nodeText(bar) ?: continue
                val matched = findAdultKeywordIn(text)
                if (matched != null) return matched
            }
        } finally {
            for (bar in bars) {
                try {
                    bar.recycle()
                } catch (_: Exception) {
                }
            }
        }
        return null
    }

    /**
     * Scans visible browser content (search results/page text) for explicit terms.
     * Used to block result pages containing adult content like "xxx", "xvideos", etc.
     */
    fun findBlockedAdultKeywordInVisibleContent(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        // Only scan visible content on search-results pages.
        if (!isSearchResultsPage(root)) return null
        val texts = LinkedHashSet<String>()
        collectVisibleTexts(root, texts, depth = 0)
        for (text in texts) {
            val matched = findAdultKeywordIn(text)
            if (matched != null) return matched
        }
        return null
    }

    private fun collectAddressBars(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>,
        depth: Int,
    ) {
        if (depth > 32) return
        val viewId = try {
            node.viewIdResourceName?.lowercase(Locale.US).orEmpty()
        } catch (_: Exception) {
            ""
        }
        if (looksLikeAddressBarId(viewId)) {
            out.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            } ?: continue
            try {
                collectAddressBars(child, out, depth + 1)
            } finally {
                try {
                    child.recycle()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun looksLikeAddressBarId(viewId: String): Boolean {
        if (viewId.isEmpty()) return false
        return ADDRESS_BAR_ID_HINTS.any { hint ->
            if (hint == "search_box_text") {
                viewId.endsWith("/search_box_text")
            } else {
                viewId.contains(hint)
            }
        }
    }

    private fun isLikelyAddressBarNode(node: AccessibilityNodeInfo): Boolean {
        val viewId = try {
            node.viewIdResourceName?.lowercase(Locale.US).orEmpty()
        } catch (_: Exception) {
            ""
        }
        if (looksLikeAddressBarId(viewId)) return true
        return node.isEditable && (node.isFocused || node.isAccessibilityFocused)
    }

    private fun nodeText(node: AccessibilityNodeInfo): String? {
        val text = node.text?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) return text
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        return desc.ifEmpty { null }
    }

    private fun isAddressBarFocused(root: AccessibilityNodeInfo): Boolean {
        val bars = ArrayList<AccessibilityNodeInfo>()
        collectAddressBars(root, bars, depth = 0)
        if (bars.isEmpty()) return false
        try {
            return bars.any { it.isFocused || it.isAccessibilityFocused }
        } finally {
            for (bar in bars) {
                try {
                    bar.recycle()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun isSearchResultsPage(root: AccessibilityNodeInfo): Boolean {
        val bars = ArrayList<AccessibilityNodeInfo>()
        collectAddressBars(root, bars, depth = 0)
        if (bars.isEmpty()) {
            // Some browsers don't expose address-bar ids; fallback to visible text scan.
            return isSearchResultsFromVisibleTexts(root)
        }
        try {
            for (bar in bars) {
                val text = nodeText(bar) ?: continue
                if (looksLikeSearchResultsUrl(text)) return true
            }
        } finally {
            for (bar in bars) {
                try {
                    bar.recycle()
                } catch (_: Exception) {
                }
            }
        }
        return isSearchResultsFromVisibleTexts(root)
    }

    private fun looksLikeSearchResultsUrl(raw: String): Boolean {
        val value = raw.trim().lowercase(Locale.US)
        if (value.isEmpty()) return false
        // Avoid browser history pages and other internal URLs.
        if (value.startsWith("chrome://") || value.startsWith("about:")) return false
        val host = extractHost(value) ?: return false
        val isSearchHost = SEARCH_HOST_HINTS.any { hint -> host.contains(hint) }
        if (!isSearchHost) return false
        return value.contains("/search") || value.contains("search?q=") || value.contains("?q=")
    }

    private fun isSearchResultsFromVisibleTexts(root: AccessibilityNodeInfo): Boolean {
        val texts = LinkedHashSet<String>()
        collectVisibleTexts(root, texts, depth = 0)
        var sawSearchHost = false
        var sawSearchPathOrQuery = false
        var sawResultsUiHint = false
        var checked = 0
        for (raw in texts) {
            if (checked++ > 180) break
            val value = raw.trim().lowercase(Locale.US)
            if (value.isEmpty()) continue

            if (!sawSearchHost) {
                val host = extractHost(value)
                if (host != null && SEARCH_HOST_HINTS.any { hint -> host.contains(hint) }) {
                    sawSearchHost = true
                }
            }

            if (!sawSearchPathOrQuery &&
                (value.contains("/search") || value.contains("search?q=") || value.contains("?q="))
            ) {
                sawSearchPathOrQuery = true
            }

            if (!sawResultsUiHint &&
                (value == "safesearch" || value.contains("images") || value.contains("videos"))
            ) {
                sawResultsUiHint = true
            }
        }
        return (sawSearchHost && sawSearchPathOrQuery) ||
            (sawSearchHost && sawResultsUiHint)
    }

    private fun collectVisibleTexts(
        node: AccessibilityNodeInfo,
        out: MutableSet<String>,
        depth: Int,
    ) {
        if (depth > 28 || out.size > 220) return
        val text = node.text?.toString()?.trim().orEmpty()
        if (text.isNotEmpty() && text.length <= 220) out.add(text)
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        if (desc.isNotEmpty() && desc.length <= 220) out.add(desc)

        for (i in 0 until node.childCount) {
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            } ?: continue
            try {
                collectVisibleTexts(child, out, depth + 1)
            } finally {
                try {
                    child.recycle()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun collectNodeTexts(
        node: AccessibilityNodeInfo,
        out: MutableSet<String>,
        depth: Int,
        maxItems: Int,
    ) {
        if (depth > 4 || out.size >= maxItems) return
        val text = node.text?.toString()?.trim().orEmpty()
        if (text.isNotEmpty() && text.length <= 260) out.add(text)
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        if (desc.isNotEmpty() && desc.length <= 260) out.add(desc)

        for (i in 0 until node.childCount) {
            if (out.size >= maxItems) return
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            } ?: continue
            try {
                collectNodeTexts(child, out, depth + 1, maxItems)
            } finally {
                try {
                    child.recycle()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun extractHost(raw: String): String? {
        var value = raw.trim()
        if (value.isEmpty() || value.length > 500) return null
        if (value.contains(' ') && !value.contains("://")) return null
        if (!value.contains('.') && !value.contains("://")) return null

        value = value.lowercase(Locale.US)
        value = value.removePrefix("view-source:")
        val cut = value.indexOfFirst { it == '–' || it == '—' || it == '|' }
        if (cut > 0) value = value.substring(0, cut).trim()

        val matcher = URL_PATTERN.matcher(value)
        if (!matcher.find()) return null
        var host = matcher.group(1) ?: return null
        host = host.trim('.').lowercase(Locale.US)
        if (host.isEmpty() || !host.contains('.')) return null
        return host
    }

    private fun findBlockedHostInCandidate(candidate: String, rules: Set<String>): String? {
        val hosts = extractHosts(candidate)
        for (host in hosts) {
            if (DomainMatcher.isBlocked(host, rules)) return host
        }
        return null
    }

    private fun extractHosts(raw: String): Set<String> {
        val hosts = LinkedHashSet<String>()
        val directHost = extractHost(raw)
        if (directHost != null) hosts.add(directHost)

        val value = raw.trim()
        if (!value.contains("://")) return hosts
        val uri = try {
            Uri.parse(value)
        } catch (_: Exception) {
            null
        } ?: return hosts

        for (key in EMBEDDED_URL_PARAM_KEYS) {
            val param = try {
                uri.getQueryParameter(key)
            } catch (_: Exception) {
                null
            } ?: continue

            val decoded = decodeParam(param)
            val host = extractHost(decoded)
            if (host != null) hosts.add(host)
        }
        return hosts
    }

    private fun decodeParam(value: String): String {
        return try {
            URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
        } catch (_: Exception) {
            value
        }
    }

    private fun findAdultKeywordIn(value: String): String? {
        val normalized = value.lowercase(Locale.US).trim()
        if (normalized.isEmpty()) return null
        for (keyword in ADULT_KEYWORDS) {
            if (containsKeyword(normalized, keyword)) return keyword
        }
        return null
    }

    private fun containsKeyword(text: String, keyword: String): Boolean {
        val idx = text.indexOf(keyword)
        if (idx < 0) return false
        val asciiWord = keyword.all { it in 'a'..'z' || it in '0'..'9' }
        if (!asciiWord) return true

        val startOk = idx == 0 || !text[idx - 1].isLetterOrDigit()
        val end = idx + keyword.length
        val endOk = end >= text.length || !text[end].isLetterOrDigit()
        return startOk && endOk
    }
}
