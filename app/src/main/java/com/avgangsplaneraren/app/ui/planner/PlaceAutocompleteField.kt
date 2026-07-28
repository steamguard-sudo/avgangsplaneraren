package com.avgangsplaneraren.app.ui.planner

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avgangsplaneraren.app.domain.Coordinates
import com.avgangsplaneraren.app.domain.PlaceProvider
import com.avgangsplaneraren.app.domain.PlaceSuggestion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fritextfält för att söka fram en plats (stad, adress, ort — vad som helst
 * Google Places känner till), med förslag som dyker upp medan man skriver.
 *
 * Söker inte vid varje tangenttryckning — väntar [debounceMillis] efter
 * senaste tryckningen innan den frågar backend, så att en snabb skrivare
 * inte skickar ett anrop per bokstav. När ett förslag väljs slås dess
 * koordinat upp i bakgrunden, och [onPlaceSelected] anropas när den är klar.
 *
 * Har en rensa-knapp (✕) när fältet innehåller text, så det är enkelt att
 * skriva om — t.ex. om man valde fel ort bland flera med samma namn
 * (Google Places förslagstexten innehåller normalt kommun/län för att
 * skilja dem åt, men det är ändå smidigt att kunna börja om direkt).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceAutocompleteField(
    label: String,
    placeProvider: PlaceProvider,
    onPlaceSelected: (name: String, coordinates: Coordinates) -> Unit,
    modifier: Modifier = Modifier,
    debounceMillis: Long = 350,
    onCleared: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()

    var text by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var isResolving by remember { mutableStateOf(false) }
    var hasSelectedPlace by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Sök om `text` när den ändras, men vänta debounceMillis efter sista
    // tangenttryckningen. Om texten ändras igen innan dess avbryts detta
    // varv automatiskt, eftersom LaunchedEffect med `text` som nyckel
    // startar om från början vid varje ny bokstav.
    LaunchedEffect(text) {
        if (hasSelectedPlace) {
            // Vi satte texten själva (användaren valde ett förslag) — inget
            // nytt sök behövs för just den ändringen.
            return@LaunchedEffect
        }
        if (text.length < 2) {
            suggestions = emptyList()
            expanded = false
            return@LaunchedEffect
        }
        delay(debounceMillis)
        isSearching = true
        errorMessage = null
        try {
            suggestions = placeProvider.autocomplete(text)
            expanded = suggestions.isNotEmpty()
        } catch (e: Exception) {
            suggestions = emptyList()
            errorMessage = "Kunde inte söka just nu"
        } finally {
            isSearching = false
        }
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                hasSelectedPlace = false
                text = it
            },
            label = { Text(label) },
            singleLine = true,
            isError = errorMessage != null,
            supportingText = errorMessage?.let { { Text(it) } },
            trailingIcon = {
                when {
                    isSearching || isResolving -> {
                        CircularProgressIndicator(modifier = Modifier.width(16.dp), strokeWidth = 2.dp)
                    }
                    text.isNotEmpty() -> {
                        IconButton(onClick = {
                            text = ""
                            suggestions = emptyList()
                            expanded = false
                            hasSelectedPlace = false
                            errorMessage = null
                            onCleared()
                        }) {
                            Text("✕", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            },
            modifier = modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion.description) },
                    onClick = {
                        hasSelectedPlace = true
                        text = suggestion.description
                        expanded = false
                        errorMessage = null
                        coroutineScope.launch {
                            isResolving = true
                            try {
                                val coordinates = placeProvider.details(suggestion.placeId)
                                onPlaceSelected(suggestion.description, coordinates)
                            } catch (e: Exception) {
                                errorMessage = "Kunde inte hämta platsen"
                            } finally {
                                isResolving = false
                            }
                        }
                    }
                )
            }
        }
    }
}
