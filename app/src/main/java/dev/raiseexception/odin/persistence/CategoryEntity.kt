package dev.raiseexception.odin.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val data: ByteArray
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CategoryEntity) return false
        return this.id == other.id
    }

    override fun hashCode(): Int = this.id.hashCode()
}
