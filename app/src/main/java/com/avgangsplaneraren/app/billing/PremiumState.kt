package com.avgangsplaneraren.app.billing

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

fun PremiumState.hasAccess(feature: PremiumFeature): Boolean = when (this) {
    is PremiumState.Premium -> true
    is PremiumState.Free, is PremiumState.Loading -> false
}
