package com.avgangsplaneraren.app.domain

/** En platsträff i autokompletteringslistan, t.ex. medan användaren skriver "Jönk...". */
data class PlaceSuggestion(
    val placeId: String,
    val description: String
)
