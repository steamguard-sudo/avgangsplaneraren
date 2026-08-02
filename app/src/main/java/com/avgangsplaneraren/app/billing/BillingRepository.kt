package com.avgangsplaneraren.app.billing

import android.app.Application
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BillingRepository(
    private val app: Application
) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_ID = "premium_yearly"
        const val PRODUCT_TYPE = BillingClient.ProductType.SUBS
    }

    private val _premiumState = MutableStateFlow<PremiumState>(PremiumState.Loading)
    val premiumState: StateFlow<PremiumState> = _premiumState.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(app)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    fun startConnection(onReady: () -> Unit = {}) {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refreshPurchases()
                    onReady()
                } else {
                    _premiumState.value = PremiumState.Free
                }
            }

            override fun onBillingServiceDisconnected() {
                // BillingClient försöker återansluta automatiskt vid nästa anrop.
            }
        })
    }

    fun refreshPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(PRODUCT_TYPE)
            .build()

        billingClient.queryPurchasesAsync(params) { _, purchases ->
            val active = purchases.firstOrNull {
                it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            _premiumState.value = if (active != null) {
                PremiumState.Premium(
                    productId = active.products.firstOrNull().orEmpty(),
                    purchaseTimeMillis = active.purchaseTime,
                    isAutoRenewing = active.isAutoRenewing
                )
            } else {
                PremiumState.Free
            }
        }
    }

    fun queryProductDetails(onResult: (ProductDetails?) -> Unit) {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(PRODUCT_TYPE)
            .build()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { _, productDetailsList ->
            onResult(productDetailsList.firstOrNull())
        }
    }

    fun launchPurchaseFlow(activity: android.app.Activity, productDetails: ProductDetails) {
        val offerToken = productDetails.subscriptionOfferDetails
            ?.firstOrNull()?.offerToken

        val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        offerToken?.let { paramsBuilder.setOfferToken(it) }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(paramsBuilder.build()))
            .build()

        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { purchase ->
                if (!purchase.isAcknowledged) {
                    val ackParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(ackParams) {}
                }
            }
            refreshPurchases()
        }
    }
}
