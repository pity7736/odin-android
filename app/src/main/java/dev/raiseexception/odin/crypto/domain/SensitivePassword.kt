package dev.raiseexception.odin.crypto.domain

class SensitivePassword(private val characters: CharArray) {

    val value: CharArray get() = this.characters

    fun wipe() {
        this.characters.fill('\u0020')
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SensitivePassword) return false
        return this.characters.contentEquals(other.characters)
    }

    override fun hashCode(): Int = this.characters.contentHashCode()

    override fun toString(): String = "SensitivePassword(***)"
}
