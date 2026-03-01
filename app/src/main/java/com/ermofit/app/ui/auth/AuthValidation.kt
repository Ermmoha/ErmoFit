package com.ermofit.app.ui.auth

enum class AuthValidationError {
    NAME_REQUIRED,
    NAME_TOO_SHORT,
    NAME_TOO_LONG,
    NAME_INVALID_CHARS,
    EMAIL_REQUIRED,
    EMAIL_TOO_LONG,
    EMAIL_INVALID,
    PASSWORD_REQUIRED,
    PASSWORD_TOO_SHORT,
    PASSWORD_TOO_LONG,
    PASSWORD_HAS_SPACES,
    PASSWORD_NEEDS_UPPER,
    PASSWORD_NEEDS_LOWER,
    PASSWORD_NEEDS_DIGIT,
    PASSWORD_NEEDS_SPECIAL,
    REPEAT_PASSWORD_REQUIRED,
    PASSWORDS_MISMATCH,
    LOGIN_PASSWORD_TOO_SHORT
}

object AuthValidation {
    private val namePattern = Regex("^[\\p{L}][\\p{L}\\s'\\-]{1,29}$")
    private val emailPattern = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun nameError(value: String): AuthValidationError? {
        val name = value.trim()
        return when {
            name.isEmpty() -> AuthValidationError.NAME_REQUIRED
            name.length < 2 -> AuthValidationError.NAME_TOO_SHORT
            name.length > 30 -> AuthValidationError.NAME_TOO_LONG
            !namePattern.matches(name) -> AuthValidationError.NAME_INVALID_CHARS
            else -> null
        }
    }

    fun emailError(value: String): AuthValidationError? {
        val email = value.trim()
        return when {
            email.isEmpty() -> AuthValidationError.EMAIL_REQUIRED
            email.length > 254 -> AuthValidationError.EMAIL_TOO_LONG
            !emailPattern.matches(email) -> AuthValidationError.EMAIL_INVALID
            else -> null
        }
    }

    fun registerPasswordError(value: String): AuthValidationError? {
        return when {
            value.isEmpty() -> AuthValidationError.PASSWORD_REQUIRED
            value.length < 8 -> AuthValidationError.PASSWORD_TOO_SHORT
            value.length > 64 -> AuthValidationError.PASSWORD_TOO_LONG
            value.any(Char::isWhitespace) -> AuthValidationError.PASSWORD_HAS_SPACES
            value.none(Char::isUpperCase) -> AuthValidationError.PASSWORD_NEEDS_UPPER
            value.none(Char::isLowerCase) -> AuthValidationError.PASSWORD_NEEDS_LOWER
            value.none(Char::isDigit) -> AuthValidationError.PASSWORD_NEEDS_DIGIT
            value.none { !it.isLetterOrDigit() } -> AuthValidationError.PASSWORD_NEEDS_SPECIAL
            else -> null
        }
    }

    fun repeatPasswordError(password: String, repeatPassword: String): AuthValidationError? {
        return when {
            repeatPassword.isEmpty() -> AuthValidationError.REPEAT_PASSWORD_REQUIRED
            password != repeatPassword -> AuthValidationError.PASSWORDS_MISMATCH
            else -> null
        }
    }

    fun loginPasswordError(value: String): AuthValidationError? {
        return when {
            value.isEmpty() -> AuthValidationError.PASSWORD_REQUIRED
            value.length < 6 -> AuthValidationError.LOGIN_PASSWORD_TOO_SHORT
            else -> null
        }
    }
}
