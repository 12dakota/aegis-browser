package com.aegis.browser.downloads

import java.util.concurrent.CopyOnWriteArrayList

data class DownloadItem(
    val id: Long,
    val name: String,
    val host: String,
    var state: String,
    var progress: Int,
    var message: String,
    var uri: String? = null
)

/** In-memory list of current + recent downloads for the in-app Downloads menu. */
object DownloadStore {
    private val items = LinkedHashMap<Long, DownloadItem>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Synchronized
    fun upsert(id: Long, name: String, host: String, state: String, progress: Int, message: String, uri: String?) {
        val existing = items[id]
        if (existing == null) {
            items[id] = DownloadItem(id, name, host, state, progress, message, uri)
        } else {
            existing.state = state
            existing.progress = progress
            existing.message = message
            if (!uri.isNullOrBlank()) existing.uri = uri
        }
        listeners.forEach { runCatching { it.invoke() } }
    }

    @Synchronized
    fun snapshot(): List<DownloadItem> = items.values.reversed()

    @Synchronized
    fun clearFinished() {
        val it = items.entries.iterator()
        while (it.hasNext()) {
            val st = it.next().value.state
            if (st == DownloadCoordinator.STATE_FINISHED ||
                st == DownloadCoordinator.STATE_FAILED ||
                st == DownloadCoordinator.STATE_BLOCKED
            ) it.remove()
        }
        listeners.forEach { runCatching { it.invoke() } }
    }

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
}
