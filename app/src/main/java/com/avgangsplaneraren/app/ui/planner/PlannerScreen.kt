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
import com.avgangsplaneraren.app.data.directions.GooglePlacesRepository
import com.avgangsplaneraren.app.data.directions.GoogleRoutesRepository
import com.avgangsplaneraren.app.data.directions.RouteEstimator
import com.avgangsplaneraren.app.data.osm.OverpassOvernightRepository
import com.avgangsplaneraren.app.data.trafikverket.TrafikverketDataSeeder
import com.avgangsplaneraren.app.data.trafikverket.TrafikverketDatabase
import com.avgangsplaneraren.app.data.trafikverket.TrafikverketRestStopRepository
import com.avgangsplaneraren.app.domain.CalculateDeparture
import com.avgangsplaneraren.app.domain.Coordinates
import com.avgangsplaneraren.app.domain.DepartureResult
import com.avgangsplaneraren.app.domain.OvernightSpot
import com.avgangsplaneraren.app.domain.OvernightSpotProvider
import com.avgangsplaneraren.app.domain.OvernightSpotType
import com.avgangsplaneraren.app.domain.RouteInfo
import com.avgangsplaneraren.app.domain.TripInput
import com.avgangsplaneraren.app.ui.board.DepartureBoard
import com.avgangsplaneraren.app.ui.board.OvernightSpotsSection
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Enkel skärm som kopplar ihop formuläret med domänlogiken.
 * Detta är avsiktligt minimalt (ingen ViewModel/Hilt ännu) – tanken är att
 * ni bygger vidare med riktig arkitektur (MVVM + Hilt) när ni fyller på
 * med fler skärmar.
 *
 * Avreseplats/mål väljs via fritextsökning ([PlaceAutocompleteField], mot
 * Googles Places API via backend). Ruttdata hämtas via [GoogleRoutesRepository]
 * (Google Routes API via backend, med [RouteEstimator] som fallback om
 * anropet misslyckas). Rastplatser kommer från Trafikverket
 * ([TrafikverketRestStopRepository]), och — om användaren bockar i det —
 * övernattningsförslag från OpenStreetMap ([OverpassOvernightRepository]),
 * hämtade på tre punkter utspridda längs rutten (se [findOvernightSpotsAlongRoute]).
 */
@Composable
fun PlannerScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var fromName by remember { mutableStateOf<String?>(null) }
    var fromCoord by remember { mutableStateOf<Coordinates?>(null) }
    var toName by remember { mutableStateOf<String?>(null) }
    var toCoord by remember { mutableStateOf<Coordinates?>(null) }

    var bufferMinutes by remember { mutableStateOf(10) }
    var minutesPerBreak by remember { mutableStateOf(20) }
    var campingStopHours by remember { mutableStateOf(0) }
    var campingStopExtraMinutes by remember { mutableStateOf(0) }
    var onlyAmenities by remember { mutableStateOf(false) }
    var showOvernightSpots by remember { mutableStateOf(false) }
    var includeCaravanSites by remember { mutableStateOf(true) }
    var includeCampSites by remember { mutableStateOf(true) }
    var arrival by remember { mutableStateOf(LocalDateTime.now().plusHours(6)) }
    var result by remember { mutableStateOf<DepartureResult?>(null) }
    var overnightSpots by remember { mutableStateOf<List<OvernightSpot>>(emptyList()) }
    var isSeeding by remember { mutableStateOf(true) }
    var isCalculating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val placeProvider = remember { GooglePlacesRepository(baseUrl = AppConfig.BACKEND_BASE_URL) }
    val routeProvider = remember {
        GoogleRoutesRepository(baseUrl = AppConfig.BACKEND_BASE_URL, fallback = RouteEstimator())
    }
    val overnightProvider = remember { OverpassOvernightRepository(baseUrl = AppConfig.BACKEND_BASE_URL) }
    val database = remember { TrafikverketDatabase.getInstance(context) }
    val restStopRepository = remember { TrafikverketRestStopRepository(database.restAreaDao()) }
    val calculator = remember { CalculateDeparture(restStopProvider = restStopRepository) }

    // Fyller databasen från assets/rastplatser.json första gången appen körs.
    LaunchedEffect(Unit) {
        TrafikverketDataSeeder.seedIfNeeded(context, database.restAreaDao())
        isSeeding = false
    }

    val canCalculate = fromCoord != null && toCoord != null && !isSeeding && !isCalculating

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

        PlaceAutocompleteField(
            label = "Avreseplats",
            placeProvider = placeProvider,
            onPlaceSelected = { name, coordinates ->
                fromName = name
                fromCoord = coordinates
            },
            onCleared = {
                fromName = null
                fromCoord = null
            }
        )

        PlaceAutocompleteField(
            label = "Slutmål",
            placeProvider = placeProvider,
            onPlaceSelected = { name, coordinates ->
                toName = name
                toCoord = coordinates
            },
            onCleared = {
                toName = null
                toCoord = null
            }
        )

        ArrivalDateTimePicker(value = arrival, onValueChange = { arrival = it })

        OutlinedTextField(
            value = bufferMinutes.toString(),
            onValueChange = { bufferMinutes = it.toIntOrNull() ?: 0 },
            label = { Text("Buffert innan avresa (minuter)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = minutesPerBreak.toString(),
            onValueChange = { minutesPerBreak = it.toIntOrNull() ?: 0 },
            label = { Text("Rasttid per stopp (minuter)") },
            supportingText = { Text("Läggs till en gång per rast, ungefär var 2:a körtimme") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = onlyAmenities, onCheckedChange = { onlyAmenities = it })
            Text("Endast rastplatser med bord & bänk")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = showOvernightSpots, onCheckedChange = { showOvernightSpots = it })
            Text("Visa övernattningsmöjligheter (OpenStreetMap)")
        }

        if (showOvernightSpots) {
            Column(modifier = Modifier.padding(start = 40.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeCaravanSites, onCheckedChange = { includeCaravanSites = it })
                    Text("Husbil/husvagn", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeCampSites, onCheckedChange = { includeCampSites = it })
                    Text("Camping (även tält)", style = MaterialTheme.typography.bodySmall)
                }
                DurationPicker(
                    label = "Extra tid vid campingstopp",
                    hours = campingStopHours,
                    minutes = campingStopExtraMinutes,
                    onDurationChange = { h, m ->
                        campingStopHours = h
                        campingStopExtraMinutes = m
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "T.ex. övernattning eller matlagning. Lämna på 0 om du inte planerar ett stopp där.",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Button(
            enabled = canCalculate,
            onClick = {
                val from = fromCoord ?: return@Button
                val to = toCoord ?: return@Button
                errorMessage = null
                isCalculating = true
                overnightSpots = emptyList()
                coroutineScope.launch {
                    try {
                        val route = routeProvider.getRoute(from, to)

                        val campingStopMinutes = if (showOvernightSpots) {
                            campingStopHours * 60 + campingStopExtraMinutes
                        } else {
                            0
                        }
                        val trip = TripInput(
                            fromPlace = fromName.orEmpty(),
                            toPlace = toName.orEmpty(),
                            desiredArrival = arrival,
                            bufferMinutes = bufferMinutes,
                            onlyStopsWithTableAndBench = onlyAmenities,
                            minutesPerBreak = minutesPerBreak,
                            campingStopMinutes = campingStopMinutes
                        )
                        result = calculator.calculate(trip, route)

                        if (showOvernightSpots) {
                            val types = buildSet {
                                if (includeCaravanSites) add(OvernightSpotType.CARAVAN_SITE)
                                if (includeCampSites) add(OvernightSpotType.CAMP_SITE)
                            }
                            overnightSpots = findOvernightSpotsAlongRoute(overnightProvider, route, types)
                        }
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
                    fromCoord == null || toCoord == null -> "Välj avresa och mål"
                    else -> "Beräkna avgångstid"
                }
            )
        }

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        result?.let { DepartureBoard(it) }

        if (showOvernightSpots) {
            OvernightSpotsSection(overnightSpots)
        }
    }
}

/**
 * Söker efter övernattningsplatser på tre punkter utspridda längs rutten
 * (25 %, 50 %, 75 % av vägen) istället för bara mittpunkten — en enda punkt
 * missar lätt allt om den råkar hamna i ett glesbefolkat område, särskilt
 * på längre resor genom t.ex. Bergslagen eller andra skogsrika sträckor.
 *
 * Max [maxPerPoint] platser tas med per delsträcka (annars kan en tät
 * anläggning dominera hela listan) — resultaten slås sedan ihop och
 * dubbletter (samma namn + nästan samma koordinat, t.ex. om två
 * sökcirklar överlappar) filtreras bort.
 */
private suspend fun findOvernightSpotsAlongRoute(
    provider: OvernightSpotProvider,
    route: RouteInfo,
    types: Set<OvernightSpotType>,
    maxPerPoint: Int = 4
): List<OvernightSpot> {
    val line = route.polyline
    if (line.isEmpty() || types.isEmpty()) return emptyList()

    val fractions = listOf(0.25, 0.5, 0.75)
    val allSpots = mutableListOf<OvernightSpot>()

    for (fraction in fractions) {
        val index = (fraction * (line.size - 1)).toInt().coerceIn(0, line.size - 1)
        val point = line[index]
        val distanceFromStartKm = (route.distanceKm * fraction).toInt()
        val spotsAtPoint = provider.candidatesNear(
            point = point,
            distanceFromStartKm = distanceFromStartKm,
            types = types
        )
        allSpots += spotsAtPoint.take(maxPerPoint)
    }

    // Enkel dubblettfiltrering: samma namn inom ~0.01° (ca 1 km) räknas som samma plats.
    val seen = mutableSetOf<String>()
    return allSpots.filter { spot ->
        val key = "${spot.name}:${(spot.latitude * 100).toInt()}:${(spot.longitude * 100).toInt()}"
        seen.add(key)
    }
}
