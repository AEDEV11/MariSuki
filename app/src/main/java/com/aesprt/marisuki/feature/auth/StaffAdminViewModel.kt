package com.aesprt.marisuki.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aesprt.marisuki.data.auth.AuthException
import com.aesprt.marisuki.data.auth.AuthRepository
import com.aesprt.marisuki.data.local.entity.AuditLogEntity
import com.aesprt.marisuki.domain.auth.AccountSummary
import com.aesprt.marisuki.domain.auth.DEFAULT_CASHIER_PERMISSIONS
import com.aesprt.marisuki.domain.auth.PosPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class StaffAdminState(
    val accounts: List<AccountSummary> = emptyList(),
    val selectedId: String? = null,
    val permissions: Set<PosPermission> = DEFAULT_CASHIER_PERMISSIONS,
    val activity: List<AuditLogEntity> = emptyList(),
    val restrictedActions: List<com.aesprt.marisuki.data.local.entity.RestrictedActionAuditEntity> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

class StaffAdminViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(StaffAdminState())
    val state: StateFlow<StaffAdminState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = launch { loadAccounts() }

    private suspend fun loadAccounts() {
        val accounts = auth.staffAccounts()
        val approvals = auth.restrictedActionAudit().first()
        val selected = _state.value.selectedId?.let { id -> accounts.firstOrNull { it.id == id } }
        val activity = selected?.let { auth.staffActivity(it.id).first() }.orEmpty()
        _state.value = _state.value.copy(accounts = accounts, restrictedActions = approvals, activity = activity, error = null)
    }

    fun addCashier() {
        _state.value = _state.value.copy(selectedId = null, permissions = DEFAULT_CASHIER_PERMISSIONS, activity = emptyList(), error = null, saved = false)
    }

    fun select(accountId: String) = launch {
        val account = _state.value.accounts.firstOrNull { it.id == accountId } ?: return@launch
        if (account.role.name != "CASHIER") return@launch
        val permissions = auth.permissionsFor(accountId)
        val activity = auth.staffActivity(accountId).first()
        _state.value = _state.value.copy(selectedId = accountId, permissions = permissions, activity = activity, error = null, saved = false)
    }

    fun save(displayName: String, username: String, pin: String, confirmPin: String, permissions: Set<PosPermission>) = launch {
        val saved = auth.saveCashier(_state.value.selectedId, displayName, username, pin, confirmPin, permissions)
        _state.value = _state.value.copy(selectedId = saved.id, permissions = permissions, saved = true)
        loadAccounts()
    }

    fun setEnabled(accountId: String, enabled: Boolean) = launch {
        auth.setCashierEnabled(accountId, enabled)
        loadAccounts()
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, saved = false)
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

    private fun Throwable.userMessage() = when (this) {
        is AuthException -> when (val reason = failure) {
            com.aesprt.marisuki.domain.auth.AuthFailure.InvalidCredentials -> "Sign in again as the owner to manage staff."
            com.aesprt.marisuki.domain.auth.AuthFailure.AccountDisabled -> "This account is disabled."
            com.aesprt.marisuki.domain.auth.AuthFailure.RateLimited -> "Too many attempts. Wait before trying again."
            com.aesprt.marisuki.domain.auth.AuthFailure.OwnerPinRejected -> "The owner PIN is incorrect."
            com.aesprt.marisuki.domain.auth.AuthFailure.DuplicateUsername -> "That username is already in use."
            is com.aesprt.marisuki.domain.auth.AuthFailure.Validation -> reason.message
            is com.aesprt.marisuki.domain.auth.AuthFailure.General -> reason.message
        }
        else -> message?.takeIf(String::isNotBlank) ?: "Could not save staff changes."
    }
}
