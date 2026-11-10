package com.beemind.personal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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

@Composable
fun EmptyNotes(onCreate: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(BeeDesign.rainbow, BeeDesign.panel).padding(28.dp),
        horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.empty_body), style = MaterialTheme.typography.bodyLarge,
            color = BeeDesign.ink)
        Button(onClick = onCreate, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.write_first))
        }
    }
}
