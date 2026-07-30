package com.avgangsplaneraren.app.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
fun LocalizedContent(languageTag: String, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current

    val localizedContext = remember(languageTag) {
        val locale = Locale(languageTag)
        val configuration = Configuration(baseContext.resources.configuration)
        configuration.setLocale(locale)
        baseContext.createConfigurationContext(configuration)
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration
    ) {
        content()
    }
}
