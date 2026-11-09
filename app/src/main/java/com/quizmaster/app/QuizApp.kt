package com.quizmaster.app

import android.app.Application
import com.quizmaster.app.data.AppDatabase
import com.quizmaster.app.data.QuizRepository

class QuizApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: QuizRepository by lazy { QuizRepository(database) }
}
