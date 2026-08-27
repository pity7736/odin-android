package dev.raiseexception.odin.accounts.infrastructure.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: UserEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM users LIMIT 1)")
    suspend fun exists(): Boolean

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun get(): UserEntity?
}
