/*
 * Copyright 2025 Atick Faisal
 * Modifie en 2026 : TikTok, et protection des reglages par code.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.atick.shorts.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import dev.atick.shorts.MainActivity
import dev.atick.shorts.R
import dev.atick.shorts.services.detectors.InstagramReelsDetector
import dev.atick.shorts.services.detectors.ShortFormContentDetector
import dev.atick.shorts.services.detectors.TikTokDetector
import dev.atick.shorts.services.detectors.YouTubeShortsDetector
import dev.atick.shorts.utils.LockManager
import dev.atick.shorts.utils.LockState
import dev.atick.shorts.utils.PackageConstants
import dev.atick.shorts.utils.UserPreferencesProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Service d'accessibilite :
 *  - renvoie en arriere des qu'un Reel ou un Short s'affiche (TikTok : retour a l'accueil) ;
 *  - verrou ferme : referme toute page des Parametres ou de l'installateur qui affiche
 *    le nom du bloqueur, et ouvre EndScroll pour entrer le code. Le bon code libere
 *    ces pages tout de suite pour 15 minutes ;
 *  - arret d'urgence (code a part) : plus aucun blocage, nulle part, pendant 15 minutes.
 *
 * Rien n'est enregistre ni envoye : l'ecran est lu en memoire puis oublie.
 */
@SuppressLint("AccessibilityPolicy")
class ShortFormContentBlockerService : AccessibilityService() {

    private val lastActionTimestamps = ConcurrentHashMap<String, Long>()
    private val actionCooldownMillis = 1500L

    private val userPreferencesProvider by lazy { UserPreferencesProvider(applicationContext) }
    private val lockManager by lazy { LockManager(applicationContext) }
    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + job)

    @Volatile
    private var lockState = LockState()

    @Volatile
    private var trackedPackages: Set<String> = emptySet()

    private val appLabel by lazy { getString(R.string.app_name) }

    private val detectors: Map<String, ShortFormContentDetector> by lazy {
        buildMap {
            put(PackageConstants.YOUTUBE_PACKAGE, YouTubeShortsDetector())
            put(PackageConstants.INSTAGRAM_PACKAGE, InstagramReelsDetector())
            PackageConstants.TIKTOK_ALIASES.forEach { put(it, TikTokDetector(it)) }
        }
    }

    override fun onServiceConnected() {
        Timber.d("ShortFormContentBlockerService connected")

        combine(userPreferencesProvider.getTrackedPackages(), lockManager.state) { packages, lock ->
            packages to lock
        }
            .onEach { (packages, lock) ->
                val expanded = packages.flatMap { pkg ->
                    if (pkg == PackageConstants.TIKTOK_PACKAGE) PackageConstants.TIKTOK_ALIASES else listOf(pkg)
                }.toSet()
                trackedPackages = expanded
                lockState = lock

                // On n'ecoute les Parametres que si un code protege le bloqueur
                val listened = if (lock.hasCode) expanded + PackageConstants.GUARDED_PACKAGES else expanded
                Timber.d("Packages ecoutes : ${listened.joinToString()}")
                serviceInfo = AccessibilityServiceInfo().apply {
                    eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                    flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    packageNames = listened.toTypedArray()
                    notificationTimeout = 100
                }
            }
            .catch { error -> Timber.e(error, "Lecture des reglages impossible") }
            .launchIn(coroutineScope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val relevant = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            (
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                    event.contentChangeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE != 0
                )
        if (!relevant) return
        val packageName = event.packageName?.toString() ?: return
        // Arret d'urgence : on ne touche a rien
        if (lockState.isEmergency()) return

        if (packageName in PackageConstants.GUARDED_PACKAGES) {
            guardSettings(packageName)
            return
        }

        if (packageName !in trackedPackages) return
        val detector = detectors[packageName] ?: return

        for (win in windows) {
            if (!win.isFocused || !win.isActive) continue
            val root = win.root ?: continue
            if (detector.isShortFormContent(event, root, resources)) {
                Timber.i("[$packageName] Contenu court detecte")
                if (shouldPerformAction("${packageName}_content_detected")) {
                    val action = if (detector is TikTokDetector) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK
                    performGlobalAction(action)
                }
                break
            }
        }
    }

    /** Referme la page si elle montre le bloqueur, sauf pendant les 15 minutes ouvertes par le code. */
    private fun guardSettings(packageName: String) {
        if (!lockState.isGuardLocked()) return
        val root = rootInActiveWindow ?: return
        val mentionsUs = root.findAccessibilityNodeInfosByText(appLabel).isNotEmpty()
        if (!mentionsUs) return
        Timber.i("[$packageName] Page de reglage du bloqueur refermee (verrou ferme)")
        if (shouldPerformAction("guard_$packageName")) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            Toast.makeText(this, R.string.guard_message, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    override fun onInterrupt() {
        Timber.w("ShortFormContentBlockerService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun shouldPerformAction(key: String): Boolean {
        val now = SystemClock.uptimeMillis()
        val last = lastActionTimestamps[key] ?: 0L
        if (now - last < actionCooldownMillis) return false
        lastActionTimestamps[key] = now
        return true
    }
}
