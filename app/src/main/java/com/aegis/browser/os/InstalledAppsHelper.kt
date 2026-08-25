package com.aegis.browser.os

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class LaunchableApp(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable?
)

object InstalledAppsHelper {

    fun listLaunchable(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolve = try {
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        } catch (_: Exception) {
            pm.queryIntentActivities(intent, 0)
        }
        val self = context.packageName
        return resolve
            .mapNotNull { ri ->
                val ai = ri.activityInfo ?: return@mapNotNull null
                if (ai.packageName == self) return@mapNotNull null
                val label = try {
                    ri.loadLabel(pm)?.toString()?.trim().orEmpty()
                } catch (_: Exception) {
                    ai.packageName
                }
                if (label.isBlank()) return@mapNotNull null
                val icon = try {
                    ri.loadIcon(pm)
                } catch (_: Exception) {
                    null
                }
                LaunchableApp(
                    label = label,
                    packageName = ai.packageName,
                    activityName = ai.name,
                    icon = icon
                )
            }
            .distinctBy { it.packageName + "/" + it.activityName }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(context: Context, app: LaunchableApp): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(app.packageName, app.activityName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    true
                } else false
            } catch (_: Exception) {
                false
            }
        }
    }
}
