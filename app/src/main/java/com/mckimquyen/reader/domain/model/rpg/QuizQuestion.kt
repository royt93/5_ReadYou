package com.mckimquyen.reader.domain.model.rpg

data class QuizQuestion(
    val articleId: String,
    val question: String,
    val options: List<String>,
    val correctAnswerIndex: Int,
    val explanation: String,
    val category: String
)
