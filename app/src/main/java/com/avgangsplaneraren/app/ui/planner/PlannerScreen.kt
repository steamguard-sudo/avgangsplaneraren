package com.avgangsplaneraren.app.ui.planner

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import com.avgangsplaneraren.app.ui.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avgangsplaneraren.app.R
import com.avgangsplaneraren.app.AppLanguageState
import com.avgangsplaneraren.app.ui.withLocale
import com.avgangsplaneraren.app.billing.BillingRepository
import com.avgangsplaneraren.app.billing.PremiumFeature
import com.avgangsplaneraren.app.billing.PremiumGate
import com.avgangsplaneraren.app.billing.PremiumViewModel
import com.avgangsplaneraren.app.data.directions.AppConfig
import com.avgangsplaneraren.app.data.directions.GooglePlacesRepository
import com.avgangsplaneraren.app.data.directions.GoogleRoutesRepository
import com.avgangsplaneraren.app.data.directions.RouteEstimator
import com.avgangsplaneraren.app.data.nobil.NobilChargingRepository
import com.avgangsplaneraren.app.data.osm.OverpassOvernightRepository
import com.avgangsplaneraren.app.data.trafikverket.TrafikverketDataSeeder
import com.avgangsplaneraren.app.data.trafikverket.TrafikverketDatabase
import com.avgangsplaneraren.app.data.trafikverket.TrafikverketRestStopRepository
import com.avgangsplaneraren.app.data.trips.SavedTripDatabase
import com.avgangsplaneraren.app.data.trips.SavedTripRepository
import com.avgangsplaneraren.app.domain.CalculateDeparture
import com.avgangsplaneraren.app.domain.ChargingStation
import com.avgangsplaneraren.app.domain.ChargingStationProvider
import com.avgangsplaneraren.app.domain.Coordinates
import com.avgangsplaneraren.app.domain.DepartureResult
import com.avgangsplaneraren.app.domain.OvernightSpot
import com.avgangsplaneraren.app.domain.OvernightSpotProvider
import com.avgangsplaneraren.app.domain.OvernightSpotType
import com.avgangsplaneraren.app.domain.RouteInfo
import com.avgangsplaneraren.app.domain.SavedTrip
import com.avgangsplaneraren.app.domain.TripInput
import com.avgangsplaneraren.app.notifications.NotificationScheduler
import com.avgangsplaneraren.app.ui.board.ChargingStationsSection
import com.avgangsplaneraren.app.ui.board.DepartureBoard
import com.avgangsplaneraren.app.ui.board.OvernightSpotsSection
import com.avgangsplaneraren.app.ui.map.RouteMapView
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

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
    var lastRoutePolyline by remember { mutableStateOf<List<Coordinates>>(emptyList()) }
    var overnightSpots by remember { mutableStateOf<List<OvernightSpot>>(emptyList()) }
    var overnightSearchDone by remember { mutableStateOf(false) }
    var overnightSearchFailed by remember { mutableStateOf(false) }
    var showChargingStations by remember { mutableStateOf(false) }
    var chargingStations by remember { mutableStateOf<List<ChargingStation>>(emptyList()) }
    var chargingSearchDone by remember { mutableStateOf(false) }
    var chargingSearchFailed by remember { mutableStateOf(false) }
    var isSeeding by remember { mutableStateOf(true) }
    var isCalculating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedSegment by remember { mutableStateOf(1) }
    var lastRoute by remember { mutableStateOf<RouteInfo?>(null) }

    var notifyEnabled by remember { mutableStateOf(false) }
    var notifyMinutesBefore by remember { mutableStateOf(15) }
    var notificationPermissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var canScheduleExactAlarms by remember { mutableStateOf(NotificationScheduler.canScheduleExactAlarms(context)) }
    var notificationScheduledMessage by remember { mutableStateOf<String?>(null) }
    var searchStatusMessage by remember { mutableStateOf<String?>(null) }

    var loadTripCounter by remember { mutableStateOf(0) }
    var showSaveTripDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationPermissionGranted = granted }

    val placeProvider = remember { GooglePlacesRepository(baseUrl = AppConfig.BACKEND_BASE_URL) }
    val routeProvider = remember {
        GoogleRoutesRepository(baseUrl = AppConfig.BACKEND_BASE_URL, fallback = RouteEstimator())
    }
    val overnightProvider = remember { OverpassOvernightRepository(baseUrl = AppConfig.BACKEND_BASE_URL) }
    // Laddplatser hämtas via NOBIL (Norges/Sveriges officiella laddstationsregister)
    // istället för OpenStreetMap/Overpass. Byt tillbaka om NOBIL visar sig sämre i
    // praktiken: byt bara klassnamnet nedan till OverpassChargingRepository
    // (com.avgangsplaneraren.app.data.osm, samma ChargingStationProvider-gränssnitt)
    // och peka importen ovan dit igen.
    val chargingProvider = remember { NobilChargingRepository(baseUrl = AppConfig.BACKEND_BASE_URL) }
    val database = remember { TrafikverketDatabase.getInstance(context) }
    val restStopRepository = remember { TrafikverketRestStopRepository(database.restAreaDao()) }
    val calculator = remember { CalculateDeparture(restStopProvider = restStopRepository) }
    val savedTripRepository = remember {
        SavedTripRepository(SavedTripDatabase.getInstance(context).savedTripDao())
    }
    val savedTrips by savedTripRepository.observeAll().collectAsState(initial = emptyList())
    val searchingOvernightText = stringResource(R.string.searching_overnight)
    val searchingChargingText = stringResource(R.string.searching_charging)

    val premiumViewModel: PremiumViewModel = viewModel {
        PremiumViewModel(BillingRepository(context.applicationContext as Application))
    }

    LaunchedEffect(Unit) {
        TrafikverketDataSeeder.seedIfNeeded(context, database.restAreaDao())
        isSeeding = false
    }

    LaunchedEffect(premiumViewModel) {
        premiumViewModel.connect()
    }

    val canCalculate = fromCoord != null && toCoord != null && !isSeeding && !isCalculating
    val numSegments = ((result?.distanceKm ?: 0) / 200.0).roundToInt().coerceIn(1, 6)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LanguageSelector()

        Text(stringResource(R.string.planner_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.planner_subtitle),
            style = MaterialTheme.typography.bodyMedium
        )

        SavedTripsSection(
            trips = savedTrips,
            onTripSelected = { trip ->
                fromName = trip.fromPlace
                fromCoord = trip.fromCoordinates
                toName = trip.toPlace
                toCoord = trip.toCoordinates
                bufferMinutes = trip.bufferMinutes
                minutesPerBreak = trip.minutesPerBreak
                onlyAmenities = trip.onlyStopsWithTableAndBench
                showOvernightSpots = trip.showOvernightSpots
                includeCaravanSites = trip.includeCaravanSites
                includeCampSites = trip.includeCampSites
                campingStopHours = trip.campingStopHours
                campingStopExtraMinutes = trip.campingStopExtraMinutes
                notifyEnabled = trip.notifyEnabled
                notifyMinutesBefore = trip.notifyMinutesBefore
                loadTripCounter++
            },
            onTripDeleted = { trip ->
                coroutineScope.launch { savedTripRepository.delete(trip) }
            }
        )

        key(loadTripCounter) {
            PlaceAutocompleteField(
                label = stringResource(R.string.label_from),
                placeProvider = placeProvider,
                initialValue = fromName.orEmpty(),
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
                label = stringResource(R.string.label_to),
                placeProvider = placeProvider,
                initialValue = toName.orEmpty(),
                onPlaceSelected = { name, coordinates ->
                    toName = name
                    toCoord = coordinates
                },
                onCleared = {
                    toName = null
                    toCoord = null
                }
            )
        }

        ArrivalDateTimePicker(value = arrival, onValueChange = { arrival = it })

        key(loadTripCounter) {
            NumberField(
                value = bufferMinutes,
                onValueChange = { bufferMinutes = it },
                label = stringResource(R.string.label_buffer_minutes)
            )

            NumberField(
                value = minutesPerBreak,
                onValueChange = { minutesPerBreak = it },
                label = stringResource(R.string.label_minutes_per_break),
                supportingText = stringResource(R.string.hint_minutes_per_break)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = onlyAmenities, onCheckedChange = { onlyAmenities = it })
            Text(stringResource(R.string.checkbox_only_amenities))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = showOvernightSpots, onCheckedChange = { showOvernightSpots = it })
            Text(stringResource(R.string.checkbox_show_overnight))
        }

        if (showOvernightSpots) {
            Column(modifier = Modifier.padding(start = 40.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeCaravanSites, onCheckedChange = { includeCaravanSites = it })
                    Text(stringResource(R.string.checkbox_caravan_sites), style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeCampSites, onCheckedChange = { includeCampSites = it })
                    Text(stringResource(R.string.checkbox_camp_sites), style = MaterialTheme.typography.bodySmall)
                }
                DurationPicker(
                    label = stringResource(R.string.label_camping_stop_duration),
                    hours = campingStopHours,
                    minutes = campingStopExtraMinutes,
                    onDurationChange = { h, m ->
                        campingStopHours = h
                        campingStopExtraMinutes = m
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.hint_camping_stop_duration),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = showChargingStations, onCheckedChange = { showChargingStations = it })
            Text(stringResource(R.string.checkbox_show_charging))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = notifyEnabled,
                onCheckedChange = { checked ->
                    notifyEnabled = checked
                    if (checked &&
                        !notificationPermissionGranted &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (checked) {
                        canScheduleExactAlarms = NotificationScheduler.canScheduleExactAlarms(context)
                    }
                }
            )
            Text(stringResource(R.string.checkbox_notify_before))
        }

        if (notifyEnabled) {
            Column(modifier = Modifier.padding(start = 40.dp)) {
                key(loadTripCounter) {
                    NumberField(
                        value = notifyMinutesBefore,
                        onValueChange = { notifyMinutesBefore = it },
                        label = stringResource(R.string.label_notify_minutes)
                    )
                }

                if (!notificationPermissionGranted) {
                    Text(
                        stringResource(R.string.notification_permission_rationale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) {
                        Text(stringResource(R.string.button_grant_notification_permission))
                    }
                }

                if (!canScheduleExactAlarms) {
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }) {
                        Text(stringResource(R.string.button_enable_exact_alarms))
                    }
                }

                notificationScheduledMessage?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        OutlinedButton(
            enabled = fromCoord != null && toCoord != null,
            onClick = { showSaveTripDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.button_save_trip))
        }

        if (showSaveTripDialog) {
            SaveTripDialog(
                defaultLabel = stringResource(
                    R.string.default_trip_label_format,
                    fromName.orEmpty(),
                    toName.orEmpty()
                ),
                onConfirm = { label ->
                    val from = fromCoord
                    val to = toCoord
                    if (from != null && to != null) {
                        coroutineScope.launch {
                            savedTripRepository.save(
                                SavedTrip(
                                    label = label,
                                    fromPlace = fromName.orEmpty(),
                                    fromCoordinates = from,
                                    toPlace = toName.orEmpty(),
                                    toCoordinates = to,
                                    bufferMinutes = bufferMinutes,
                                    minutesPerBreak = minutesPerBreak,
                                    onlyStopsWithTableAndBench = onlyAmenities,
                                    showOvernightSpots = showOvernightSpots,
                                    includeCaravanSites = includeCaravanSites,
                                    includeCampSites = includeCampSites,
                                    campingStopHours = campingStopHours,
                                    campingStopExtraMinutes = campingStopExtraMinutes,
                                    notifyEnabled = notifyEnabled,
                                    notifyMinutesBefore = notifyMinutesBefore
                                )
                            )
                        }
                    }
                    showSaveTripDialog = false
                },
                onDismiss = { showSaveTripDialog = false }
            )
        }

        Button(
            enabled = canCalculate,
            onClick = {
                val from = fromCoord ?: return@Button
                val to = toCoord ?: return@Button
                errorMessage = null
                isCalculating = true
                overnightSpots = emptyList()
                overnightSearchDone = false
                overnightSearchFailed = false
                chargingStations = emptyList()
                chargingSearchDone = false
                chargingSearchFailed = false
                notificationScheduledMessage = null
                selectedSegment = 1
                coroutineScope.launch {
                    try {
                        val route = routeProvider.getRoute(from, to)
                        lastRoutePolyline = route.polyline
                        lastRoute = route

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
                        val departureResult = calculator.calculate(trip, route)
                        result = departureResult

                        if (notifyEnabled) {
                            val triggerTime = departureResult.departureTime.minusMinutes(notifyMinutesBefore.toLong())
                            val triggerAtMillis = triggerTime
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()

                            if (triggerAtMillis > System.currentTimeMillis()) {
                                NotificationScheduler.ensureChannel(context)
                                val scheduled = NotificationScheduler.schedule(
                                    context = context,
                                    triggerAtMillis = triggerAtMillis,
                                    fromPlace = fromName.orEmpty(),
                                    toPlace = toName.orEmpty()
                                )
                                canScheduleExactAlarms = scheduled || NotificationScheduler.canScheduleExactAlarms(context)
                                notificationScheduledMessage = if (scheduled) {
                                    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                                    context.withLocale(AppLanguageState.current.value).getString(
                                        R.string.notification_scheduled_confirmation,
                                        triggerTime.format(timeFormatter)
                                    )
                                } else {
                                    null
                                }
                            }
                        }

                        if (showOvernightSpots) {
                            searchStatusMessage = searchingOvernightText
                            val types = buildSet {
                                if (includeCaravanSites) add(OvernightSpotType.CARAVAN_SITE)
                                if (includeCampSites) add(OvernightSpotType.CAMP_SITE)
                            }
                            val outcome = findOvernightSpotsAlongRoute(overnightProvider, route, types)
                            overnightSpots = outcome.spots
                            overnightSearchFailed = outcome.hadFailure
                            overnightSearchDone = true
                        }

                        if (showChargingStations) {
                            searchStatusMessage = searchingChargingText
                            val outcome = findChargingStationsAlongRoute(chargingProvider, route)
                            chargingStations = outcome.stations
                            chargingSearchFailed = outcome.hadFailure
                            chargingSearchDone = true
                        }

                        searchStatusMessage = null
                    } catch (e: Exception) {
                        errorMessage = context.withLocale(AppLanguageState.current.value)
                            .getString(R.string.error_calculate_failed, e.message)
                    } finally {
                        isCalculating = false
                        searchStatusMessage = null
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    isSeeding -> stringResource(R.string.button_loading_data)
                    isCalculating -> stringResource(R.string.button_calculating)
                    fromCoord == null || toCoord == null -> stringResource(R.string.button_choose_places)
                    else -> stringResource(R.string.button_calculate)
                }
            )
        }

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        searchStatusMessage?.let {
            BlinkingStatusText(it)
        }

        result?.let { departureResult ->
            DepartureBoard(departureResult, premiumViewModel)
            RouteMapView(
                routePoints = lastRoutePolyline,
                fromName = fromName.orEmpty(),
                toName = toName.orEmpty(),
                restStops = departureResult.restStops,
                overnightSpots = overnightSpots,
                chargingStations = chargingStations,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        if (result != null && numSegments > 1 && (showChargingStations || showOvernightSpots)) {
            val totalDistance = result?.distanceKm ?: 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (n in 1..numSegments) {
                    val startKm = ((n - 1).toDouble() * totalDistance / numSegments).roundToInt()
                    val endKm = (n.toDouble() * totalDistance / numSegments).roundToInt()
                    FilterChip(
                        selected = selectedSegment == n,
                        onClick = {
                            selectedSegment = n
                            val route = lastRoute
                            if (route != null) {
                                coroutineScope.launch {
                                    val startFraction = (n - 1).toDouble() / numSegments
                                    val endFraction = n.toDouble() / numSegments
                                    val fractionRange = startFraction..endFraction
                                    searchStatusMessage = context.withLocale(AppLanguageState.current.value)
                                        .getString(R.string.searching_segment_format, n)
                                    if (showChargingStations) {
                                        val outcome = findChargingStationsAlongRoute(
                                            chargingProvider,
                                            route,
                                            fractionRange = fractionRange
                                        )
                                        chargingStations = outcome.stations
                                        chargingSearchFailed = outcome.hadFailure
                                        chargingSearchDone = true
                                    }
                                    if (showOvernightSpots) {
                                        val types = buildSet {
                                            if (includeCaravanSites) add(OvernightSpotType.CARAVAN_SITE)
                                            if (includeCampSites) add(OvernightSpotType.CAMP_SITE)
                                        }
                                        val outcome = findOvernightSpotsAlongRoute(
                                            overnightProvider,
                                            route,
                                            types,
                                            fractionRange = fractionRange
                                        )
                                        overnightSpots = outcome.spots
                                        overnightSearchFailed = outcome.hadFailure
                                        overnightSearchDone = true
                                    }
                                    searchStatusMessage = null
                                }
                            }
                        },
                        label = {
                            Text(stringResource(R.string.segment_label_format, n, startKm, endKm))
                        }
                    )
                }
            }
        }

        if (showOvernightSpots) {
            PremiumGate(feature = PremiumFeature.OVERNIGHT_STAYS, viewModel = premiumViewModel) {
                OvernightSpotsSection(
                    spots = overnightSpots,
                    searchDone = overnightSearchDone,
                    searchFailed = overnightSearchFailed
                )
            }
        }

        if (showChargingStations) {
            PremiumGate(feature = PremiumFeature.CHARGING_STATIONS, viewModel = premiumViewModel) {
                ChargingStationsSection(
                    stations = chargingStations,
                    searchDone = chargingSearchDone,
                    searchFailed = chargingSearchFailed
                )
            }
        }
    }
}

private data class OvernightSearchOutcome(
    val spots: List<OvernightSpot>,
    val hadFailure: Boolean
)

private suspend fun findOvernightSpotsAlongRoute(
    provider: OvernightSpotProvider,
    route: RouteInfo,
    types: Set<OvernightSpotType>,
    maxPerPoint: Int = 4,
    fractionRange: ClosedFloatingPointRange<Double> = 0.0..1.0
): OvernightSearchOutcome {
    val line = route.polyline
    if (line.isEmpty() || types.isEmpty()) return OvernightSearchOutcome(emptyList(), hadFailure = false)

    val numPoints = (route.distanceKm / 150).coerceIn(3, 8)
    val fractions = (1..numPoints).map { it.toDouble() / (numPoints + 1) }
        .map { fractionRange.start + it * (fractionRange.endInclusive - fractionRange.start) }
    val allSpots = mutableListOf<OvernightSpot>()
    var hadFailure = false

    for (fraction in fractions) {
        val index = (fraction * (line.size - 1)).toInt().coerceIn(0, line.size - 1)
        val point = line[index]
        val distanceFromStartKm = (route.distanceKm * fraction).toInt()
        try {
            val spotsAtPoint = provider.candidatesNear(
                point = point,
                distanceFromStartKm = distanceFromStartKm,
                types = types
            )
            allSpots += spotsAtPoint.take(maxPerPoint)
        } catch (e: Exception) {
            hadFailure = true
        }
    }

    val seen = mutableSetOf<String>()
    val deduped = allSpots.filter { spot ->
        val key = "${spot.name}:${(spot.latitude * 100).toInt()}:${(spot.longitude * 100).toInt()}"
        seen.add(key)
    }
    return OvernightSearchOutcome(deduped, hadFailure)
}

private data class ChargingSearchOutcome(
    val stations: List<ChargingStation>,
    val hadFailure: Boolean
)

private const val CHARGING_SEARCH_RADIUS_KM = 5.0

private suspend fun findChargingStationsAlongRoute(
    provider: ChargingStationProvider,
    route: RouteInfo,
    maxPerPoint: Int = 4,
    fractionRange: ClosedFloatingPointRange<Double> = 0.0..1.0
): ChargingSearchOutcome {
    val line = route.polyline
    if (line.isEmpty()) return ChargingSearchOutcome(emptyList(), hadFailure = false)

    val numPoints = (route.distanceKm / 150).coerceIn(6, 12)
    val fractions = (1..numPoints).map { it.toDouble() / (numPoints + 1) }
        .map { fractionRange.start + it * (fractionRange.endInclusive - fractionRange.start) }
    val allStations = mutableListOf<ChargingStation>()
    var hadFailure = false

    for (fraction in fractions) {
        val index = (fraction * (line.size - 1)).toInt().coerceIn(0, line.size - 1)
        val point = line[index]
        val distanceFromStartKm = (route.distanceKm * fraction).toInt()
        try {
            val stationsAtPoint = provider.candidatesNear(
                point = point,
                distanceFromStartKm = distanceFromStartKm,
                radiusKm = CHARGING_SEARCH_RADIUS_KM
            )
            allStations += stationsAtPoint
                .filter { it.distanceFromRouteKm <= CHARGING_SEARCH_RADIUS_KM }
                .take(maxPerPoint)
        } catch (e: Exception) {
            hadFailure = true
        }
    }

    val seenStations = mutableSetOf<String>()
    val dedupedStations = allStations.filter { station ->
        val key = "${station.name}:${(station.latitude * 100).toInt()}:${(station.longitude * 100).toInt()}"
        seenStations.add(key)
    }
    return ChargingSearchOutcome(dedupedStations, hadFailure)
}

@Composable
private fun BlinkingStatusText(text: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "searchStatusBlink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "searchStatusAlpha"
    )
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.alpha(alpha)
    )
}
