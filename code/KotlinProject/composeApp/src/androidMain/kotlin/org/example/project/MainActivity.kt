package org.example.project
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RaLensDebugAndroidContext.appContext = applicationContext
        setContent {
            // This line calls your App() function from commonMain
            App()
        }
    }
}
