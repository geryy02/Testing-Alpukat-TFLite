package com.dicoding.testingalpukat.data

data class Result (
    var id: String = "", // Add an ID field to store the Firestore document ID
    val image_url: String = "",
    val kematangan: String = "",
    val score: String = ""
)