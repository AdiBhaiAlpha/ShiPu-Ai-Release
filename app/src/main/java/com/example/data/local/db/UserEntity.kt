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
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toUser(): User = User(
        userId = userId,
        email = email,
        passwordHash = passwordHash,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromUser(user: User): UserEntity = UserEntity(
            userId = user.userId,
            email = user.email,
            passwordHash = user.passwordHash,
            name = user.name,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt
        )
    }
}
