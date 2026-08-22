package dev.raiseexception.odin.accounts.infrastructure.repository

import dev.raiseexception.odin.accounts.domain.RegistrationError
import dev.raiseexception.odin.accounts.domain.model.User
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val BYTE_ARRAY_SIZE = 8
private const val SECOND_USER_OFFSET = 2
private const val SECOND_KEY_OFFSET = 3

class InMemoryUserRepositoryTest {

    private lateinit var repository: InMemoryUserRepository

    @Before
    fun setUp() {
        repository = InMemoryUserRepository()
    }

    @Test
    fun `given no user exists, when adding user, then returns success`() = runTest {
        val user = buildUser("user-1")

        val result = repository.add(user)

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `given no user exists, when checking exists, then returns false`() = runTest {
        val result = repository.exists()

        assertFalse(result)
    }

    @Test
    fun `given user was added, when checking exists, then returns true`() = runTest {
        repository.add(buildUser("user-1"))

        val result = repository.exists()

        assertTrue(result)
    }

    @Test
    fun `given user already exists, when adding another, then returns failure`() = runTest {
        repository.add(buildUser("user-1"))

        val result = repository.add(
            User(
                id = "user-2",
                salt = ByteArray(BYTE_ARRAY_SIZE) { (it + SECOND_USER_OFFSET).toByte() },
                wrappedMasterKey = ByteArray(BYTE_ARRAY_SIZE) { (it + SECOND_KEY_OFFSET).toByte() }
            )
        )

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is RegistrationError.StorageFailure)
    }

    private fun buildUser(id: String) = User(
        id = id,
        salt = ByteArray(BYTE_ARRAY_SIZE) { it.toByte() },
        wrappedMasterKey = ByteArray(BYTE_ARRAY_SIZE) { (it + 1).toByte() }
    )
}
