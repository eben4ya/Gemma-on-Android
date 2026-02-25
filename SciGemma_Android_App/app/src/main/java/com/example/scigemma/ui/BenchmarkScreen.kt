package com.example.scigemma.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scigemma.architecture.ArchitectureType
import com.example.scigemma.architecture.MultitaskArchitecture
import com.example.scigemma.architecture.PipelineArchitecture
import com.example.scigemma.architecture.SeparateArchitecture
import com.example.scigemma.benchmark.ArchitectureSummary
import com.example.scigemma.benchmark.BenchmarkResultStore
import com.example.scigemma.benchmark.BenchmarkRunner
import com.example.scigemma.benchmark.BenchmarkSample
import com.example.scigemma.benchmark.SyntheticDataset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val Purple = Color(0xFF6A1B9A)

// ---- ViewModel ----

class BenchmarkViewModel(private val context: Context) : ViewModel() {

    enum class State { IDLE, RUNNING, DONE, ERROR }

    private val _state    = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress.asStateFlow()

    private val _progressFraction = MutableStateFlow(0f)
    val progressFraction: StateFlow<Float> = _progressFraction.asStateFlow()

    private val _summaries = MutableStateFlow<List<ArchitectureSummary>>(emptyList())
    val summaries: StateFlow<List<ArchitectureSummary>> = _summaries.asStateFlow()

    private val _saveDir = MutableStateFlow("")
    val saveDir: StateFlow<String> = _saveDir.asStateFlow()

    private val _error = MutableStateFlow("")
    val error: StateFlow<String> = _error.asStateFlow()

    private val allSamples = mutableMapOf<String, List<BenchmarkSample>>()
    private val TOTAL_STEPS = 3  // one per architecture

    fun runBenchmark(quickMode: Boolean = false) {
        if (_state.value == State.RUNNING) return
        _state.value = State.RUNNING
        allSamples.clear()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dataset = if (quickMode) SyntheticDataset.QUICK else SyntheticDataset.ALL
                val runner  = BenchmarkRunner(context)
                val summaryList = mutableListOf<ArchitectureSummary>()
                var step = 0

                val architectures = listOf(
                    MultitaskArchitecture(),
                    SeparateArchitecture(),
                    PipelineArchitecture()
                )

                runner.onProgress = { msg ->
                    _progress.value = msg
                }

                for (arch in architectures) {
                    val (samples, summary) = runner.run(arch, dataset)
                    allSamples[arch.type.displayName] = samples
                    summaryList.add(summary)
                    step++
                    _progressFraction.value = step.toFloat() / TOTAL_STEPS
                }

                _summaries.value = summaryList

                // Save results
                val saved = BenchmarkResultStore.save(context, allSamples, summaryList)
                _saveDir.value = saved.directory

                _state.value = State.DONE

            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
                _state.value = State.ERROR
            }
        }
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                BenchmarkViewModel(context.applicationContext) as T
        }
    }
}

// ---- Composable ----

@Composable
fun BenchmarkScreen(
    onResultsReady: () -> Unit,
    vm: BenchmarkViewModel = viewModel(
        factory = BenchmarkViewModel.factory(LocalContext.current)
    )
) {
    val state           by vm.state.collectAsStateWithLifecycle()
    val progress        by vm.progress.collectAsStateWithLifecycle()
    val progressFraction by vm.progressFraction.collectAsStateWithLifecycle()
    val error           by vm.error.collectAsStateWithLifecycle()

    // Navigate when done
    if (state == BenchmarkViewModel.State.DONE) {
        onResultsReady()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text       = "Architecture Benchmark",
            style      = MaterialTheme.typography.headlineSmall,
            color      = Purple,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        when (state) {
            BenchmarkViewModel.State.IDLE -> {
                Text(
                    text  = "Runs all 3 architectures (Multitask, Separate, Pipeline)\n" +
                            "through the 20-sample synthetic dataset.\n\n" +
                            "This will take several minutes on the device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF424242)
                )
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick  = { vm.runBenchmark(quickMode = false) },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = Purple)
                ) { Text("Run Full Benchmark (20 samples)", color = Color.White) }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick  = { vm.runBenchmark(quickMode = true) },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF9575CD))
                ) { Text("Quick Benchmark (10 samples)", color = Color.White) }
            }

            BenchmarkViewModel.State.RUNNING -> {
                CircularProgressIndicator(color = Purple)
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier.fillMaxWidth(),
                    color    = Purple
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text  = progress,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF424242)
                )
            }

            BenchmarkViewModel.State.ERROR -> {
                Text(
                    text  = "Error: $error",
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { vm.runBenchmark() },
                    colors  = ButtonDefaults.buttonColors(containerColor = Purple)
                ) { Text("Retry", color = Color.White) }
            }

            BenchmarkViewModel.State.DONE -> {
                // Navigation triggered above
                CircularProgressIndicator(color = Purple)
                Text("Loading results…", color = Purple)
            }
        }
    }
}
