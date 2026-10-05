package com.rackexcel.mobile.storage

data class ModelConfig(
    val url: String,
    val model: String,
    val apiKey: String,
    val concurrency: Int = 5,
)
