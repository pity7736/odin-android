package dev.raiseexception.odin.shared.infrastructure.vault

import dev.raiseexception.odin.crypto.domain.VaultCrypto
import dev.raiseexception.odin.crypto.domain.repository.MasterKeyRepository
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InMemoryEncryptedRecordStore(
    private val vaultCrypto: VaultCrypto,
    private val masterKeyRepository: MasterKeyRepository,
    private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default
) : EncryptedRecordStore {

    private val encryptedBlobs = mutableMapOf<String, ByteArray>()

    internal val entries: Map<String, ByteArray> get() = this.encryptedBlobs.toMap()

    override suspend fun save(id: String, plaintext: ByteArray): Outcome<Unit> =
        withContext(this.cpuDispatcher) {
            val masterKey = when (val masterKeyOutcome = masterKeyRepository.get()) {
                is Outcome.Success -> masterKeyOutcome.value
                is Outcome.Failure -> return@withContext masterKeyOutcome
            }
            when (val encryptOutcome = vaultCrypto.encrypt(plaintext, masterKey)) {
                is Outcome.Success -> {
                    encryptedBlobs[id] = encryptOutcome.value
                    Outcome.Success(Unit)
                }
                is Outcome.Failure -> encryptOutcome
            }
        }

    override suspend fun readAll(): Outcome<List<StoredRecord>> =
        withContext(this.cpuDispatcher) {
            val masterKey = when (val masterKeyOutcome = masterKeyRepository.get()) {
                is Outcome.Success -> masterKeyOutcome.value
                is Outcome.Failure -> return@withContext masterKeyOutcome
            }
            decryptAll(masterKey)
        }

    private fun decryptAll(masterKey: ByteArray): Outcome<List<StoredRecord>> {
        val records = mutableListOf<StoredRecord>()
        for ((id, blob) in this.encryptedBlobs) {
            when (val decryptOutcome = this.vaultCrypto.decrypt(blob, masterKey)) {
                is Outcome.Success -> records.add(StoredRecord(id, decryptOutcome.value))
                is Outcome.Failure -> return decryptOutcome
            }
        }
        return Outcome.Success(records)
    }
}
