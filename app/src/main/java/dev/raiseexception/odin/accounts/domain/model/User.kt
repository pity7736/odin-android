package dev.raiseexception.odin.accounts.domain.model

data class User(
    val id: String,
    val salt: ByteArray,
    val wrappedMasterKey: ByteArray
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return this.id == other.id
    }

    override fun hashCode(): Int = this.id.hashCode()

    override fun toString(): String = "User(id=$id, salt=***, wrappedMasterKey=***)"
}
