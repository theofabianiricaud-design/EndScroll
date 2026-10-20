# EndScroll

Application Android qui bloque les **Reels Instagram** (et en option les Shorts YouTube et TikTok)
sans toucher au reste : messages, appels, envoi de photos et stories restent libres.
Un verrou par code empêche de la couper sur un coup de tête.

*Android app that blocks Instagram Reels (optionally YouTube Shorts and TikTok) while keeping
messages, calls, photos and stories usable, with a code lock against impulsive disabling.*

> EndScroll est une version modifiée de [Shorts Blocker](https://github.com/atick-faisal/Shorts-Blocker)
> d'Atick Faisal, publiée sous licence Apache 2.0. Ce n'est pas le projet d'origine et son auteur
> n'y est pas associé.

## Ce que fait EndScroll

- **Instagram** : l'onglet Reels et le lecteur de Reels plein écran sont refermés, y compris un Reel
  ouvert depuis une conversation. Les Reels préchargés en arrière-plan sont ignorés, donc les stories,
  les conversations et la liste des conversations ne sont jamais bloquées.
- **YouTube** (option) : tout Short ouvert est refermé, les vidéos normales restent libres.
- **TikTok** (option) : l'appli entière est refermée.
- **Aucun accès Internet** : la permission réseau est retirée, rien ne sort du téléphone.

## Le verrou

- Code généré de 20 caractères, ou code choisi de 12 caractères minimum,
  haché en PBKDF2-HMAC-SHA256 (210 000 itérations, sel aléatoire), jamais stocké en clair.
- Verrou fermé : les pages des Paramètres et de l'installateur qui affichent « EndScroll » se referment.
- Bon code : Paramètres et désinstallation libres tout de suite pendant 15 minutes ; couper le blocage
  des Reels demande d'attendre 15 minutes, puis c'est ouvert 5 minutes.
- Code faux : 3 essais libres, puis 1 min, 2, 4, 8... jusqu'à 24 h d'attente.
- **Arrêt d'urgence** : un second code, défini verrou ouvert, coupe tous les blocages pendant 15 minutes.
- Sortie de secours si les codes sont perdus : le mode sans échec d'Android désactive EndScroll.

## Installation

1. Télécharger l'APK dans les [Releases](../../releases), ou compiler (voir plus bas).
2. L'installer, puis : Paramètres > Accessibilité > Applis installées > EndScroll > Activer.
   Sur Samsung, si Android parle de « paramètre restreint » : Paramètres > Applications > EndScroll >
   menu ⋮ > « Autoriser les paramètres restreints ».

## Compiler

JDK 21 et le SDK Android sont nécessaires.

```
./gradlew assembleDebug
```

L'APK sort dans `app/build/outputs/apk/debug/`.

## Modifications par rapport à Shorts Blocker

- Détection Instagram réécrite : seuls l'onglet Reels sélectionné et un lecteur `clips_*` visible et
  plein écran déclenchent le retour ; les conversations ne sont jamais bloquées.
- Ajout de TikTok, du verrou par code, de la protection des Paramètres et de l'arrêt d'urgence.
- Retrait de Firebase (Analytics, Crashlytics), des permissions réseau et de la sauvegarde cloud.
- Interface traduite en français, nouveau nom, nouvel identifiant (`dev.theo.endscroll`) et nouveau logo.
- Retrait de la configuration de déploiement et de financement propre au projet d'origine.

Chaque fichier modifié porte la mention « Modifié en 2026 » sous l'en-tête de copyright d'origine.

## Limites

- Instagram, YouTube et TikTok changent régulièrement leurs identifiants internes :
  une mise à jour de ces applis peut casser la détection.
- Ne fonctionne que sur Android. Une appli qui détourne l'accessibilité de cette façon
  ne serait pas acceptée telle quelle sur le Play Store.

## Marques

Instagram, YouTube et TikTok sont des marques de leurs propriétaires respectifs.
EndScroll n'est ni affilié à Meta, Google ou ByteDance, ni approuvé par eux.

## Licence

Distribué sous licence Apache 2.0 : voir [LICENSE](LICENSE) et [NOTICE](NOTICE).

```
Copyright 2025 Atick Faisal
Copyright 2026 Théo Fabiani Ricaud (modifications)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
