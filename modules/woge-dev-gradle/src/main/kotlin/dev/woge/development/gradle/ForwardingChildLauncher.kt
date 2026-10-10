package dev.woge.development.gradle

import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.process.ChildLaunchSpec
import dev.woge.development.process.ChildLauncher
import dev.woge.development.process.ManagedChild
import dev.woge.development.process.ProcessChildLauncher

/** Shows the application's own log in the `wogeDev` terminal, just like `bootRun` does. */
@ExperimentalWogeDevelopmentApi
internal class ForwardingChildLauncher(
    private val print: (String) -> Unit,
    private val delegate: ChildLauncher = ProcessChildLauncher,
) : ChildLauncher {
    override fun launch(
        spec: ChildLaunchSpec,
        onLine: (String) -> Unit,
        onExit: (Int) -> Unit,
    ): ManagedChild =
        delegate.launch(
            spec,
            { line ->
                onLine(line)
                if (!line.startsWith(INTERNAL_PREFIX)) print(line)
            },
            onExit,
        )

    private companion object {
        const val INTERNAL_PREFIX = "WOGE-DEV-READY "
    }
}
