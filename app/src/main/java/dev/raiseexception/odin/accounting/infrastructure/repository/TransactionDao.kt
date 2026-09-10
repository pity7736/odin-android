package dev.raiseexception.odin.accounting.infrastructure.repository

import androidx.room.Dao
import androidx.room.Insert

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: TransactionEntity)
}
