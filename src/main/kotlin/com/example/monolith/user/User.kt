package com.example.monolith.user

import com.example.monolith.common.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "users")
data class User(
    @Column(nullable = false, unique = true)
    val email: String,

    @Column(nullable = false)
    val name: String
) : BaseEntity()