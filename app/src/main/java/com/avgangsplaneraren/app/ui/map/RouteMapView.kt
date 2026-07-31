package com.avgangsplaneraren.app.ui.map

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.avgangsplaneraren.app.ui.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.avgangsplaneraren.app.R
import com.avgangsplaneraren.app.domain.ChargingStation
import com.avgangsplaneraren.app.domain.Coordinates
import com.avgangsplaneraren.app.domain.OvernightSpot
import com.avgangsplaneraren.app.domain.RestStop
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions

@Composable
fun RouteMapView(
    routePoints: List<Coordinates>,
    fromName: String,
    toName: String,
    restStops: List<RestStop>,
    overnightSpots: List<OvernightSpot>,
    chargingStations: List<ChargingStation> = emptyList(),
    modifier: Modifier = Modifier
) {
    if (routePoints.size < 2) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val departureLabel = stringResource(R.string.map_departure)
    val destinationLabel = stringResource(R.string.map_destination)
    val restStopSnippetFormat = stringResource(R.string.map_rest_stop_snippet)
    val overnightSnippetFormat = stringResource(R.string.map_overnight_snippet)
    val chargingSnippetFormat = stringResource(R.string.map_charging_snippet)
    val legendText = stringResource(R.string.map_legend)

    val mapView = remember {
        MapView(context).apply { onCreate(Bundle()) }
    }
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(12.dp)),
        factory = {
            mapView.apply {
                getMapAsync { map -> googleMap = map }
            }
        }
    )

    LaunchedEffect(googleMap, routePoints, restStops, overnightSpots, chargingStations) {
        val map = googleMap ?: return@LaunchedEffect
        map.clear()

        val latLngPoints = routePoints.map { LatLng(it.lat, it.lon) }

        map.addPolyline(
            PolylineOptions()
                .addAll(latLngPoints)
                .color(android.graphics.Color.parseColor("#1A73E8"))
                .width(8f)
        )

        map.addMarker(
            MarkerOptions()
                .position(latLngPoints.first())
                .title(departureLabel)
                .snippet(fromName)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )
        map.addMarker(
            MarkerOptions()
                .position(latLngPoints.last())
                .title(destinationLabel)
                .snippet(toName)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )

        restStops.forEach { stop ->
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(stop.latitude, stop.longitude))
                    .title(stop.name)
                    .snippet(String.format(restStopSnippetFormat, stop.distanceFromStartKm))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
            )
        }

        overnightSpots.forEach { spot ->
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(spot.latitude, spot.longitude))
                    .title(spot.name)
                    .snippet(String.format(overnightSnippetFormat, spot.distanceFromStartKm))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
        }

        chargingStations.forEach { station ->
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(station.latitude, station.longitude))
                    .title(station.name)
                    .snippet(String.format(chargingSnippetFormat, station.distanceFromStartKm))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
            )
        }

        try {
            val bounds = LatLngBounds.Builder().apply {
                latLngPoints.forEach { include(it) }
            }.build()
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
        } catch (e: Exception) {
        }
    }

    Text(
        legendText,
        style = MaterialTheme.typography.labelSmall
    )
}
