package com.quizmaster.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quizmaster.app.data.QuizMode
import com.quizmaster.app.ui.edit.EditQuestionScreen
import com.quizmaster.app.ui.history.HistoryScreen
import com.quizmaster.app.ui.history.SessionDetailScreen
import com.quizmaster.app.ui.home.HomeScreen
import com.quizmaster.app.ui.importer.ImportScreen
import com.quizmaster.app.ui.quiz.QuizScreen
import com.quizmaster.app.ui.set.SetDetailScreen
import com.quizmaster.app.ui.wrong.WrongBookScreen

/** limit = 每批題數，0 代表不限 */
private fun quizRoute(mode: QuizMode, setId: Long, shuffle: Boolean, limit: Int) =
    "quiz?mode=${mode.name}&setId=$setId&shuffle=$shuffle&limit=$limit"

@Composable
fun AppNavigation() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "home") {

        composable("home") {
            HomeScreen(
                onOpenSet = { nav.navigate("set/$it") },
                onWrongBook = { nav.navigate("wrong") },
                onHistory = { nav.navigate("history") },
            )
        }

        composable(
            "set/{setId}",
            arguments = listOf(navArgument("setId") { type = NavType.LongType }),
        ) { entry ->
            val setId = entry.arguments?.getLong("setId") ?: return@composable
            SetDetailScreen(
                setId = setId,
                onBack = { nav.popBackStack() },
                onEditQuestion = { qid -> nav.navigate("edit/$setId/$qid") },
                onImport = { nav.navigate("import/$setId") },
                onStartQuiz = { mode, shuffle, limit -> nav.navigate(quizRoute(mode, setId, shuffle, limit)) },
            )
        }

        composable(
            "edit/{setId}/{questionId}",
            arguments = listOf(
                navArgument("setId") { type = NavType.LongType },
                navArgument("questionId") { type = NavType.LongType },
            ),
        ) { entry ->
            EditQuestionScreen(
                setId = entry.arguments?.getLong("setId") ?: return@composable,
                questionId = entry.arguments?.getLong("questionId") ?: -1L,
                onBack = { nav.popBackStack() },
            )
        }

        composable(
            "import/{setId}",
            arguments = listOf(navArgument("setId") { type = NavType.LongType }),
        ) { entry ->
            ImportScreen(
                setId = entry.arguments?.getLong("setId") ?: return@composable,
                onBack = { nav.popBackStack() },
            )
        }

        composable(
            "quiz?mode={mode}&setId={setId}&shuffle={shuffle}&limit={limit}",
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType; defaultValue = QuizMode.ALL.name },
                navArgument("setId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("shuffle") { type = NavType.BoolType; defaultValue = true },
                navArgument("limit") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) { entry ->
            val args = entry.arguments
            QuizScreen(
                mode = QuizMode.valueOf(args?.getString("mode") ?: QuizMode.ALL.name),
                setId = args?.getLong("setId") ?: -1L,
                shuffleQuestions = args?.getBoolean("shuffle") ?: true,
                limit = args?.getInt("limit") ?: 0,
                onBack = { nav.popBackStack() },
            )
        }

        composable("wrong") {
            WrongBookScreen(
                onBack = { nav.popBackStack() },
                onPracticeAll = { shuffle, limit -> nav.navigate(quizRoute(QuizMode.WRONG_ALL, -1L, shuffle, limit)) },
            )
        }

        composable("history") {
            HistoryScreen(
                onBack = { nav.popBackStack() },
                onOpenSession = { nav.navigate("session/$it") },
            )
        }

        composable(
            "session/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) { entry ->
            SessionDetailScreen(
                sessionId = entry.arguments?.getLong("sessionId") ?: return@composable,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
