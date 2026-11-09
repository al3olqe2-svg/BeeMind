package com.beemind.personal.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemind.personal.R

@Composable
fun WelcomeScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding().fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(stringResource(R.string.foundation_label), color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge)
                Box(
                    modifier = Modifier.clearAndSetSemantics { }.size(152.dp).clip(RoundedCornerShape(48.dp))
                        .background(Brush.linearGradient(listOf(
                            Color(0xFFF8DCE7), Color(0xFFF8EBCF), Color(0xFFDDEEDC),
                            Color(0xFFD6EAF7), Color(0xFFE5D8F6),
                        ))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("B", fontSize = 72.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary)
                }
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.welcome_eyebrow), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                Box(Modifier.width(64.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer))
                Text(stringResource(R.string.privacy_label), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}
