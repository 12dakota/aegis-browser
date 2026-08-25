package com.aegis.browser.os

import android.app.Activity
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * Password gate for Device Owner / uninstall protection actions.
 * Sample credential for development — change before production.
 */
object OsAdminGate {

    /** Sample admin password (change in production). */
    const val SAMPLE_PASSWORD = "provonlong-os"

    fun challenge(activity: Activity, title: String = "Admin password required", onGranted: () -> Unit) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Admin password"
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage("This action can remove device protection. Enter the admin password to continue.")
            .setView(input)
            .setPositiveButton("Unlock") { _, _ ->
                if (input.text?.toString() == SAMPLE_PASSWORD) {
                    onGranted()
                } else {
                    Toast.makeText(activity, "Incorrect password", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
