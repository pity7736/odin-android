package dev.raiseexception.odin.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.raiseexception.odin.accounts.infrastructure.repository.UserDao
import dev.raiseexception.odin.accounts.infrastructure.repository.UserEntity

@Database(entities = [UserEntity::class], version = 1, exportSchema = false)
abstract class OdinDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}
