package com.aegis.browser.extensions

/**
 * Last tap-and-hold target so an add-on popup can act on the link/image/page
 * (Save to Drive, pin, download, etc.).
 */
object ExtensionContext {
    @Volatile var linkUrl: String? = null
    @Volatile var imageUrl: String? = null
    @Volatile var text: String? = null
    @Volatile var pageUrl: String? = null

    fun remember(page: String?, link: String?, image: String?, selected: String?) {
        pageUrl = page
        linkUrl = link
        imageUrl = image
        text = selected
    }

    fun primaryUrl(): String =
        linkUrl?.takeIf { it.startsWith("http") }
            ?: imageUrl?.takeIf { it.startsWith("http") }
            ?: pageUrl.orEmpty()
}
