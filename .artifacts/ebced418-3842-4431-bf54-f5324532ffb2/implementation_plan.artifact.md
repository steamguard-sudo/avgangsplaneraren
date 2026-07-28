# Fix Unresolved Reference: OvernightSpotType

The build is failing because `OvernightSpotType` is missing in the `com.avgangsplaneraren.app.domain` package, yet it is imported and used in `PlannerScreen.kt`.

## Proposed Changes

### Domain Layer

#### [MODIFY] [OvernightSpot.kt](file:///C:/Users/Z97X/Documents/avgangsplaneraren/AvgangsplanerarenAndroid/app/src/main/java/com/avgangsplaneraren/app/domain/OvernightSpot.kt)
- Add `OvernightSpotType` enum with values `CARAVAN_SITE` and `CAMP_SITE`.

#### [MODIFY] [OvernightSpotProvider.kt](file:///C:/Users/Z97X/Documents/avgangsplaneraren/AvgangsplanerarenAndroid/app/src/main/java/com/avgangsplaneraren/app/domain/OvernightSpotProvider.kt)
- Update `candidatesNear` signature to include `types: Set<OvernightSpotType>`.

### Data Layer

#### [MODIFY] [OverpassOvernightRepository.kt](file:///C:/Users/Z97X/Documents/avgangsplaneraren/AvgangsplanerarenAndroid/app/src/main/java/com/avgangsplaneraren/app/data/osm/OverpassOvernightRepository.kt)
- Implement the updated `candidatesNear` method.
- Filter the results from the backend based on the provided `types`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to ensure the unresolved reference is fixed and the project builds.
