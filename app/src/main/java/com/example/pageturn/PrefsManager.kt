package net.kannagi.pageturn

import android.content.Context
import android.view.KeyEvent

class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLeftKey(): Int = prefs.getInt(KEY_LEFT, KeyEvent.KEYCODE_UNKNOWN)
    fun setLeftKey(keyCode: Int) { prefs.edit().putInt(KEY_LEFT, keyCode).apply() }

    fun getRightKey(): Int = prefs.getInt(KEY_RIGHT, KeyEvent.KEYCODE_UNKNOWN)
    fun setRightKey(keyCode: Int) { prefs.edit().putInt(KEY_RIGHT, keyCode).apply() }

    fun getTargetApps(): Set<String> = prefs.getStringSet(KEY_APPS, emptySet())?.toSet() ?: emptySet()
    fun setTargetApps(apps: Set<String>) { prefs.edit().putStringSet(KEY_APPS, apps).apply() }

    companion object {
        private const val PREFS_NAME = "pageturn"
        private const val KEY_LEFT = "left_key"
        private const val KEY_RIGHT = "right_key"
        private const val KEY_APPS = "target_apps"
    }
}
