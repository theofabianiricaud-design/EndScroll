/*
 * Copyright 2025 Atick Faisal
 * Modifie en 2026 : verrouillage par code.
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

package dev.atick.shorts.ui.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.atick.shorts.models.TrackedPackage
import dev.atick.shorts.services.ShortFormContentBlockerService
import dev.atick.shorts.utils.AccessibilityServiceManager
import dev.atick.shorts.utils.LockManager
import dev.atick.shorts.utils.LockState
import dev.atick.shorts.utils.UnlockResult
import dev.atick.shorts.utils.UserPreferencesProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

data class ServiceState(
    val isGranted: Boolean = false,
    val isChecking: Boolean = false,
    val trackedPackages: List<TrackedPackage> = emptyList(),
    val showDisclosure: Boolean = false,
    val lock: LockState = LockState(),
    /** Heure courante, rafraichie chaque seconde pour les comptes a rebours. */
    val now: Long = System.currentTimeMillis(),
    /** Message a afficher une fois (erreur de code, etc.). */
    val message: String? = null,
    /** Code genere a montrer une seule fois. */
    val generatedCode: String? = null,
)

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val serviceName: String =
        "${application.packageName}/${ShortFormContentBlockerService::class.java.name}"

    private val userPreferencesProvider = UserPreferencesProvider(application)
    private val lockManager = LockManager(application)

    private val _serviceState = MutableStateFlow(ServiceState())
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    private var isMonitoring = false

    init {
        viewModelScope.launch {
            userPreferencesProvider.getTrackedPackagesWithStatus().collect { packages ->
                _serviceState.update { it.copy(trackedPackages = packages) }
            }
        }
        viewModelScope.launch {
            lockManager.state.collect { lock -> _serviceState.update { it.copy(lock = lock) } }
        }
        viewModelScope.launch {
            while (isActive) {
                _serviceState.update { it.copy(now = System.currentTimeMillis()) }
                delay(1000L)
            }
        }
    }

    private val isLocked: Boolean
        get() = _serviceState.value.lock.isLocked()

    fun checkPermission(context: Context) {
        viewModelScope.launch {
            _serviceState.update { it.copy(isChecking = true) }
            val isGranted = AccessibilityServiceManager.isAccessibilityServiceEnabled(context, serviceName)
            _serviceState.update { it.copy(isGranted = isGranted, isChecking = false) }
        }
    }

    fun showDisclosure() {
        // Tant que le service n'est pas actif, il n'y a rien a proteger : on laisse activer.
        if (isLocked && _serviceState.value.isGranted) {
            say("Verrouillé : déverrouille avec ton code d'abord.")
            return
        }
        _serviceState.update { it.copy(showDisclosure = true) }
    }

    fun hideDisclosure() {
        _serviceState.update { it.copy(showDisclosure = false) }
    }

    fun acceptDisclosure(context: Context) {
        viewModelScope.launch { userPreferencesProvider.setDisclosureAccepted(true) }
        hideDisclosure()
        openAccessibilitySettings(context)
    }

    fun openAccessibilitySettings(context: Context) {
        AccessibilityServiceManager.openAccessibilitySettings(context)
        startMonitoring(context)
    }

    private fun startMonitoring(context: Context) {
        if (isMonitoring) return
        isMonitoring = true
        viewModelScope.launch {
            while (isActive && isMonitoring) {
                delay(1000L)
                checkPermission(context)
                if (_serviceState.value.isGranted) {
                    isMonitoring = false
                    break
                }
            }
        }
    }

    fun onResume(context: Context) {
        checkPermission(context)
    }

    /** Activer un blocage est toujours permis ; le couper demande le verrou ouvert. */
    fun togglePackageTracking(packageName: String, enabled: Boolean) {
        if (!enabled && isLocked) {
            say("Verrouillé : impossible de couper un blocage sans le code.")
            return
        }
        viewModelScope.launch { userPreferencesProvider.togglePackage(packageName, enabled) }
    }

    // ---- Verrou ----

    fun setCode(code: CharArray, confirmation: CharArray) {
        try {
            when {
                isLocked -> say("Verrouillé : déverrouille d'abord pour changer le code.")
                code.size < LockManager.MIN_CODE_LENGTH ->
                    say("Le code doit faire au moins ${LockManager.MIN_CODE_LENGTH} caractères.")
                !code.contentEquals(confirmation) -> say("Les deux codes ne sont pas identiques.")
                else -> {
                    val copy = code.copyOf()
                    viewModelScope.launch {
                        lockManager.setCode(copy)
                        _serviceState.update { it.copy(generatedCode = null) }
                        say("Code enregistré. EndScroll est verrouillé.")
                    }
                }
            }
        } finally {
            code.fill('\u0000')
            confirmation.fill('\u0000')
        }
    }

    /** Genere un code aleatoire et l'enregistre directement. Il n'est montre qu'une fois. */
    fun generateCode() {
        if (isLocked) {
            say("Verrouillé : déverrouille d'abord pour changer le code.")
            return
        }
        val code = LockManager.randomCode()
        val shown = String(code)
        viewModelScope.launch {
            lockManager.setCode(code)
            _serviceState.update { it.copy(generatedCode = shown) }
        }
    }

    fun dismissGeneratedCode() {
        _serviceState.update { it.copy(generatedCode = null) }
    }

    fun tryUnlock(code: CharArray) {
        val copy = code.copyOf()
        code.fill('\u0000')
        viewModelScope.launch {
            when (val result = lockManager.tryUnlock(copy)) {
                is UnlockResult.Accepted -> say(
                    "Code bon. Ouverture dans ${LockManager.WAIT_MS / 60_000} minutes, " +
                        "pour ${LockManager.OPEN_MS / 60_000} minutes.",
                )
                is UnlockResult.Rejected -> say("Code faux.")
                is UnlockResult.TooSoon -> say("Trop d'erreurs : attends la fin du blocage.")
            }
        }
    }

    fun relock() {
        viewModelScope.launch { lockManager.relock() }
    }

    fun removeCode() {
        if (isLocked) return
        viewModelScope.launch {
            lockManager.removeCode()
            say("Code supprimé. EndScroll n'est plus protégé.")
        }
    }

    fun consumeMessage() {
        _serviceState.update { it.copy(message = null) }
    }

    private fun say(text: String) {
        Timber.d(text)
        _serviceState.update { it.copy(message = text) }
    }

    override fun onCleared() {
        super.onCleared()
        isMonitoring = false
    }
}
