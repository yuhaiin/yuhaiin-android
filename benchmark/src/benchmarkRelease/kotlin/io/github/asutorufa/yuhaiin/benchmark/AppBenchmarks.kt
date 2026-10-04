package io.github.asutorufa.yuhaiin.benchmark

import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

class AppBenchmarks {
    @get:Rule val benchmark = MacrobenchmarkRule()

    @Test fun coldStartupWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun coldStartupWithProfile() =
        startup(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun startup(compilation: CompilationMode) =
        benchmark.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilation,
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = { pressHome() },
            measureBlock = { startActivityAndWait() },
        )

    @Test
    fun navigateSearchAndScroll() =
        benchmark.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 5,
            setupBlock = {
                killProcess()
                pressHome()
                startActivityAndWait()
            },
            measureBlock = { device.appJourney() },
        )
}
