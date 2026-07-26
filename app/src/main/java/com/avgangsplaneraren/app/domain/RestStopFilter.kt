package com.avgangsplaneraren.app.domain

/**
 * Filtrerar rastplatser utifrån användarens önskemål.
 * Om ingen rastplats i listan uppfyller kravet faller filtret tillbaka
 * på hela listan, så att appen aldrig visar en tom rutt.
 */
object RestStopFilter {

    fun apply(stops: List<RestStop>, onlyWithTableAndBench: Boolean): List<RestStop> {
        if (!onlyWithTableAndBench) return stops

        val filtered = stops.filter { it.hasTable && it.hasBench }
        return filtered.ifEmpty { stops }
    }
}
