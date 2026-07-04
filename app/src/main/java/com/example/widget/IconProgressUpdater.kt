package com.example.widget

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

object IconProgressUpdater {
    private const val TAG = "IconProgressUpdater"

    fun updateIcon(context: Context, progressPercent: Int) {
        val targetLevel = when {
            progressPercent < 13 -> 0
            progressPercent < 38 -> 25
            progressPercent < 63 -> 50
            progressPercent < 88 -> 75
            else -> 100
        }

        val activeAlias = "com.example.MainActivityAlias$targetLevel"
        val aliases = listOf(
            "com.example.MainActivityAlias0",
            "com.example.MainActivityAlias25",
            "com.example.MainActivityAlias50",
            "com.example.MainActivityAlias75",
            "com.example.MainActivityAlias100"
        )

        val pm = context.applicationContext.packageManager
        try {
            val activeComponent = ComponentName(context.applicationContext, activeAlias)
            val currentState = pm.getComponentEnabledSetting(activeComponent)
            if (currentState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return // Already enabled, avoid redundant toggles
            }

            Log.d(TAG, "Updating active icon component to: $activeAlias (Progress: $progressPercent%)")

            // Enable target first
            pm.setComponentEnabledSetting(
                activeComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            // Disable others
            for (alias in aliases) {
                if (alias != activeAlias) {
                    val comp = ComponentName(context.applicationContext, alias)
                    if (pm.getComponentEnabledSetting(comp) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                        pm.setComponentEnabledSetting(
                            comp,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dynamically adjust app icon", e)
        }
    }
}
