package com.example.data

import kotlinx.coroutines.flow.Flow
import java.util.Locale
import kotlin.random.Random

class PaymentRepository(
    private val transactionDao: TransactionDao,
    private val profileDao: ProfileDao
) {
    val profileFlow: Flow<Profile?> = profileDao.getProfileFlow()
    val transactionsFlow: Flow<List<Transaction>> = transactionDao.getAllTransactions()

    suspend fun getProfile(): Profile? = profileDao.getProfile()

    suspend fun initializeDbIfNecessary() {
        val existing = profileDao.getProfile()
        if (existing == null) {
            val initial = Profile(
                id = 1,
                name = "Aero Pay User",
                upiId = "aeropay@upi",
                balance = 5000.00,
                totalCashback = 0.00
            )
            profileDao.insertOrUpdateProfile(initial)
        }
    }

    suspend fun sendPayment(
        upiId: String,
        name: String,
        amount: Double,
        note: String
    ): SendPaymentResult {
        if (amount <= 0) return SendPaymentResult.Error("Amount must be greater than zero.")

        val currentProfile = profileDao.getProfile() ?: return SendPaymentResult.Error("Wallet profile not found.")

        if (currentProfile.balance < amount) {
            return SendPaymentResult.Error("Insufficient balance in your No-KYC wallet.")
        }

        // Calculate continuous cashback (e.g., between 2% and 10% randomly)
        val cashbackRate = 0.02 + (Random.nextDouble() * 0.08) // 2% to 10%
        val rawCashback = amount * cashbackRate
        val cashbackEarned = Math.round(rawCashback * 100).toDouble() / 100.0

        // Perform balance deduction and cashback additions
        val newBalance = currentProfile.balance - amount + cashbackEarned
        val newTotalCashback = currentProfile.totalCashback + cashbackEarned

        val updatedProfile = currentProfile.copy(
            balance = Math.round(newBalance * 100).toDouble() / 100.0,
            totalCashback = Math.round(newTotalCashback * 100).toDouble() / 100.0
        )

        // Make receipt/name pretty if blank
        val cleanName = name.ifBlank { upiId.substringBefore("@").replaceFirstChar { it.uppercase() } }
        val cleanUpi = if (upiId.contains("@")) upiId.lowercase(Locale.ROOT) else "$upiId@upi"

        val transaction = Transaction(
            upiId = cleanUpi,
            name = cleanName,
            amount = amount,
            isIncoming = false,
            timestamp = System.currentTimeMillis(),
            cashbackEarned = cashbackEarned,
            status = "SUCCESS",
            note = note.ifBlank { "Unsecured Payment" }
        )

        // Save to Room
        profileDao.insertOrUpdateProfile(updatedProfile)
        transactionDao.insertTransaction(transaction)

        return SendPaymentResult.Success(
            cashbackEarned = cashbackEarned,
            newBalance = updatedProfile.balance,
            transaction = transaction
        )
    }

    suspend fun receivePayment(
        senderName: String,
        senderUpi: String,
        amount: Double,
        note: String
    ): Boolean {
        if (amount <= 0) return false
        val currentProfile = profileDao.getProfile() ?: return false

        val newBalance = currentProfile.balance + amount
        val updatedProfile = currentProfile.copy(
            balance = Math.round(newBalance * 100).toDouble() / 100.0
        )

        val cleanName = senderName.ifBlank { "External Sender" }
        val cleanUpi = if (senderUpi.contains("@")) senderUpi.lowercase(Locale.ROOT) else "$senderUpi@upi"

        val transaction = Transaction(
            upiId = cleanUpi,
            name = cleanName,
            amount = amount,
            isIncoming = true,
            timestamp = System.currentTimeMillis(),
            cashbackEarned = 0.00,
            status = "SUCCESS",
            note = note.ifBlank { "Received via QR Code" }
        )

        profileDao.insertOrUpdateProfile(updatedProfile)
        transactionDao.insertTransaction(transaction)
        return true
    }

    suspend fun updateProfileInfo(name: String, upiId: String): Boolean {
        val current = profileDao.getProfile() ?: return false
        val cleanUpi = if (upiId.contains("@")) upiId.lowercase(Locale.ROOT) else "$upiId@upi"
        val updated = current.copy(
            name = name.ifBlank { "Aero Pay User" },
            upiId = if (upiId.isBlank()) "aeropay@upi" else cleanUpi
        )
        profileDao.insertOrUpdateProfile(updated)
        return true
    }

    suspend fun resetWallet() {
        val resetProfile = Profile(
            id = 1,
            name = "Aero Pay User",
            upiId = "aeropay@upi",
            balance = 5000.00,
            totalCashback = 0.00
        )
        profileDao.insertOrUpdateProfile(resetProfile)
        transactionDao.clearTransactions()
    }
}

sealed class SendPaymentResult {
    data class Success(
        val cashbackEarned: Double,
        val newBalance: Double,
        val transaction: Transaction
    ) : SendPaymentResult()

    data class Error(val message: String) : SendPaymentResult()
}
