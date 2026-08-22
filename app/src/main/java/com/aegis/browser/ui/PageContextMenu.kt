package com.aegis.browser.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.aegis.browser.R
import com.aegis.browser.downloads.DownloadCoordinator
import java.net.URLEncoder

data class PageHit(
    val linkUrl: String? = null,
    val imageUrl: String? = null,
    val text: String? = null
)

object PageContextMenu {

    fun show(activity: Activity, hit: PageHit, openInTab: (String) -> Unit, loadHere: (String) -> Unit) {
        val rows = mutableListOf<Pair<String, Int>>()
        val acts = mutableListOf<() -> Unit>()

        fun add(label: String, icon: Int, act: () -> Unit) {
            rows.add(label to icon)
            acts.add(act)
        }

        val link = hit.linkUrl?.takeIf { it.startsWith("http") }
        val img = hit.imageUrl?.takeIf { it.startsWith("http") }
        val text = hit.text?.trim()?.takeIf { it.isNotEmpty() }

        if (link != null) {
            add("Open", R.drawable.ic_nav_forward) { loadHere(link) }
            add("Open in new tab", R.drawable.ic_nav_add) { openInTab(link) }
            add("Copy link address", R.drawable.ic_nav_info) { copy(activity, link) }
            add("Share link", R.drawable.ic_nav_share) { share(activity, link) }
        }
        if (img != null) {
            add("Open image", R.drawable.ic_nav_forward) { loadHere(img) }
            add("Open image in new tab", R.drawable.ic_nav_add) { openInTab(img) }
            add("Download image", R.drawable.ic_nav_download) {
                DownloadCoordinator.start(activity, img)
            }
            add("Copy image address", R.drawable.ic_nav_info) { copy(activity, img) }
        }
        if (text != null) {
            add("Copy", R.drawable.ic_nav_info) { copy(activity, text) }
            add("Search Google for “${text.take(28)}”", R.drawable.ic_nav_search) {
                loadHere("https://www.google.com/search?q=" + URLEncoder.encode(text, "UTF-8"))
            }
            add("Share", R.drawable.ic_nav_share) { share(activity, text) }
        }

        if (rows.isEmpty()) {
            add("Copy page link", R.drawable.ic_nav_info) { /* filled by caller text */ }
            Toast.makeText(activity, "No link or text here", Toast.LENGTH_SHORT).show()
            return
        }

        val panel = ChromeUi.popupPanel(activity)
        val dlg = AlertDialog.Builder(activity).setView(panel).create()
        rows.forEachIndexed { i, (label, icon) ->
            panel.addView(ChromeUi.navRow(activity, label, null, icon) {
                dlg.dismiss()
                acts[i].invoke()
            })
        }
        dlg.show()
    }

    private fun copy(activity: Activity, value: String) {
        val cm = activity.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("Provonlong", value))
        Toast.makeText(activity, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun share(activity: Activity, value: String) {
        activity.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, value)
                },
                "Share"
            )
        )
    }
}
