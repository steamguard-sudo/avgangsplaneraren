package com.avgangsplaneraren.app.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import com.avgangsplaneraren.app.AppLanguageState
import java.util.Locale

fun Context.withLocale(languageTag: String): Context {
    val locale = Locale(languageTag)
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    return createConfigurationContext(configuration)
}

@Composable
@ReadOnlyComposable
private fun localizedAppContext(): Context {
    val baseContext = LocalContext.current
    val languageTag = AppLanguageState.current.value
    return baseContext.withLocale(languageTag)
}

@Composable
@ReadOnlyComposable
fun stringResource(id: Int): String {
    return localizedAppContext().getString(id)
}

@Composable
@ReadOnlyComposable
fun stringResource(id: Int, vararg formatArgs: Any): String {
    return localizedAppContext().getString(id, *formatArgs)
}
