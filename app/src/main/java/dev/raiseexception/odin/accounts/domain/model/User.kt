package dev.raiseexception.odin.accounts.domain.model

data class User(
    val id: String,
    val salt: ByteArray,
    val wrappedMasterKey: ByteArray
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return id == other.id &&
            salt.contentEquals(other.salt) &&
            wrappedMasterKey.contentEquals(other.wrappedMasterKey)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = HASH_MULTIPLIER * result + salt.contentHashCode()
        result = HASH_MULTIPLIER * result + wrappedMasterKey.contentHashCode()
        return result
    }

    override fun toString(): String = "User(id=$id, salt=***, wrappedMasterKey=***)"

    private companion object {
        const val HASH_MULTIPLIER = 31
    }
}
