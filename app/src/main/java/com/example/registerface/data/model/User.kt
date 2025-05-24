package com.example.registerface.data.model

import java.util.Date

data class User(
    val id: String,
    val name: String,
    val email: String,
    val registrationDate: Date,
    val lastLogin: Date,
    val faceData: String? = null
) 