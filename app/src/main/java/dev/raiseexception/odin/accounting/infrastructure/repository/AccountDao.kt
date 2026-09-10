package dev.raiseexception.odin.accounting.infrastructure.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Insert
    suspend fun insert(account: AccountEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM accounts WHERE LOWER(name) = LOWER(:name))")
    suspend fun existsByName(name: String): Boolean

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun findById(id: String): Flow<AccountEntity?>

    @Transaction
    @Query("SELECT * FROM accounts WHERE id = :id")
    fun findByIdWithTransactions(id: String): Flow<AccountWithTransactions?>

    @Query("SELECT * FROM accounts")
    fun getAll(): Flow<List<AccountEntity>>

    @Transaction
    @Query("SELECT * FROM accounts")
    fun getAllWithTransactions(): Flow<List<AccountWithTransactions>>
}
