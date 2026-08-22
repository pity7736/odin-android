package dev.raiseexception.odin.accounts.domain.model

import dev.raiseexception.odin.accounts.domain.RegistrationError
import dev.raiseexception.odin.shared.domain.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MAX_PASSWORD_LENGTH = 100
private const val OVER_MAX_PASSWORD_LENGTH = 101

class PasswordTest {

    @Test
    fun `given a valid password of 12 characters, when creating, then returns success`() {
        val result = Password.create("123456789012")

        assertTrue(result is Outcome.Success)
        assertEquals("123456789012", (result as Outcome.Success).value.value)
    }

    @Test
    fun `given a valid password of 100 characters, when creating, then returns success`() {
        val raw = "a".repeat(MAX_PASSWORD_LENGTH)

        val result = Password.create(raw)

        assertTrue(result is Outcome.Success)
        assertEquals(raw, (result as Outcome.Success).value.value)
    }

    @Test
    fun `given a valid password between 12 and 100 characters, when creating, then returns success`() {
        val raw = "mySecurePassword123"

        val result = Password.create(raw)

        assertTrue(result is Outcome.Success)
        assertEquals(raw, (result as Outcome.Success).value.value)
    }

    @Test
    fun `given a valid password with special characters, when creating, then returns success`() {
        val raw = "p@ss!w0rd#\$%^"

        val result = Password.create(raw)

        assertTrue(result is Outcome.Success)
        assertEquals(raw, (result as Outcome.Success).value.value)
    }

    @Test
    fun `given a password shorter than 12 characters, when creating, then returns failure`() {
        val result = Password.create("short")

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is RegistrationError.InvalidPassword)
    }

    @Test
    fun `given an empty password, when creating, then returns failure`() {
        val result = Password.create("")

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is RegistrationError.InvalidPassword)
    }

    @Test
    fun `given a password longer than 100 characters, when creating, then returns failure`() {
        val raw = "a".repeat(OVER_MAX_PASSWORD_LENGTH)

        val result = Password.create(raw)

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is RegistrationError.InvalidPassword)
    }
}
