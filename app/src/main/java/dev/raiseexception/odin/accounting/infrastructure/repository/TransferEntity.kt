package dev.raiseexception.odin.accounting.infrastructure.repository

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transfers",
    foreignKeys = [
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["expenseId"]),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["incomeId"])
    ],
    indices = [Index("expenseId"), Index("incomeId")]
)
data class TransferEntity(
    @PrimaryKey val id: String,
    val expenseId: String,
    val incomeId: String,
    val createdAt: String
)
