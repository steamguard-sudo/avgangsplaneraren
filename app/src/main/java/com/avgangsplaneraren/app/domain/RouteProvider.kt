package com.avgangsplaneraren.app.domain

/**
 * Källa för ruttdata (sträcka, körtid, ruttlinje) mellan två punkter.
 *
 * Två implementationer finns i det här skelettet:
 * - `RouteEstimator` (data/directions) — grov uppskattning utan nätverk,
 *   används som fallback och för snabb UI-utveckling.
 * - `GoogleRoutesRepository` (data/directions) — riktigt anrop mot Google
 *   Routes API, via ert eget backend (se backend/server.js) så att API-
 *   nyckeln aldrig hamnar i appen.
 *
 * `suspend` eftersom den riktiga implementationen gör ett nätverksanrop.
 */
interface RouteProvider {
    suspend fun getRoute(from: Coordinates, to: Coordinates): RouteInfo
}
