# Offline proximity estimation

KalPlan can improve the preliminary feasibility status without contacting a routing provider.

## Idea

A bundled local place index resolves:
- German postal codes.
- city/place names.
- optional administrative region.

The result is a centroid coordinate, not a street-level geocode.

From two centroids KalPlan calculates a great-circle distance and converts it into a conservative corridor.

Example intent:
- Dortmund -> Bochum should be classified much closer than Dortmund -> Euskirchen.
- no network request is needed.
- no routing quota is consumed.

## Data source

Preferred first candidate: GeoNames postal-code/gazetteer downloads.

The dataset itself is not committed yet. Before bundling:
- pin the exact source snapshot.
- verify the license text attached to that snapshot.
- generate attribution/NOTICE.
- store only fields needed by KalPlan.
- document the preprocessing script and checksum.

GeoNames provides downloadable postal-code data with latitude/longitude and an accuracy field.

## Lookup confidence

Highest:
1. exact country + postal code.
2. postal code + matching place name.
3. unique place name + country/region.

If a city name is ambiguous, KalPlan must not guess.

## Output

Do not show fake precision.

Preferred display:
- approx. 18 km straight line.
- rough drive estimate: about 25-45 min.
- source: offline postcode estimate.

A manually requested route replaces or supplements this estimate with a provider result.

## Preliminary assessment

If available gap is below the lower estimate plus buffer:
- likely conflict.

If gap is above the upper estimate plus buffer:
- likely feasible.

Otherwise:
- possibly feasible.

The user can still request real routing manually.
