package com.avgangsplaneraren.app.billing

import com.avgangsplaneraren.app.BuildConfig

sealed class PremiumState {
    data object Loading : PremiumState()
    data object Free : PremiumState()
    data class Premium(
        val productId: String,
        val purchaseTimeMillis: Long,
        val isAutoRenewing: Boolean
    ) : PremiumState()
}

enum class PremiumFeature {
    REST_STOPS,
    OVERNIGHT_STAYS,
    CHARGING_STATIONS
}

fun PremiumState.hasAccess(feature: PremiumFeature): Boolean {
    // Debug-bygge (körd direkt från Android Studio) kringgår premium-spärren
    // så vi kan testa/felsöka telefonnummer, priser m.m. utan riktigt köp.
    // Release-byggen (Google Play) har fortfarande betalspärren orörd.
    if (BuildConfig.DEBUG) return true
    return when (this) {
        is PremiumState.Premium -> true
        is PremiumState.Free, is PremiumState.Loading -> false
    }
}
