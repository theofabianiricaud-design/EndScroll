/*
 * Copyright 2025 Atick Faisal
 * Modifie en 2026 : ecran en francais et verrouillage par code.
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

package dev.atick.shorts.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.atick.shorts.ui.components.TrackedPackagesSection
import dev.atick.shorts.ui.viewmodels.MainViewModel
import dev.atick.shorts.ui.viewmodels.ServiceState
import dev.atick.shorts.utils.LockManager

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.serviceState.collectAsState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    state.generatedCode?.let { code ->
        GeneratedCodeDialog(code = code, onDismiss = viewModel::dismissGeneratedCode)
    }

    if (state.showDisclosure) {
        ProminentDisclosureScreen(
            onAgree = { viewModel.acceptDisclosure(context) },
            onCancel = { viewModel.hideDisclosure() },
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            StatusHeader(state)

            if (state.isGranted) {
                TrackedPackagesSection(
                    packages = state.trackedPackages,
                    onPackageToggle = viewModel::togglePackageTracking,
                )
            } else {
                Button(
                    onClick = viewModel::showDisclosure,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Activer dans les Paramètres", fontWeight = FontWeight.SemiBold)
                }
            }

            LockSection(state = state, viewModel = viewModel)

            if (state.isGranted && !state.lock.isLocked(state.now)) {
                OutlinedButton(
                    onClick = viewModel::showDisclosure,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Gérer la permission d'accessibilité")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusHeader(state: ServiceState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = if (state.isGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Settings,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = if (state.isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (state.isGranted) "EndScroll est actif" else "EndScroll est éteint",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (state.isGranted) {
                "Reels et Shorts sont bloqués. Messages, appels, photos et stories restent libres."
            } else {
                "Active-le dans Paramètres > Accessibilité > Applis installées > EndScroll."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LockSection(state: ServiceState, viewModel: MainViewModel) {
    val lock = state.lock
    val now = state.now
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Verrou",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            when {
                !lock.hasCode -> NoCodeContent(viewModel)
                lock.isLocked(now) -> LockedContent(state, viewModel)
                else -> OpenContent(state, viewModel)
            }
        }
    }
}

@Composable
private fun NoCodeContent(viewModel: MainViewModel) {
    Text(
        "Sans code, n'importe qui (toi compris) peut couper EndScroll. " +
            "Avec un code, il faut le code puis ${LockManager.WAIT_MS / 60_000} minutes d'attente " +
            "pour couper un blocage, désactiver l'accessibilité ou désinstaller.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Button(onClick = viewModel::generateCode, modifier = Modifier.fillMaxWidth()) {
        Text("Générer un code de 20 caractères")
    }
    Text(
        "Le plus solide : donne ce code à quelqu'un de confiance et ne le garde pas sur toi.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ChooseCodeFields(viewModel)
}

@Composable
private fun ChooseCodeFields(viewModel: MainViewModel) {
    var code by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    Text("Ou choisis ton propre code (${LockManager.MIN_CODE_LENGTH} caractères minimum) :")
    SecretField(value = code, onValueChange = { code = it }, label = "Code")
    SecretField(value = confirmation, onValueChange = { confirmation = it }, label = "Confirmer le code")
    OutlinedButton(
        onClick = {
            viewModel.setCode(code.toCharArray(), confirmation.toCharArray())
            code = ""
            confirmation = ""
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Enregistrer ce code")
    }
}

@Composable
private fun LockedContent(state: ServiceState, viewModel: MainViewModel) {
    val lock = state.lock
    val now = state.now
    LabelRow(Icons.Rounded.Lock, "Verrouillé")

    if (now < lock.guardOpenUntil) {
        Text(
            "Paramètres et désinstallation libres encore ${formatDuration(lock.guardOpenUntil - now)}.",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }

    if (lock.isWaitingForUnlock(now)) {
        Text(
            "Blocage des Reels modifiable dans ${formatDuration(lock.unlockStartsAt - now)}.",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedButton(onClick = viewModel::relock, modifier = Modifier.fillMaxWidth()) {
            Text("Annuler, je n'en ai plus besoin")
        }
        return
    }

    if (now < lock.blockedUntil) {
        Text(
            "Trop d'erreurs. Nouvel essai dans ${formatDuration(lock.blockedUntil - now)}.",
            color = MaterialTheme.colorScheme.error,
        )
        return
    }

    var code by remember { mutableStateOf("") }
    Text(
        "Pour couper un blocage, il faut le code, puis attendre " +
            "${LockManager.WAIT_MS / 60_000} minutes.",
        style = MaterialTheme.typography.bodyMedium,
    )
    SecretField(value = code, onValueChange = { code = it }, label = "Code")
    Button(
        onClick = {
            viewModel.tryUnlock(code.toCharArray())
            code = ""
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Demander le déverrouillage")
    }
    if (lock.failedAttempts > 0) {
        Text(
            "Erreurs : ${lock.failedAttempts}. Après 3, l'attente double à chaque erreur (jusqu'à 24 h).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun OpenContent(state: ServiceState, viewModel: MainViewModel) {
    Text(
        "Déverrouillé encore ${formatDuration(state.lock.unlockEndsAt - state.now)}. " +
            "Tu peux couper un blocage, changer ou supprimer le code.",
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Button(onClick = viewModel::relock, modifier = Modifier.fillMaxWidth()) {
        Text("Reverrouiller maintenant")
    }
    OutlinedButton(onClick = viewModel::generateCode, modifier = Modifier.fillMaxWidth()) {
        Text("Remplacer par un nouveau code généré")
    }
    ChooseCodeFields(viewModel)
    TextButton(onClick = viewModel::removeCode, modifier = Modifier.fillMaxWidth()) {
        Text("Supprimer le code", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun LabelRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecretField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun GeneratedCodeDialog(code: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Ton code de verrouillage") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectionContainer {
                    Text(
                        code,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "Il ne sera plus jamais affiché. Recopie-le et donne-le à quelqu'un de confiance. " +
                        "Sans lui, la seule sortie est le mode sans échec d'Android.",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("C'est noté, verrouiller") }
        },
    )
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) + 999) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "${h} h ${m} min"
        m > 0 -> "${m} min ${s.toString().padStart(2, '0')} s"
        else -> "${s} s"
    }
}
