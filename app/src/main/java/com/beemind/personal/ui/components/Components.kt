package com.beemind.personal.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.beemind.personal.R
import com.beemind.personal.ui.theme.BeeDesign

@Composable
fun BeeBack(onBack: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onBack, enabled = enabled) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
    }
}

@Composable
fun ErrorPanel(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Surface(modifier = modifier.fillMaxWidth(), shape = BeeDesign.card,
        color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium)
            if (onRetry != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}
