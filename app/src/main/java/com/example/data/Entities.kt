package com.example.data

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val upiId: String,
    val name: String,
    val amount: Double,
    val isIncoming: Boolean,
    val timestamp: Long,
    val cashbackEarned: Double,
    val status: String = "SUCCESS",
    val note: String = ""
)

@Keep
@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val upiId: String,
    val balance: Double,
    val totalCashback: Double
)
