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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

/**
 * Visar rutten (blå linje), avresa/mål (gröna/röda markörer), rastplatser
 * (orange) och ev. övernattningsplatser (blå) på en Google-karta.
 *
 * Använder medvetet den KLASSISKA Maps SDK:n (`MapView` + `AndroidView`)
 * istället för det nyare `maps-compose`-biblioteket. `maps-compose` drar
 * med sig väldigt färska transitiva beroenden (Compose 1.11.x, core-ktx
 * 1.19.0) som kräver compileSdk 37 och Android Gradle Plugin 9.1+ — en
 * mycket större uppgradering än vad projektet i övrigt är byggt för. Den
 * klassiska SDK:n (`com.google.android.gms:play-services-maps`) har inga
 * sådana krav och fungerar fint med projektets nuvarande compileSdk 34.
 *
 * Kräver att en Maps API-nyckel finns i `local.properties` (se
 * KOM_IGANG.md) — utan den visas bara en tom/grå ruta istället för en
 * krasch, vilket är Maps SDK:s normala beteende vid saknad/ogiltig nyckel.
 */
@Composable
fun RouteMapView(
    routePoints: List<Coordinates>,
    fromName: String,
    toName: String,
    restStops: List<RestStop>,
    overnightSpots: List<OvernightSpot>,
    modifier: Modifier = Modifier
) {
    if (routePoints.size < 2) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapView(context).apply { onCreate(Bundle()) }
    }
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }

    // Kopplar MapView:ns livscykel (onStart/onResume/onPause/onStop/onDestroy)
    // till Composable:ns livscykel — annars läcker kartan minne eller
    // kraschar när skärmen stängs/återöppnas.
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

    // Ritar om markörer/linje varje gång kartan blir redo eller datan ändras.
    LaunchedEffect(googleMap, routePoints, restStops, overnightSpots) {
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
                .title("Avresa")
                .snippet(fromName)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )
        map.addMarker(
            MarkerOptions()
                .position(latLngPoints.last())
                .title("Mål")
                .snippet(toName)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )

        restStops.forEach { stop ->
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(stop.latitude, stop.longitude))
                    .title(stop.name)
                    .snippet("Rastplats · ca ${stop.distanceFromStartKm} km in på resan")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
            )
        }

        overnightSpots.forEach { spot ->
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(spot.latitude, spot.longitude))
                    .title(spot.name)
                    .snippet("Övernattning · ca ${spot.distanceFromStartKm} km in på resan")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
        }

        try {
            val bounds = LatLngBounds.Builder().apply {
                latLngPoints.forEach { include(it) }
            }.build()
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
        } catch (e: Exception) {
            // Om kartan inte hunnit få en uppmätt storlek än (t.ex. första
            // ritningen), låt den bara visa standardvyn istället för krascha.
        }
    }

    Text(
        "Grön = avresa · Röd = mål · Orange = rastplats · Blå markör = övernattning",
        style = MaterialTheme.typography.labelSmall
    )
}
