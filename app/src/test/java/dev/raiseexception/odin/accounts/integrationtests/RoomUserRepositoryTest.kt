package dev.raiseexception.odin.accounts.integrationtests

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.raiseexception.odin.accounts.domain.LoginError
import dev.raiseexception.odin.accounts.domain.RegistrationError
import dev.raiseexception.odin.accounts.domain.model.User
import dev.raiseexception.odin.accounts.infrastructure.repository.RoomUserRepository
import dev.raiseexception.odin.persistence.OdinDatabase
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val BYTE_ARRAY_SIZE = 8

@RunWith(RobolectricTestRunner::class)
class RoomUserRepositoryTest {

    private lateinit var database: OdinDatabase
    private lateinit var repository: RoomUserRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OdinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomUserRepository(database.userDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `given empty database, when exists called, then returns false`() = runTest {
        val result = repository.exists()

        assertFalse(result)
    }

    @Test
    fun `given empty database, when get called, then returns UserNotFound`() = runTest {
        val result = repository.get()

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is LoginError.UserNotFound)
    }

    @Test
    fun `given empty database, when add called with user, then returns success`() = runTest {
        val user = buildUser("user-1")

        val result = repository.add(user)

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `given user in database, when exists called, then returns true`() = runTest {
        repository.add(buildUser("user-1"))

        val result = repository.exists()

        assertTrue(result)
    }

    @Test
    fun `given user in database, when get called, then returns the same user`() = runTest {
        val storedUser = buildUser("user-1")
        repository.add(storedUser)

        val result = repository.get()

        assertTrue(result is Outcome.Success)
        assertEquals(storedUser, (result as Outcome.Success).value)
    }

    @Test
    fun `given user in database, when add called again, then returns StorageFailure`() = runTest {
        repository.add(buildUser("user-1"))

        val result = repository.add(buildUser("user-2"))

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is RegistrationError.StorageFailure)
    }

    private fun buildUser(id: String) = User(
        id = id,
        salt = ByteArray(BYTE_ARRAY_SIZE) { it.toByte() },
        wrappedMasterKey = ByteArray(BYTE_ARRAY_SIZE) { (it + 1).toByte() }
    )
}
