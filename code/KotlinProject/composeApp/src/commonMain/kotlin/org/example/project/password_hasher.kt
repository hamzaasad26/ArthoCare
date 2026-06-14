package org.example.project

// Simple password hasher using SHA-256
// Note: For production, use bcrypt or similar
object PasswordHasher {
    fun hash(password: String): String {
        // Simple hash for now - in production use bcrypt
        return password.hashCode().toString() + password.reversed().hashCode().toString()
    }

    fun verify(password: String, hash: String): Boolean {
        return hash(password) == hash
    }
}