package dev.raiseexception.odin.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.raiseexception.odin.accounting.infrastructure.repository.AccountDao
import dev.raiseexception.odin.accounting.infrastructure.repository.AccountEntity
import dev.raiseexception.odin.accounting.infrastructure.repository.CategoryDao
import dev.raiseexception.odin.accounting.infrastructure.repository.CategoryEntity
import dev.raiseexception.odin.accounting.infrastructure.repository.TransactionDao
import dev.raiseexception.odin.accounting.infrastructure.repository.TransactionEntity
import dev.raiseexception.odin.accounts.infrastructure.repository.UserDao
import dev.raiseexception.odin.accounts.infrastructure.repository.UserEntity

@Database(
    entities = [UserEntity::class, AccountEntity::class, CategoryEntity::class, TransactionEntity::class],
    version = 3,
    exportSchema = false
)
abstract class OdinDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
}
