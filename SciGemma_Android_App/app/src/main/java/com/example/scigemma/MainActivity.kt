package com.example.scigemma

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.scigemma.architecture.ArchitectureType
import com.example.scigemma.ui.ArchitectureSelectionScreen
import com.example.scigemma.ui.BenchmarkResultsScreen
import com.example.scigemma.ui.BenchmarkScreen
import com.example.scigemma.ui.BenchmarkViewModel
import com.example.scigemma.ui.theme.SciGemmaTheme

// ---- Route constants ----
private const val ROUTE_ARCH_SELECTION  = "arch_selection"
private const val ROUTE_LOADING         = "loading/{archType}"
private const val ROUTE_CHAT            = "chat/{archType}"
private const val ROUTE_BENCHMARK       = "benchmark"
private const val ROUTE_BENCHMARK_RESULTS = "benchmark_results"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SciGemmaTheme {
                AppScaffold()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: ""

    // Derive top bar title from current route
    val title = when {
        currentRoute == ROUTE_ARCH_SELECTION        -> "MindChat – Architecture"
        currentRoute == ROUTE_BENCHMARK             -> "MindChat – Benchmark"
        currentRoute == ROUTE_BENCHMARK_RESULTS     -> "MindChat – Results"
        currentRoute.startsWith("chat/")            -> "MindChat – Chat"
        currentRoute.startsWith("loading/")         -> "MindChat – Loading"
        else                                        -> "MindChat"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor   = Color(0xFF6A1B9A),
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = Color.White
        ) {
            // Shared BenchmarkViewModel scoped to the nav graph
            val benchmarkVm: BenchmarkViewModel = viewModel(
                factory = BenchmarkViewModel.factory(
                    androidx.compose.ui.platform.LocalContext.current
                )
            )

            NavHost(
                navController  = navController,
                startDestination = ROUTE_ARCH_SELECTION
            ) {

                // ---- Architecture selection ----
                composable(ROUTE_ARCH_SELECTION) {
                    ArchitectureSelectionScreen(
                        onArchitectureSelected = { archType ->
                            navController.navigate("loading/${archType.name}")
                        },
                        onRunBenchmark = {
                            navController.navigate(ROUTE_BENCHMARK)
                        }
                    )
                }

                // ---- Model loading ----
                composable(
                    route     = ROUTE_LOADING,
                    arguments = listOf(navArgument("archType") { type = NavType.StringType })
                ) { backEntry ->
                    val archTypeName = backEntry.arguments?.getString("archType")
                        ?: ArchitectureType.SEPARATE.name
                    val archType = ArchitectureType.valueOf(archTypeName)

                    LoadingRoute(
                        archType      = archType,
                        onModelLoaded = {
                            navController.navigate("chat/${archType.name}") {
                                popUpTo("loading/${archType.name}") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }

                // ---- Chat screen ----
                composable(
                    route     = ROUTE_CHAT,
                    arguments = listOf(navArgument("archType") { type = NavType.StringType })
                ) { backEntry ->
                    val archTypeName = backEntry.arguments?.getString("archType")
                        ?: ArchitectureType.SEPARATE.name
                    val archType = ArchitectureType.valueOf(archTypeName)
                    ChatRoute(archType = archType)
                }

                // ---- Benchmark runner ----
                composable(ROUTE_BENCHMARK) {
                    BenchmarkScreen(
                        vm            = benchmarkVm,
                        onResultsReady = {
                            navController.navigate(ROUTE_BENCHMARK_RESULTS) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                // ---- Benchmark results ----
                composable(ROUTE_BENCHMARK_RESULTS) {
                    val summaries = benchmarkVm.summaries.value
                    val saveDir   = benchmarkVm.saveDir.value

                    if (summaries.isEmpty()) {
                        Text("No results yet. Run the benchmark first.")
                    } else {
                        BenchmarkResultsScreen(
                            summaries = summaries,
                            saveDir   = saveDir,
                            onBack    = {
                                navController.navigate(ROUTE_ARCH_SELECTION) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
