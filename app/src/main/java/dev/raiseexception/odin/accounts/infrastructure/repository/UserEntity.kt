package dev.raiseexception.odin.accounts.infrastructure.repository

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.raiseexception.odin.accounts.domain.model.User

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val salt: ByteArray,
    val wrappedMasterKey: ByteArray,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserEntity) return false
        return this.id == other.id
    }

    override fun hashCode(): Int = this.id.hashCode()
}

fun UserEntity.toDomain(): User = User(
    id = id,
    salt = salt,
    wrappedMasterKey = wrappedMasterKey,
)

fun User.toEntity(): UserEntity = UserEntity(
    id = id,
    salt = salt,
    wrappedMasterKey = wrappedMasterKey,
)
