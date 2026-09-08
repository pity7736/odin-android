package dev.raiseexception.odin.crypto.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

private const val TEST_PASSWORD_LENGTH = 14

class SensitivePasswordTest {

    @Test
    fun `given a password, when accessing value, then returns the original content`() {
        val characters = "my-secret-pass".toCharArray()
        val password = SensitivePassword(characters)

        assertArrayEquals(characters, password.value)
    }

    @Test
    fun `given a password, when wiped, then characters are all zeroes`() {
        val password = SensitivePassword("my-secret-pass".toCharArray())

        password.wipe()

        val expected = CharArray(TEST_PASSWORD_LENGTH) { '\u0020' }
        assertArrayEquals(expected, password.value)
    }

    @Test
    fun `given two passwords with same content, when compared, then they are equal`() {
        val first = SensitivePassword("same-password1".toCharArray())
        val second = SensitivePassword("same-password1".toCharArray())

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `given two passwords with different content, when compared, then they are not equal`() {
        val first = SensitivePassword("password-one1".toCharArray())
        val second = SensitivePassword("password-two2".toCharArray())

        assertNotEquals(first, second)
    }

    @Test
    fun `given a password, when converted to string, then content is redacted`() {
        val password = SensitivePassword("my-secret-pass".toCharArray())

        assertEquals("SensitivePassword(***)", password.toString())
    }
}
