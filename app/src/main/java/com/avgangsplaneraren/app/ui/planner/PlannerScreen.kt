package com.avgangsplaneraren.app.ui.planner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.avgangsplaneraren.app.data.directions.AppConfig
import com.avgangsplaneraren.app.data.directions.GoogleRoutesRepository
import com.avgangsplaneraren.app.data.directions.RouteEstimator
import com.avgangsplaneraren.app.data.directions.SampleCities
import com.avgangsplaneraren.app.data.trafikverket.TrafikverketDataSeeder
import com.avgangsplaneraren.app.data.trafikverket.TrafikverketDatabase
import com.avgangsplaneraren.app.data.trafikverket.TrafikverketRestStopRepository
import com.avgangsplaneraren.app.domain.CalculateDeparture
import com.avgangsplaneraren.app.domain.DepartureResult
import com.avgangsplaneraren.app.domain.TripInput
import com.avgangsplaneraren.app.ui.board.DepartureBoard
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Enkel skärm som kopplar ihop formuläret med domänlogiken.
 * Detta är avsiktligt minimalt (ingen ViewModel/Hilt ännu) – tanken är att
 * ni bygger vidare med riktig arkitektur (MVVM + Hilt) när ni fyller på
 * med fler skärmar.
 *
 * Ruttdata hämtas nu via [GoogleRoutesRepository], som anropar ert eget
 * backend (backend/server.js) — vilket i sin tur anropar Google Routes API
 * och cachar svaret. Om anropet misslyckas (backend inte igång, ingen
 * nätanslutning) faller det automatiskt tillbaka på [RouteEstimator], så
 * appen alltid ger ett svar.
 */
@Composable
fun PlannerScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val cityNames = remember { SampleCities.all.keys.toList() }
    var from by remember { mutableStateOf(cityNames.first()) }
    var to by remember { mutableStateOf(cityNames[1]) }
    var bufferMinutes by remember { mutableStateOf(10) }
    var onlyAmenities by remember { mutableStateOf(false) }
    var arrival by remember { mutableStateOf(LocalDateTime.now().plusHours(6)) }
    var result by remember { mutableStateOf<DepartureResult?>(null) }
    var isSeeding by remember { mutableStateOf(true) }
    var isCalculating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val routeProvider = remember {
        GoogleRoutesRepository(baseUrl = AppConfig.BACKEND_BASE_URL, fallback = RouteEstimator())
    }
    val database = remember { TrafikverketDatabase.getInstance(context) }
    val restStopRepository = remember { TrafikverketRestStopRepository(database.restAreaDao()) }
    val calculator = remember { CalculateDeparture(restStopProvider = restStopRepository) }

    // Fyller databasen från assets/rastplatser.json första gången appen körs.
    LaunchedEffect(Unit) {
        TrafikverketDataSeeder.seedIfNeeded(context, database.restAreaDao())
        isSeeding = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Avgångsplaneraren", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Ange avresa, mål och önskad ankomsttid.",
            style = MaterialTheme.typography.bodyMedium
        )

        CityDropdown(label = "Avreseplats", selected = from, options = cityNames) { from = it }
        CityDropdown(label = "Slutmål", selected = to, options = cityNames) { to = it }

        ArrivalDateTimePicker(value = arrival, onValueChange = { arrival = it })

        OutlinedTextField(
            value = bufferMinutes.toString(),
            onValueChange = { bufferMinutes = it.toIntOrNull() ?: 0 },
            label = { Text("Buffert innan avresa (minuter)") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = onlyAmenities, onCheckedChange = { onlyAmenities = it })
            Text("Endast rastplatser med bord & bänk")
        }

        Button(
            enabled = !isSeeding && !isCalculating,
            onClick = {
                errorMessage = null
                isCalculating = true
                coroutineScope.launch {
                    try {
                        val fromCoord = SampleCities.all.getValue(from)
                        val toCoord = SampleCities.all.getValue(to)
                        val route = routeProvider.getRoute(fromCoord, toCoord)

                        val trip = TripInput(
                            fromPlace = from,
                            toPlace = to,
                            desiredArrival = arrival,
                            bufferMinutes = bufferMinutes,
                            onlyStopsWithTableAndBench = onlyAmenities
                        )
                        result = calculator.calculate(trip, route)
                    } catch (e: Exception) {
                        errorMessage = "Kunde inte beräkna resan: ${e.message}"
                    } finally {
                        isCalculating = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    isSeeding -> "Laddar rastplatsdata …"
                    isCalculating -> "Beräknar …"
                    else -> "Beräkna avgångstid"
                }
            )
        }

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        result?.let { DepartureBoard(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CityDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
