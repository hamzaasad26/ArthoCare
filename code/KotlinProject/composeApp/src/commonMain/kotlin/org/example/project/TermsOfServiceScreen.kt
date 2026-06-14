package org.example.project

import androidx.compose.runtime.Composable

private val TERMS_BODY = """
Terms of Service
Last updated: January 2025

ArthoCare is provided as a Final Year Project for academic and demonstrative purposes.

1. Acceptance
By using ArthoCare you agree to these terms. If you do not agree, do not use the app.

2. Medical Disclaimer
ArthoCare is NOT a medical device and does NOT provide medical advice. All ROM measurements, flare risk estimates, and health insights are for informational purposes only. Always consult a qualified healthcare professional for medical decisions.

3. Data Use
Data you enter is used solely to provide app functionality. It is not shared with third parties or used for commercial purposes.

4. Limitation of Liability
ArthoCare and its developers are not liable for any health decisions made based on app output.

5. Changes
These terms may be updated. Continued use of the app constitutes acceptance of updated terms.
""".trimIndent()

@Composable
fun TermsOfServiceScreen(onNavigateBack: () -> Unit) {
    LegalDocumentLayout(
        title = "Terms of Service",
        body = TERMS_BODY,
        onNavigateBack = onNavigateBack
    )
}
