package org.example.project

import androidx.compose.runtime.Composable

private val POLICY_BODY = """
Privacy Policy
Last updated: January 2025

1. Data We Collect
- Account information (username, password hash)
- Health profile data (age, gender, activity level, medical history)
- Symptom logs (pain, fatigue, stiffness levels)
- ROM assessment results from RA Lens sessions

2. How We Use Your Data
- To generate personalized flare risk estimates
- To display longitudinal health trends
- To power RA prediction analytics

3. Data Storage
Your data is stored securely via Supabase with row-level security. Only you can access your own data.

4. Data Sharing
We do not sell, share, or transfer your data to any third party.

5. Your Rights
You may request deletion of your account and all associated data at any time by contacting support.

6. Contact
For privacy concerns: arthocareapp@gmail.com
""".trimIndent()

@Composable
fun PrivacyPolicyScreen(onNavigateBack: () -> Unit) {
    LegalDocumentLayout(
        title = "Privacy Policy",
        body = POLICY_BODY,
        onNavigateBack = onNavigateBack
    )
}
