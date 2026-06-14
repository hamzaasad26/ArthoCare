package org.example.project

object PasswordValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val message: String
    )

    fun validate(password: String): ValidationResult {
        // Check minimum length
        if (password.length < 8) {
            return ValidationResult(false, "Password must be at least 8 characters long")
        }

        // Check for uppercase letter
        if (!password.any { it.isUpperCase() }) {
            return ValidationResult(false, "Password must contain at least one uppercase letter")
        }

        // Check for lowercase letter
        if (!password.any { it.isLowerCase() }) {
            return ValidationResult(false, "Password must contain at least one lowercase letter")
        }

        // Check for digit
        if (!password.any { it.isDigit() }) {
            return ValidationResult(false, "Password must contain at least one number")
        }

        // Check for special character
        val specialCharacters = "!@#$%^&*(),.?\":{}|<>_-+="
        if (!password.any { it in specialCharacters }) {
            return ValidationResult(false, "Password must contain at least one special character (!@#$%^&* etc.)")
        }

        return ValidationResult(true, "Password is strong")
    }

    fun getRequirements(): List<String> {
        return listOf(
            "• At least 8 characters long",
            "• Contains uppercase letter (A-Z)",
            "• Contains lowercase letter (a-z)",
            "• Contains number (0-9)",
            "• Contains special character (!@#$%^&* etc.)"
        )
    }
}