package com.avgangsplaneraren.app.billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class PremiumViewModel(
    private val billingRepository: BillingRepository
) : ViewModel() {

    val premiumState = billingRepository.premiumState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PremiumState.Loading
        )

    fun hasAccess(feature: PremiumFeature): Boolean =
        premiumState.value.hasAccess(feature)

    fun connect() = billingRepository.startConnection()

    fun refresh() = billingRepository.refreshPurchases()
}
