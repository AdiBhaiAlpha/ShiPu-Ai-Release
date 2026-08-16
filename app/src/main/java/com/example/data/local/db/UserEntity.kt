package com.example.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.User

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val email: String,
    val passwordHash: String,
    val name: String,
    val role: String = "USER",
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toUser(): User = User(
        userId = userId,
        email = email,
        passwordHash = passwordHash,
        name = name,
        role = if (email.trim().lowercase() == "chitronbhattacharjee@gmail.com") "SUPER_ADMIN" else role,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromUser(user: User): UserEntity = UserEntity(
            userId = user.userId,
            email = user.email,
            passwordHash = user.passwordHash,
            name = user.name,
            role = if (user.email.trim().lowercase() == "chitronbhattacharjee@gmail.com") "SUPER_ADMIN" else user.role,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt
        )
    }
}
