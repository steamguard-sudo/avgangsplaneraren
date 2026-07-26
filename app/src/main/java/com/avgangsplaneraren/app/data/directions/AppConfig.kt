package com.avgangsplaneraren.app.data.directions

/**
 * Bas-URL till ert backend (backend/server.js).
 *
 * `10.0.2.2` är Android-emulatorns särskilda adress för "min dators
 * localhost" — funkar direkt om ni kör backend lokalt (`npm start`) och
 * appen i emulatorn. På en fysisk telefon eller i produktion, byt ut mot
 * er riktiga serveradress (och helst via en byggkonfiguration/BuildConfig
 * per byggvariant, inte hårdkodat här).
 */
object AppConfig {
    const val BACKEND_BASE_URL = "http://10.0.2.2:3000/"
}
