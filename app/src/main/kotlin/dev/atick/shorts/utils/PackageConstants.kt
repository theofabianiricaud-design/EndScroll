/*
 * Copyright 2025 Atick Faisal
 * Modifie en 2026 : TikTok, et applis de reglages a proteger.
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

package dev.atick.shorts.utils

import dev.atick.shorts.models.TrackedPackage

object PackageConstants {
    const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    const val INSTAGRAM_PACKAGE = "com.instagram.android"
    const val TIKTOK_PACKAGE = "com.zhiliaoapp.musically"

    /** Autres noms de TikTok selon le pays : surveilles avec la meme case. */
    val TIKTOK_ALIASES = listOf(TIKTOK_PACKAGE, "com.ss.android.ugc.trill")

    val AVAILABLE_PACKAGES = listOf(
        TrackedPackage(
            packageName = INSTAGRAM_PACKAGE,
            displayName = "Instagram",
            description = "Bloque les Reels. Messages, appels, photos et stories restent libres.",
            isEnabled = true,
        ),
        TrackedPackage(
            packageName = YOUTUBE_PACKAGE,
            displayName = "YouTube",
            description = "Bloque les Shorts. Les vidéos normales restent libres.",
            isEnabled = true,
        ),
        TrackedPackage(
            packageName = TIKTOK_PACKAGE,
            displayName = "TikTok",
            description = "Ferme toute l'appli (elle n'est faite que de vidéos courtes).",
            isEnabled = false,
        ),
    )

    val DEFAULT_ENABLED_PACKAGES = AVAILABLE_PACKAGES
        .filter { it.isEnabled }
        .map { it.packageName }
}
