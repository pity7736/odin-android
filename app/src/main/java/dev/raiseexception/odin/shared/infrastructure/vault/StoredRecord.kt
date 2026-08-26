package dev.raiseexception.odin.shared.infrastructure.vault

class StoredRecord(
    val id: String,
    val data: ByteArray
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StoredRecord) return false
        return this.id == other.id && this.data.contentEquals(other.data)
    }

    override fun hashCode(): Int = HASH_MULTIPLIER * this.id.hashCode() + this.data.contentHashCode()

    override fun toString(): String = "StoredRecord(id=${this.id}, data=***)"

    private companion object {
        const val HASH_MULTIPLIER = 31
    }
}
