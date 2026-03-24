package com.fieldtag.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fieldtag.ui.auth.AuthViewModel
import com.fieldtag.ui.auth.SignInScreen
import com.fieldtag.ui.calibrate.DiagramCalibrateScreen
import com.fieldtag.ui.camera.CameraScreen
import com.fieldtag.ui.export.ExportScreen
import com.fieldtag.ui.instrument.InstrumentDetailScreen
import com.fieldtag.ui.pid.PidGridScreen
import com.fieldtag.ui.pid.PidImportScreen
import com.fieldtag.ui.pid.TagReviewScreen
import com.fieldtag.ui.projects.ProjectDetailScreen
import com.fieldtag.ui.projects.ProjectListScreen
import com.fieldtag.ui.welcome.WelcomeScreen
import androidx.hilt.navigation.compose.hiltViewModel

object Routes {
    const val WELCOME = "welcome"
    const val PROJECT_LIST = "projects"
    const val PROJECT_DETAIL = "projects/{projectId}"
    const val PID_IMPORT = "projects/{projectId}/import"
    /** Instrument-size calibration. Optional `page` query param selects the starting page (0-based). */
    const val CALIBRATE = "projects/{projectId}/calibrate/{pidDocumentId}?page={pageNumber}"
    /** Grid-based manual tag identification — kept but no longer in the main flow. */
    const val PID_GRID = "projects/{projectId}/grid/{pidDocumentId}"
    /** Kept for reference; no longer reachable via normal navigation. */
    const val TAG_REVIEW = "projects/{projectId}/review"
    const val INSTRUMENT_DETAIL = "instruments/{instrumentId}"
    const val CAMERA = "camera/{projectId}/{instrumentId}"
    const val EXPORT = "projects/{projectId}/export"
    const val SIGN_IN = "sign_in?fromWelcome={fromWelcome}"

    fun projectDetail(projectId: String) = "projects/$projectId"
    fun pidImport(projectId: String) = "projects/$projectId/import"
    fun calibrate(projectId: String, pidDocumentId: String, pageNumber: Int = 0) =
        "projects/$projectId/calibrate/$pidDocumentId?page=$pageNumber"
    fun pidGrid(projectId: String, pidDocumentId: String) = "projects/$projectId/grid/$pidDocumentId"
    fun tagReview(projectId: String) = "projects/$projectId/review"
    fun instrumentDetail(instrumentId: String) = "instruments/$instrumentId"
    fun camera(projectId: String, instrumentId: String) = "camera/$projectId/$instrumentId"
    fun export(projectId: String) = "projects/$projectId/export"
    fun signIn(fromWelcome: Boolean) = "sign_in?fromWelcome=$fromWelcome"
}

@Composable
fun AppNavGraph(
    startDestination: String,
    navController: NavHostController = rememberNavController(),
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authSession by authViewModel.session.collectAsState()

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.WELCOME) {
            WelcomeScreen(
                onContinueWithoutAccount = {
                    navController.navigate(Routes.PROJECT_LIST) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
                onSignIn = { navController.navigate(Routes.signIn(fromWelcome = true)) },
            )
        }

        composable(Routes.PROJECT_LIST) {
            ProjectListScreen(
                authSession = authSession,
                onProjectClick = { projectId ->
                    navController.navigate(Routes.projectDetail(projectId))
                },
                onOpenSignIn = { navController.navigate(Routes.signIn(fromWelcome = false)) },
                onSignOut = { authViewModel.signOut() },
            )
        }

        composable(
            route = Routes.SIGN_IN,
            arguments = listOf(
                navArgument("fromWelcome") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            val fromWelcome = backStackEntry.arguments?.getBoolean("fromWelcome") ?: false
            SignInScreen(
                fromWelcomeFlow = fromWelcome,
                onBack = { navController.popBackStack() },
                onSignedInFromWelcome = {
                    navController.navigate(Routes.PROJECT_LIST) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
                onSignedInFromApp = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.PROJECT_DETAIL,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            ProjectDetailScreen(
                projectId = projectId,
                authSession = authSession,
                onBack = { navController.popBackStack() },
                onImportPid = { navController.navigate(Routes.pidImport(projectId)) },
                onInstrumentClick = { instrId -> navController.navigate(Routes.instrumentDetail(instrId)) },
                onExport = { navController.navigate(Routes.export(projectId)) },
                onRecalibrate = { pidDocumentId, pageNumber ->
                    navController.navigate(Routes.calibrate(projectId, pidDocumentId, pageNumber))
                },
                onNavigateToSignIn = { navController.navigate(Routes.signIn(fromWelcome = false)) },
            )
        }

        composable(
            route = Routes.PID_IMPORT,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            PidImportScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                onParseComplete = { pidDocumentId ->
                    navController.navigate(Routes.calibrate(projectId, pidDocumentId)) {
                        popUpTo(Routes.PROJECT_LIST)
                    }
                },
            )
        }

        composable(
            route = Routes.CALIBRATE,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("pidDocumentId") { type = NavType.StringType },
                navArgument("pageNumber") {
                    type = NavType.IntType
                    defaultValue = 0
                },
            ),
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            val pidDocumentId = backStackEntry.arguments?.getString("pidDocumentId") ?: return@composable
            DiagramCalibrateScreen(
                pidDocumentId = pidDocumentId,
                onBack = { navController.popBackStack() },
                onCalibrated = {
                    navController.navigate(Routes.projectDetail(projectId)) {
                        popUpTo(Routes.PROJECT_LIST)
                    }
                },
            )
        }

        composable(
            route = Routes.PID_GRID,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("pidDocumentId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            val pidDocumentId = backStackEntry.arguments?.getString("pidDocumentId") ?: return@composable
            PidGridScreen(
                projectId = projectId,
                pidDocumentId = pidDocumentId,
                onBack = {
                    navController.navigate(Routes.projectDetail(projectId)) {
                        popUpTo(Routes.PROJECT_LIST)
                    }
                },
                onInstrumentReady = { instrumentId ->
                    navController.navigate(Routes.instrumentDetail(instrumentId))
                },
            )
        }

        composable(
            route = Routes.TAG_REVIEW,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            TagReviewScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                onConfirm = {
                    navController.navigate(Routes.projectDetail(projectId)) {
                        popUpTo(Routes.PROJECT_LIST)
                    }
                },
            )
        }

        composable(
            route = Routes.INSTRUMENT_DETAIL,
            arguments = listOf(navArgument("instrumentId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val instrumentId = backStackEntry.arguments?.getString("instrumentId") ?: return@composable
            InstrumentDetailScreen(
                instrumentId = instrumentId,
                onBack = { navController.popBackStack() },
                onOpenCamera = { instrId ->
                    navController.navigate(Routes.camera("unknown", instrId))
                },
            )
        }

        composable(
            route = Routes.CAMERA,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("instrumentId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            val instrumentId = backStackEntry.arguments?.getString("instrumentId") ?: return@composable
            CameraScreen(
                instrumentId = instrumentId,
                projectId = projectId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.EXPORT,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            ExportScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
