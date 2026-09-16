package dev.raiseexception.odin.accounting.infrastructure.repository

import androidx.room.Dao
import androidx.room.Insert

@Dao
interface TransferDao {

    @Insert
    suspend fun insert(entity: TransferEntity)
}
