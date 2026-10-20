package dev.atick.shorts.services.detectors

import android.content.res.Resources
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * TikTok n'est qu'un flux de videos courtes : quand il est coche, toute l'appli est fermee.
 */
class TikTokDetector(private val packageName: String) : ShortFormContentDetector {

    override fun getPackageName(): String = packageName

    override fun isShortFormContent(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo,
        resources: Resources,
    ): Boolean = true
}
