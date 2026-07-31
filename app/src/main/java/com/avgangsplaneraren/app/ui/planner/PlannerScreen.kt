package com.avgangsplaneraren.app.ui.planner

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.avgangsplaneraren.app.ui.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.avgangsplaneraren.app.R
import com.avgangsplaneraren.app.AppLanguageState
import com.avgangsplaneraren.app.ui.withLocale
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
import com.avgangsplaneraren.app.notifications.NotificationScheduler
import com.avgangsplaneraren.app.ui.board.DepartureBoard
import com.avgangsplaneraren.app.ui.board.OvernightSpotsSection
import com.avgangsplaneraren.app.ui.map.RouteMapView
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    var isSeeding by remember { mutableStateOf(true) }
    var isCalculating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationPermissionGranted = granted }

    val placeProvider = remember { GooglePlacesRepository(baseUrl = AppConfig.BACKEND_BASE_URL) }
    val routeProvider = remember {
        GoogleRoutesRepository(baseUrl = AppConfig.BACKEND_BASE_URL, fallback = RouteEstimator())
    }
    val overnightProvider = remember { OverpassOvernightRepository(baseUrl = AppConfig.BACKEND_BASE_URL) }
    val database = remember { TrafikverketDatabase.getInstance(context) }
    val restStopRepository = remember { TrafikverketRestStopRepository(database.restAreaDao()) }
    val calculator = remember { CalculateDeparture(restStopProvider = restStopRepository) }

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
        LanguageSelector()

        Text(stringResource(R.string.planner_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.planner_subtitle),
            style = MaterialTheme.typography.bodyMedium
        )

        PlaceAutocompleteField(
            label = stringResource(R.string.label_from),
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
            label = stringResource(R.string.label_to),
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
                NumberField(
                    value = notifyMinutesBefore,
                    onValueChange = { notifyMinutesBefore = it },
                    label = stringResource(R.string.label_notify_minutes)
                )

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
                notificationScheduledMessage = null
                coroutineScope.launch {
                    try {
                        val route = routeProvider.getRoute(from, to)
                        lastRoutePolyline = route.polyline

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
                            val types = buildSet {
                                if (includeCaravanSites) add(OvernightSpotType.CARAVAN_SITE)
                                if (includeCampSites) add(OvernightSpotType.CAMP_SITE)
                            }
                            val outcome = findOvernightSpotsAlongRoute(overnightProvider, route, types)
                            overnightSpots = outcome.spots
                            overnightSearchFailed = outcome.hadFailure
                            overnightSearchDone = true
                        }
                    } catch (e: Exception) {
                        errorMessage = context.withLocale(AppLanguageState.current.value)
                            .getString(R.string.error_calculate_failed, e.message)
                    } finally {
                        isCalculating = false
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

        result?.let { departureResult ->
            DepartureBoard(departureResult)
            RouteMapView(
                routePoints = lastRoutePolyline,
                fromName = fromName.orEmpty(),
                toName = toName.orEmpty(),
                restStops = departureResult.restStops,
                overnightSpots = overnightSpots,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        if (showOvernightSpots) {
            OvernightSpotsSection(
                spots = overnightSpots,
                searchDone = overnightSearchDone,
                searchFailed = overnightSearchFailed
            )
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
    maxPerPoint: Int = 4
): OvernightSearchOutcome {
    val line = route.polyline
    if (line.isEmpty() || types.isEmpty()) return OvernightSearchOutcome(emptyList(), hadFailure = false)

    val numPoints = (route.distanceKm / 150).coerceIn(3, 8)
    val fractions = (1..numPoints).map { it.toDouble() / (numPoints + 1) }
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
