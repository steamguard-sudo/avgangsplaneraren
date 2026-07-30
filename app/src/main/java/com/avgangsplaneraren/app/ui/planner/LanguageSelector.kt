package com.avgangsplaneraren.app.ui.planner

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.avgangsplaneraren.app.R

private data class LanguageOption(val flag: String, val tag: String, val labelRes: Int)

private val LANGUAGE_OPTIONS = listOf(
    LanguageOption("\uD83C\uDDF8\uD83C\uDDEA", "sv", R.string.language_swedish), // 🇸🇪
    LanguageOption("\uD83C\uDDEC\uD83C\uDDE7", "en", R.string.language_english), // 🇬🇧
    LanguageOption("\uD83C\uDDE9\uD83C\uDDEA", "de", R.string.language_german)   // 🇩🇪
)

/**
 * Språkväljare med tre flaggor (svenska/engelska/tyska). Byter appens språk
 * direkt via [AppCompatDelegate.setApplicationLocales] — Android sköter
 * sedan om att spara valet automatiskt (kräver ingen egen lagring från oss)
 * och laddar om skärmen med rätt språk.
 *
 * Kräver `androidx.appcompat:appcompat` (se app/build.gradle.kts). Fungerar
 * oavsett om Activity ärver från AppCompatActivity eller inte.
 */
@Composable
fun LanguageSelector(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val currentTag = AppCompatDelegate.getApplicationLocales()
        .takeIf { !it.isEmpty }
        ?.get(0)
        ?.language
        ?: "sv"

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        LANGUAGE_OPTIONS.forEach { option ->
            val isSelected = option.tag == currentTag
            Text(
                text = option.flag,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .clip(CircleShape)
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape
                    )
                    .padding(4.dp)
                    .clickable {
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(option.tag)
                        )
                        (context as? Activity)?.recreate()
                    }
            )
        }
    }
}
