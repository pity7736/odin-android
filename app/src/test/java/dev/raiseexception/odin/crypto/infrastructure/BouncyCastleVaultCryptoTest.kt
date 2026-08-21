package dev.raiseexception.odin.crypto.infrastructure

import dev.raiseexception.odin.crypto.domain.VaultCrypto
import dev.raiseexception.odin.crypto.domain.VaultCryptoContractTest

class BouncyCastleVaultCryptoTest : VaultCryptoContractTest() {

    override fun createVaultCrypto(): VaultCrypto = BouncyCastleVaultCrypto()
}
