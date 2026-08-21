package dev.raiseexception.odin.crypto.infrastructure

import dev.raiseexception.odin.crypto.domain.repository.MasterKeyRepository
import dev.raiseexception.odin.crypto.domain.repository.MasterKeyRepositoryContractTest

class InMemoryMasterKeyRepositoryTest : MasterKeyRepositoryContractTest() {

    override fun createRepository(): MasterKeyRepository = InMemoryMasterKeyRepository()
}
