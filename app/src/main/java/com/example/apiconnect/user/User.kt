package com.example.apiconnect.user

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    var id: String? = null,
    var name: String? = null,
    var email: String? = null,
)
