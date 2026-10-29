package com.example.photomission

import android.content.Context

object MissionState {
    private const val PREFS = "photo_mission"
    private const val KEY_ACTIVE = "active"
    private const val KEY_STARTED_AT = "started_at"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ACTIVE, false)

    fun startedAt(context: Context): Long =
        prefs(context).getLong(KEY_STARTED_AT, 0L)

    fun start(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_STARTED_AT, System.currentTimeMillis())
            .apply()
    }

    fun finish(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, false)
            .putLong(KEY_STARTED_AT, 0L)
            .apply()
    }
}
