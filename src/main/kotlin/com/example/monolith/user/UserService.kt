package com.example.monolith.user

import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository
) {
    fun createUser(request: CreateUserRequest): User {
        val user = User(
            email = request.email,
            name = request.name
        )
        return userRepository.save(user)
    }

    fun getAllUsers(): List<User> {
        return userRepository.findAll()
    }

    fun getUserById(id: Long): User {
        return userRepository.findById(id)
            .orElseThrow { IllegalArgumentException("User not found with id: $id") }
    }

    fun getUserByEmail(email: String): User? {
        return userRepository.findByEmail(email)
    }
}

data class CreateUserRequest(
    val email: String,
    val name: String
)