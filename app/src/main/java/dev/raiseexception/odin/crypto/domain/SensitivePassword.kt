package dev.raiseexception.odin.crypto.domain

class SensitivePassword(private val characters: CharArray) {

    val length: Int get() = this.characters.size

    fun isEmpty(): Boolean = this.characters.isEmpty()

    fun isBlank(): Boolean = this.characters.isEmpty() || this.characters.all { it.isWhitespace() }

    fun toUtf8Bytes(): ByteArray = String(this.characters).toByteArray(Charsets.UTF_8)

    fun wipe() {
        this.characters.fill(' ')
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SensitivePassword) return false
        return this.characters.contentEquals(other.characters)
    }

    override fun hashCode(): Int = this.characters.contentHashCode()

    override fun toString(): String = "SensitivePassword(***)"
}
