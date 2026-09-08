package dev.raiseexception.odin.crypto.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TEST_PASSWORD_LENGTH = 14

class SensitivePasswordTest {

    @Test
    fun `given a password, when checking length, then returns the character count`() {
        val password = SensitivePassword("my-secret-pass".toCharArray())

        assertEquals(TEST_PASSWORD_LENGTH, password.length)
    }

    @Test
    fun `given a password, when checking isEmpty, then returns false`() {
        val password = SensitivePassword("my-secret-pass".toCharArray())

        assertFalse(password.isEmpty())
    }

    @Test
    fun `given an empty password, when checking isEmpty, then returns true`() {
        val password = SensitivePassword(charArrayOf())

        assertTrue(password.isEmpty())
    }

    @Test
    fun `given a blank password, when checking isBlank, then returns true`() {
        val password = SensitivePassword("   ".toCharArray())

        assertTrue(password.isBlank())
    }

    @Test
    fun `given a non-blank password, when checking isBlank, then returns false`() {
        val password = SensitivePassword("my-secret-pass".toCharArray())

        assertFalse(password.isBlank())
    }

    @Test
    fun `given a password, when converting to UTF-8 bytes, then returns correct encoding`() {
        val password = SensitivePassword("hello".toCharArray())

        assertArrayEquals("hello".toByteArray(Charsets.UTF_8), password.toUtf8Bytes())
    }

    @Test
    fun `given a password, when wiped, then becomes blank`() {
        val password = SensitivePassword("my-secret-pass".toCharArray())

        password.wipe()

        assertTrue(password.isBlank())
        assertEquals(TEST_PASSWORD_LENGTH, password.length)
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
