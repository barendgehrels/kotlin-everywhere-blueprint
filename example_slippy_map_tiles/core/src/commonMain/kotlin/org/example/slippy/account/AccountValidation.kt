package org.example.slippy.account

const val GUEST_NAME_MIN_LENGTH = 3
const val GUEST_NAME_MAX_LENGTH = 40
const val EMAIL_MAX_LENGTH = 254
const val PASSWORD_MIN_LENGTH = 6      // Firebase's own minimum
const val PASSWORD_MAX_LENGTH = 128

private val guestNameRegex = Regex("^[A-Za-z0-9]+$")
private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** 3 to 40 letters (a to z, capitals allowed) or digits: never taken for an email address. */
fun isGuestNameValid(name: String): Boolean =
    name.length in GUEST_NAME_MIN_LENGTH..GUEST_NAME_MAX_LENGTH && guestNameRegex.matches(name)

/** A rough check only: Firebase decides. It keeps a guest name and an email address apart. */
fun isEmailValid(email: String): Boolean =
    email.length <= EMAIL_MAX_LENGTH && emailRegex.matches(email)

fun isPasswordValid(password: String): Boolean =
    password.length in PASSWORD_MIN_LENGTH..PASSWORD_MAX_LENGTH
