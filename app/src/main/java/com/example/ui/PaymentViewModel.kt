package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.PaymentRepository
import com.example.data.Profile
import com.example.data.SendPaymentResult
import com.example.data.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab {
    DASHBOARD,
    SEND,
    RECEIVE,
    PASSBOOK
}

class PaymentViewModel(private val repository: PaymentRepository) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.initializeDbIfNecessary()
        }
    }

    // Tab state
    private val _currentTab = MutableStateFlow(AppTab.DASHBOARD)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    fun switchTab(tab: AppTab) {
        _currentTab.value = tab
    }

    // Database reactive UI bounds
    val profileState: StateFlow<Profile?> = repository.profileFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val transactionsState: StateFlow<List<Transaction>> = repository.transactionsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Form inputs for Sending Payment
    val sendUpiInput = MutableStateFlow("")
    val sendNameInput = MutableStateFlow("")
    val sendAmountInput = MutableStateFlow("")
    val sendNoteInput = MutableStateFlow("")

    private val _sendProgressState = MutableStateFlow<SendProgress>(SendProgress.Idle)
    val sendProgressState: StateFlow<SendProgress> = _sendProgressState.asStateFlow()

    // Form inputs for Receiving Payment
    val receiveAmountInput = MutableStateFlow("")
    val receiveSenderNameInput = MutableStateFlow("Merchant Client")
    val receiveSenderUpiInput = MutableStateFlow("payer@upi")

    private val _receiveSuccessMessage = MutableStateFlow<String?>(null)
    val receiveSuccessMessage: StateFlow<String?> = _receiveSuccessMessage.asStateFlow()

    // Profile settings edit state
    val editNameInput = MutableStateFlow("")
    val editUpiIdInput = MutableStateFlow("")
    private val _isEditingProfile = MutableStateFlow(false)
    val isEditingProfile: StateFlow<Boolean> = _isEditingProfile.asStateFlow()

    // Send payment function
    fun executeSendPayment() {
        val upi = sendUpiInput.value.trim()
        val name = sendNameInput.value.trim()
        val amountStr = sendAmountInput.value.trim()
        val note = sendNoteInput.value.trim()

        if (upi.isEmpty()) {
            _sendProgressState.value = SendProgress.Failed("Please enter a recipient UPI ID or Phone Number.")
            return
        }

        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            _sendProgressState.value = SendProgress.Failed("Please enter a valid amount greater than 0.")
            return
        }

        _sendProgressState.value = SendProgress.Processing

        viewModelScope.launch {
            val result = repository.sendPayment(upi, name, amount, note)
            when (result) {
                is SendPaymentResult.Success -> {
                    _sendProgressState.value = SendProgress.Success(
                        cashbackEarned = result.cashbackEarned,
                        transactionId = result.transaction.id,
                        amountPaid = amount,
                        receiverName = result.transaction.name
                    )
                    // Clear inputs on success
                    sendUpiInput.value = ""
                    sendNameInput.value = ""
                    sendAmountInput.value = ""
                    sendNoteInput.value = ""
                }
                is SendPaymentResult.Error -> {
                    _sendProgressState.value = SendProgress.Failed(result.message)
                }
            }
        }
    }

    // Populate Send Details directly (e.g. from rapid action or quick pay)
    fun prepopulateSend(upiId: String, name: String, amount: String = "") {
        sendUpiInput.value = upiId
        sendNameInput.value = name
        sendAmountInput.value = amount
        _sendProgressState.value = SendProgress.Idle
        _currentTab.value = AppTab.SEND
    }

    fun dismissSendProgress() {
        _sendProgressState.value = SendProgress.Idle
    }

    // Receive simulated payment
    fun executeSimulateReceive() {
        val amountStr = receiveAmountInput.value.trim()
        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            _receiveSuccessMessage.value = "Error: Invalid amount. Please enter a positive amount."
            return
        }

        val name = receiveSenderNameInput.value.trim().ifBlank { "External Sender" }
        val upi = receiveSenderUpiInput.value.trim().ifBlank { "payer@upi" }

        viewModelScope.launch {
            val success = repository.receivePayment(name, upi, amount, "Simulated Credit")
            if (success) {
                _receiveSuccessMessage.value = "Successfully received ₹$amount from $name!"
                receiveAmountInput.value = ""
            } else {
                _receiveSuccessMessage.value = "Error: Failed to credit simulated payment."
            }
        }
    }

    fun dismissReceiveMessage() {
        _receiveSuccessMessage.value = null
    }

    // Profile updates
    fun startEditingProfile(currentName: String, currentUpi: String) {
        editNameInput.value = currentName
        editUpiIdInput.value = currentUpi.removeSuffix("@upi")
        _isEditingProfile.value = true
    }

    fun saveProfile() {
        viewModelScope.launch {
            val success = repository.updateProfileInfo(editNameInput.value, editUpiIdInput.value)
            if (success) {
                _isEditingProfile.value = false
            }
        }
    }

    fun cancelEditingProfile() {
        _isEditingProfile.value = false
    }

    fun resetApp() {
        viewModelScope.launch {
            repository.resetWallet()
            _sendProgressState.value = SendProgress.Idle
            _currentTab.value = AppTab.DASHBOARD
        }
    }
}

sealed class SendProgress {
    object Idle : SendProgress()
    object Processing : SendProgress()
    data class Success(
        val cashbackEarned: Double,
        val transactionId: Int,
        val amountPaid: Double,
        val receiverName: String
    ) : SendProgress()
    data class Failed(val message: String) : SendProgress()
}

class ViewModelFactory(private val repository: PaymentRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PaymentViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PaymentViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
