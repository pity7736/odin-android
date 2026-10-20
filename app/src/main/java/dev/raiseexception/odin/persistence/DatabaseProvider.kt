package dev.raiseexception.odin.persistence

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.shared.domain.VaultUnlocker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class DatabaseProvider(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
    private val openHelperFactoryProvider: (ByteArray) -> SupportSQLiteOpenHelper.Factory? = { key ->
        SupportOpenHelperFactory(key)
    }
) : VaultUnlocker {

    @Volatile
    private var database: OdinDatabase? = null

    fun requireDatabase(): OdinDatabase =
        checkNotNull(this.database) { "Vault is locked. Call unlock() first." }

    override suspend fun unlock(encryptionKey: ByteArray): Outcome<Unit> =
        withContext(this.ioDispatcher) { openDatabase(encryptionKey) }

    override suspend fun discard(): Outcome<Unit> =
        withContext(this.ioDispatcher) { deleteDatabase() }

    private fun openDatabase(encryptionKey: ByteArray): Outcome<Unit> {
        if (this.database != null) return Outcome.Success(Unit)
        val rawKeyHex = encryptionKey.joinToString("") { "%02x".format(it) }
        val formattedKey = "x'$rawKeyHex'"
        val factory = this.openHelperFactoryProvider(formattedKey.toByteArray())
        val builder = Room.databaseBuilder(this.context, OdinDatabase::class.java, DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        if (factory != null) {
            builder.openHelperFactory(factory)
        }
        val newDatabase = builder.build()
        return try {
            newDatabase.openHelper.writableDatabase
            this.database = newDatabase
            Outcome.Success(Unit)
        } catch (@Suppress("SwallowedException") exception: SQLiteException) {
            newDatabase.close()
            Outcome.Failure(
                StorageError(internalMessage = "Failed to open encrypted database: wrong key or corrupt file")
            )
        } catch (@Suppress("SwallowedException") exception: UnsatisfiedLinkError) {
            newDatabase.close()
            Outcome.Failure(
                StorageError(internalMessage = "SQLCipher native library not loaded")
            )
        }
    }

    private fun deleteDatabase(): Outcome<Unit> {
        this.database?.close()
        this.database = null
        this.context.deleteDatabase(DATABASE_NAME)
        if (this.context.getDatabasePath(DATABASE_NAME).exists()) {
            return Outcome.Failure(StorageError(internalMessage = "Failed to delete the encrypted database file"))
        }
        return Outcome.Success(Unit)
    }

    companion object {
        private const val DATABASE_NAME = "odin_db"
    }
}
