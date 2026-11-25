package org.example.slippy.authentication

import org.example.slippy.foundation.ERROR_CODE_AUTHENTICATION
import org.example.slippy.foundation.ERROR_CODE_LOGIN
import org.example.slippy.foundation.ERROR_CODE_TOO_MANY_REQUESTS
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthenticationFailureTest {
    private val expected = mapOf("INVALID_LOGIN_CREDENTIALS" to ExpectedError(ERROR_CODE_LOGIN))

    @Test
    fun expectedErrorIsTheUsersProblem() {
        val e = authenticationFailure(400, "INVALID_LOGIN_CREDENTIALS", expected)
        assertEquals(ERROR_CODE_LOGIN, e.code)
        assertEquals(400, e.status)
    }

    @Test
    fun tooManyAttemptsIsA429() =
        assertEquals(ERROR_CODE_TOO_MANY_REQUESTS,
            authenticationFailure(400, "TOO_MANY_ATTEMPTS_TRY_LATER : Too many", expected).code)

    @Test
    fun unexpectedErrorIsFirebasesProblem() {
        val e = authenticationFailure(400, "OPERATION_NOT_ALLOWED", expected)
        assertEquals(ERROR_CODE_AUTHENTICATION, e.code)
        assertEquals(502, e.status)
    }

    @Test
    fun serverErrorIsFirebasesProblemEvenWithAKnownName() =
        assertEquals(502, authenticationFailure(503, "INVALID_LOGIN_CREDENTIALS", expected).status)
}
