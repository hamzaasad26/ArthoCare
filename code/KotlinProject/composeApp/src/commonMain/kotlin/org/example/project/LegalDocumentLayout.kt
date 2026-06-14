@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.example.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LegalScreenBg = Color(0xFF0D0B12)

@Composable
internal fun LegalDocumentLayout(
    title: String,
    body: String,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        containerColor = LegalScreenBg,
        topBar = { FeatureTopAppBar(title = title, onNavigateBack = onNavigateBack) }
    ) { paddingValues ->
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.75f),
            modifier = Modifier
                .fillMaxSize()
                .background(LegalScreenBg)
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}
