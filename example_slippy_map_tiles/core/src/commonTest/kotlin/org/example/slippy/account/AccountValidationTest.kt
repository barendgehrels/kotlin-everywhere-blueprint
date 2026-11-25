package org.example.slippy.account

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountValidationTest {
    @Test
    fun lettersAndDigitsOnly() {
        assertTrue(isGuestNameValid("Barend42"))
        assertFalse(isGuestNameValid("Bar end"))
        assertFalse(isGuestNameValid("barend@example.com"))
        assertFalse(isGuestNameValid("Zoë"))           // a to z only
    }

    @Test
    fun guestNameLength() {
        assertFalse(isGuestNameValid("ab"))
        assertTrue(isGuestNameValid("abc"))
        assertTrue(isGuestNameValid("a".repeat(40)))
        assertFalse(isGuestNameValid("a".repeat(41)))
    }

    @Test
    fun guestNameIsNeverAnEmail() {
        listOf("abc", "Guest1", "x".repeat(40)).forEach { assertFalse(isEmailValid(it)) }
    }

    @Test
    fun emailNeedsAtAndDomain() {
        assertTrue(isEmailValid("someone@example.com"))
        assertFalse(isEmailValid("someone@example"))
        assertFalse(isEmailValid("some one@example.com"))
        assertFalse(isEmailValid("a@b.c" + "c".repeat(EMAIL_MAX_LENGTH)))
    }

    @Test
    fun passwordLength() {
        assertFalse(isPasswordValid("12345"))
        assertTrue(isPasswordValid("123456"))
        assertFalse(isPasswordValid("x".repeat(PASSWORD_MAX_LENGTH + 1)))
    }
}
