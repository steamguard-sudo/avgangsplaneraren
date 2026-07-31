package com.avgangsplaneraren.app.ui.planner

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.avgangsplaneraren.app.ui.stringResource
import androidx.compose.ui.unit.dp
import com.avgangsplaneraren.app.R
import com.avgangsplaneraren.app.domain.Coordinates
import com.avgangsplaneraren.app.domain.PlaceProvider
import com.avgangsplaneraren.app.domain.PlaceSuggestion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceAutocompleteField(
    label: String,
    placeProvider: PlaceProvider,
    onPlaceSelected: (name: String, coordinates: Coordinates) -> Unit,
    modifier: Modifier = Modifier,
    debounceMillis: Long = 350,
    initialValue: String = "",
    onCleared: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()

    var text by remember { mutableStateOf(initialValue) }
    var suggestions by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var isResolving by remember { mutableStateOf(false) }
    var hasSelectedPlace by remember { mutableStateOf(initialValue.isNotEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val searchFailedText = stringResource(R.string.error_search_failed)
    val fetchPlaceFailedText = stringResource(R.string.error_fetch_place_failed)

    LaunchedEffect(text) {
        if (hasSelectedPlace) {
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
            errorMessage = searchFailedText
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
                                errorMessage = fetchPlaceFailedText
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
