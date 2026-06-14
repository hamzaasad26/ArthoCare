package org.example.project

import androidx.compose.runtime.Composable

private val SUPPORT_BODY = """
Contact Support

For technical issues, feedback, or data requests:

Email: arthocareapp@gmail.com

Response time: We aim to respond within 2–3 business days.

For urgent medical concerns, please contact your healthcare provider directly. ArthoCare support cannot provide medical advice.

ArthoCare — FYP 2025
""".trimIndent()

@Composable
fun ContactSupportScreen(onNavigateBack: () -> Unit) {
    LegalDocumentLayout(
        title = "Contact Support",
        body = SUPPORT_BODY,
        onNavigateBack = onNavigateBack
    )
}
