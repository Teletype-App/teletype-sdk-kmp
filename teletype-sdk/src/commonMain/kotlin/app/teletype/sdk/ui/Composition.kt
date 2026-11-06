package app.teletype.sdk.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import app.teletype.sdk.core.TeletypeClient
import app.teletype.sdk.core.TeletypeConfig
import app.teletype.sdk.core.TeletypeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

val LocalTeletypeClient = staticCompositionLocalOf<TeletypeClient> {
    error("TeletypeClient is not provided. Wrap your UI in TeletypeProvider.")
}

@Composable
fun TeletypeProvider(
    client: TeletypeClient,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalTeletypeClient provides client) {
        content()
    }
}

@Composable
fun TeletypeProvider(
    config: TeletypeConfig,
    content: @Composable () -> Unit,
) {
    val client = remember(config.teletypeExternalId) {
        TeletypeClient(config).also {
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { it.init() }
        }
    }
    TeletypeProvider(client = client, content = content)
}

@Composable
fun rememberTeletypeClient(): TeletypeClient = LocalTeletypeClient.current

@Composable
fun rememberTeletypeState(): TeletypeState {
    val client = rememberTeletypeClient()
    val state by client.stateFlow.collectAsState()
    return state
}
