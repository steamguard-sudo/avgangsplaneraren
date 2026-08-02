package com.avgangsplaneraren.app.billing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun PremiumGate(
    feature: PremiumFeature,
    viewModel: PremiumViewModel,
    upsell: @Composable () -> Unit = { DefaultUpsell(feature) },
    content: @Composable () -> Unit
) {
    val state by viewModel.premiumState.collectAsState()

    when {
        state is PremiumState.Loading -> {
            // Valfritt: visa en liten laddningsindikator eller inget alls
        }
        state.hasAccess(feature) -> content()
        else -> upsell()
    }
}

@Composable
private fun DefaultUpsell(feature: PremiumFeature) {
    val label = when (feature) {
        PremiumFeature.REST_STOPS -> "Rastplatser"
        PremiumFeature.OVERNIGHT_STAYS -> "Övernattningsplatser"
        PremiumFeature.CHARGING_STATIONS -> "Laddstationer"
    }
    androidx.compose.material3.Text("$label kräver Premium")
}
