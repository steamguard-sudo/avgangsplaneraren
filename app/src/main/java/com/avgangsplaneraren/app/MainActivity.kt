package com.avgangsplaneraren.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.avgangsplaneraren.app.ui.LocalizedContent
import com.avgangsplaneraren.app.ui.planner.PlannerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLanguageState.load(this)
        setContent {
            AppRoot()
        }
    }
}

@Composable
private fun AppRoot() {
    val languageTag by AppLanguageState.current
    MaterialTheme {
        Surface(modifier = Modifier) {
            LocalizedContent(languageTag = languageTag) {
                PlannerScreen()
            }
        }
    }
}
