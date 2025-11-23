package com.example.igp  // Use your actual package name

data class User(
    val username: String = "",
    val email: String = "",
    val phone: String = "",
    val street: String = "",
    val city: String = "",
    val state: String = "",
    val password: String = "",
    val pincode: String = "",
    val score: Int = 0,
    val donations: Int = 0
)
