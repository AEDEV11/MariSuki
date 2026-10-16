package com.aesprt.marisuki.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aesprt.marisuki.data.auth.AuthException
import com.aesprt.marisuki.data.auth.AuthRepository
import com.aesprt.marisuki.domain.auth.AccountSummary
import com.aesprt.marisuki.domain.auth.AuthDestination
import com.aesprt.marisuki.domain.auth.SetupDraft
import com.aesprt.marisuki.domain.auth.SetupInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.aesprt.marisuki.awaitVariantDemoData

data class AuthUiState(
    val destination: AuthDestination = AuthDestination.Loading,
    val theme: String = "SYSTEM",
    val defaultCashierScreen: String = "SALES",
    val storeName: String = "",
    val language: String = "fil",
    val busy: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            awaitVariantDemoData()
            loadStartup()
        }
    }

    fun refreshStartup() = launch { loadStartup() }

    private suspend fun loadStartup() {
        val destination = auth.startupDestination()
        val settings = auth.runtimeSettings()
        _state.value = _state.value.copy(
            destination = destination,
            theme = settings.theme,
            defaultCashierScreen = settings.defaultCashierScreen,
            storeName = settings.storeName,
            language = settings.language,
            error = null,
        )
    }

    fun saveDraft(draft: SetupDraft, next: Int) = launch {
        auth.saveSetupDraft(draft.copy(step = next))
        _state.value = _state.value.copy(
            destination = AuthDestination.Setup(draft.copy(step = next)),
            theme = draft.theme,
            defaultCashierScreen = draft.defaultCashierScreen,
            storeName = draft.storeName,
            language = draft.language,
        )
    }

    fun finishSetup(input: SetupInput) = launch {
        val account = auth.completeFirstTimeSetup(input)
        val settings = auth.runtimeSettings()
        _state.value = _state.value.copy(
            destination = AuthDestination.SetupComplete(account),
            theme = settings.theme,
            defaultCashierScreen = settings.defaultCashierScreen,
            storeName = settings.storeName,
            language = settings.language,
            error = null,
        )
    }

    fun enrollLegacyOwner(input: SetupInput, legacyPin: String?, deviceCredentialConfirmed: Boolean) = launch {
        val account = auth.enrollLegacyOwner(input, legacyPin, deviceCredentialConfirmed)
        val settings = auth.runtimeSettings()
        _state.value = _state.value.copy(
            destination = AuthDestination.SetupComplete(account),
            theme = settings.theme,
            defaultCashierScreen = settings.defaultCashierScreen,
            storeName = settings.storeName,
            language = settings.language,
            error = null,
        )
    }

    fun continueToPos() = launch {
        auth.enterPosAfterSetup()
        val account = auth.refreshSession()
        if (account == null) refreshStartup()
        else _state.value = _state.value.copy(destination = AuthDestination.SignedIn(account), error = null)
    }

    fun login(accountId: String, credential: String) = launch {
        val account = auth.login(accountId, credential)
        _state.value = _state.value.copy(destination = AuthDestination.SignedIn(account), error = null)
    }

    fun logout() = launch {
        auth.logout()
        _state.value = _state.value.copy(destination = auth.startupDestination(), error = null)
    }

    fun setThemePreference(theme: String) = launch {
        auth.updateThemePreference(theme)
        _state.value = _state.value.copy(theme = auth.runtimeSettings().theme)
    }

    fun onAppResumed() = launch {
        when (_state.value.destination) {
            is AuthDestination.SignedIn -> {
                val account = auth.refreshSession()
                _state.value = _state.value.copy(
                    destination = account?.let(AuthDestination::SignedIn) ?: auth.startupDestination(),
                )
            }
            else -> refreshStartup()
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _state.value = _state.value.copy(error = error.userMessage())
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    private fun Throwable.userMessage(): String = when (this) {
        is AuthException -> when (val reason = failure) {
            com.aesprt.marisuki.domain.auth.AuthFailure.InvalidCredentials -> "Hindi tama ang account o password. Subukan muli."
            com.aesprt.marisuki.domain.auth.AuthFailure.AccountDisabled -> "Naka-disable ang account na ito."
            com.aesprt.marisuki.domain.auth.AuthFailure.RateLimited -> "Masyadong maraming pagsubok. Maghintay muna bago sumubok muli."
            com.aesprt.marisuki.domain.auth.AuthFailure.OwnerPinRejected -> "Hindi tama ang Owner PIN."
            com.aesprt.marisuki.domain.auth.AuthFailure.DuplicateUsername -> "May gumagamit na ng username na ito."
            is com.aesprt.marisuki.domain.auth.AuthFailure.Validation -> reason.message
            is com.aesprt.marisuki.domain.auth.AuthFailure.General -> reason.message
        }
        else -> message?.takeIf(String::isNotBlank) ?: "Hindi natuloy ang operasyon. Subukan muli."
    }
}
