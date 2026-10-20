/*
 * Copyright 2025 Atick Faisal
 * Modifie en 2026 : detection par identifiants "clips" uniquement.
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

package dev.atick.shorts.services.detectors

import android.content.res.Resources
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import timber.log.Timber

/**
 * Detecte les Reels d'Instagram.
 *
 * Dans le code d'Instagram, les Reels s'appellent "clips" et les stories "reel".
 * On ne bloque donc que deux choses :
 *  - l'onglet Reels (clips_tab) quand il est selectionne ;
 *  - le lecteur plein ecran de Reels (identifiants clips_viewer...), d'ou qu'il soit ouvert.
 *
 * Tout le reste passe : messages, appels, envoi de photos, stories, profils.
 * Une conversation ouverte (zone de saisie de message) n'est jamais bloquee,
 * meme si un apercu de Reel y apparait.
 */
class InstagramReelsDetector : ShortFormContentDetector {

    override fun getPackageName(): String = "com.instagram.android"

    override fun isShortFormContent(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo,
        resources: Resources,
    ): Boolean {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(rootNode)
        var nodesScanned = 0
        var reelViewerSeen = false

        while (stack.isNotEmpty() && nodesScanned < MAX_NODES) {
            val n = stack.removeFirst()
            nodesScanned++
            val id = n.viewIdResourceName?.substringAfter(":id/")

            if (id != null) {
                // Dans une conversation : on laisse toujours passer
                if (THREAD_MARKERS.any { it in id }) {
                    Timber.v("[Instagram] Conversation ouverte, rien a bloquer")
                    return false
                }
                if (id == "clips_tab" && n.isSelected) {
                    Timber.i("[Instagram] Onglet Reels selectionne")
                    return true
                }
                if (REEL_VIEWER_MARKERS.any { id.startsWith(it) }) {
                    reelViewerSeen = true
                }
            }

            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { stack.add(it) }
            }
        }

        if (reelViewerSeen) {
            Timber.i("[Instagram] Lecteur de Reels detecte ($nodesScanned noeuds)")
        }
        return reelViewerSeen
    }

    private companion object {
        const val MAX_NODES = 600

        /** Identifiants du lecteur de Reels plein ecran. */
        val REEL_VIEWER_MARKERS = listOf(
            "clips_viewer",
            "clips_video_container",
            "clips_ufi",
        )

        /** Identifiants presents dans une conversation privee. */
        val THREAD_MARKERS = listOf(
            "row_thread_composer",
            "thread_composer",
            "direct_thread",
        )
    }
}
