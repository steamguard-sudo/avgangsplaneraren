package com.avgangsplaneraren.app.data.directions

/**
 * Bas-URL till ert backend (backend/server.js), driftsatt på Render.
 *
 * Tidigare pekade den här mot `http://10.0.2.2:3000/` (Android-emulatorns
 * adress för lokal utveckling). Nu pekar den mot den riktiga, driftsatta
 * tjänsten på Render, med HTTPS — se `AndroidManifest.xml`, där
 * `usesCleartextTraffic` därför är borttaget igen.
 *
 * Om ni vill växla tillbaka till lokal utveckling (t.ex. för att testa
 * ändringar i backend/server.js innan ni pushar), byt tillfälligt till
 * `"http://10.0.2.2:3000/"` och lägg tillbaka
 * `android:usesCleartextTraffic="true"` i manifestet.
 */
object AppConfig {
    const val BACKEND_BASE_URL = "https://avgangsplaneraren-backend.onrender.com/"
}
