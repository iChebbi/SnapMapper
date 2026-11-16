package com.amar.snapmapper

import android.app.Activity
import android.util.Log

/**
 * Opens Circle to Search with Android's own call, ContextualSearchManager.startContextualSearch(Activity,
 * ContextualSearchConfig), which only answers an app that has an activity in front. So this see-through
 * activity comes to the front, asks, and closes: about 0.3 s, against 1 s or more for holding the
 * navigation handle. If the phone lacks or refuses the call (seen working: Find X9, Android 17), the
 * handle is held instead.
 */
class CircleToSearchActivity : Activity() {
    override fun onResume() {
        super.onResume()
        val opened = try {
            // android.app.contextualsearch.ContextualSearchManager; not in the public SDK, hence by name.
            val manager = getSystemService("contextual_search")!!
            val configClass = Class.forName("android.app.contextualsearch.ContextualSearchConfig")
            val builderClass = Class.forName("android.app.contextualsearch.ContextualSearchConfig\$Builder")
            val config = builderClass.getMethod("build").invoke(builderClass.getConstructor().newInstance())
            manager.javaClass.getMethod("startContextualSearch", Activity::class.java, configClass).invoke(manager, this, config)
            true
        } catch (e: Exception) {
            Log.w(TAG, "direct call failed", e)
            false
        }
        finish()
        overridePendingTransition(0, 0)
        if (!opened) SnapKeyService.instance?.holdNavHandle()
    }

    companion object {
        private const val TAG = "SnapKeyCircle"
    }
}
