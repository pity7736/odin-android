package dev.raiseexception.odin.crypto.infrastructure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.raiseexception.odin.crypto.domain.repository.SaltRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Base64

class DataStoreSaltRepository(
    private val dataStore: DataStore<Preferences>
) : SaltRepository {

    override suspend fun save(salt: ByteArray): Outcome<Unit> =
        try {
            this.dataStore.edit { preferences ->
                preferences[SALT_KEY] = Base64.getEncoder().encodeToString(salt)
            }
            Outcome.Success(Unit)
        } catch (exception: IOException) {
            Outcome.Failure(
                StorageError(internalMessage = "Failed to save salt: ${exception.message}")
            )
        }

    override suspend fun get(): ByteArray? =
        this.dataStore.data
            .map { preferences -> preferences[SALT_KEY] }
            .first()
            ?.let { encoded -> Base64.getDecoder().decode(encoded) }

    override suspend fun exists(): Boolean =
        this.dataStore.data
            .map { preferences -> preferences.contains(SALT_KEY) }
            .first()

    override suspend fun delete() {
        this.dataStore.edit { preferences ->
            preferences.remove(SALT_KEY)
        }
    }

    companion object {
        private val SALT_KEY = stringPreferencesKey("salt")
    }
}
