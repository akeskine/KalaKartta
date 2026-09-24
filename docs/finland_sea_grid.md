# Finland sea grid

This document describes the offline-generated asset used by the standalone
`FinlandSeaService`. The Android runtime does not download source data or
process geometries.

## Source data

The feature classes are from the Finnish National Land Survey
(`Maanmittauslaitos`, MML). The complete source used for the v2 asset is the
MML-derived 1:1,000,000 generalised map product, mirrored at
`https://kartat.kapsi.fi/files/yleiskartta_1000k/kaikki/etrs89/shape/` and
repacked as a two-layer GeoPackage. It is not the full-resolution 1:10,000
Maastotietokanta GeoPackage.

This source limitation matters: the 100 m cell size is raster spacing, not
100 m shoreline accuracy. The 1:1,000,000 source is strongly generalised and
cannot support precise coastal classification. The full Maastotietokanta
GeoPackage is over 100 GB, and MML's bounded file-service download requires an
API key that was not available in this environment. Results remain limited
to the MML-derived source coverage and must not be interpreted as navigation
or precise shoreline data.

The selected source has one valid `36211` sea polygon (area about
`55,446.8 km²`, bounds X `59,797–544,061 m`, Y `6,603,737–7,300,765 m`) and
`5,147` valid `30223` shoreline lines (total length about `16,473 km`, bounds
X `73,812–544,061 m`, Y `6,632,288–7,300,765 m`). These are EPSG:3067
bounding boxes, not claims that sea geometry fills the rectangle or reaches
100 km offshore. Each selected cell still has to intersect the sea polygon
and lie within 100,000 m of a shoreline segment.

The selected geometry bounds predict a `4,844 × 6,971` v2 grid at 100 m.
That is `33,767,524` cells, a `4,220,941`-byte bit payload and a
`4,220,981`-byte complete binary including its 40-byte header. Generation
must verify and report the actual values before the asset is accepted.

Only these MML classes are accepted:

- `Merivesi (64/36211)` polygons (`kohdeluokka = 36211`)
- `Meren rantaviiva (30223)` lines (`kohdeluokka = 30223`)

The sea class is intentionally read separately from `Järvivesi (36200)` and
other water classes. The generator stops when either class, its geometry, or a
source CRS is missing. It also rejects invalid geometries and unexpected
geometry types instead of silently producing an incomplete asset.

## Reproducible generation

Install the offline tools with Python 3:

```text
python -m pip install -r tools/requirements-finland-sea-grid.txt
```

To reproduce the current v2 asset, download the MML-derived 1:1,000,000
shapefile archive and prepare a minimal GeoPackage containing only its sea and
shoreline classes:

```text
curl.exe -L "https://kartat.kapsi.fi/files/yleiskartta_1000k/kaikki/etrs89/shape/1_milj_Shape_etrs_shape.zip" -o mml_1milj.zip
python tools/prepare_finland_sea_source.py mml_1milj.zip mml_sea_source.gpkg
python tools/finland_sea_grid_generator.py mml_sea_source.gpkg
python tools/render_finland_sea_grid.py
python -m unittest tools.test_finland_sea_grid_generator
```

The default binary output is `app/src/main/assets/finland_sea_grid.bin` and
the PNG is `docs/finland_sea_grid.png`. For a full-resolution Maastotietokanta
GeoPackage, skip the preparation command and pass its path directly to the
generator. The bounded MML file service requires an API key; follow MML's
current access instructions and confirm that the file contains both required
classes before generating.

A different output and explicit GeoPackage layers can be supplied when a
source uses non-standard layer names:

```text
python tools/finland_sea_grid_generator.py path\to\mtkmaasto.gpkg `
  --sea-layer <sea-layer> `
  --shoreline-layer <shoreline-layer> `
  --class-field kohdeluokka `
  --output app\src\main\assets\finland_sea_grid.bin
```

The source CRS is read from every selected layer and transformed with
`pyproj` using `always_xy=True` to EPSG:3067 (ETRS-TM35FIN). No coordinate
projection is approximated in Python or on Android.

The transformed sea polygons are unioned with one another. The allowed mask
is the intersection of that sea union and the shoreline buffer at at most
`100,000` metres in EPSG:3067. The implementation evaluates the equivalent
distance predicate in bounded tiles instead of materialising the nationwide
buffer at once; this keeps memory use bounded without changing the mask
definition. The result is therefore limited to MML sea polygons within 100 km
of the MML sea shoreline; the buffer cannot turn a lake or inland land area
into sea.

Each 100 m cell is classified using:

```text
cellPolygon.intersection(allowedSea).area > 0
```

The cell centre is not used for classification. The output grid covers the
sea extent relevant to the shoreline mask; cells outside the allowed mask
remain zero. The generator processes bounded tiles, sets bits from positive
intersection areas, and verifies the final file size.

## Binary format v2

All multi-byte values are little-endian. The 40-byte header is:

| Offset | Field | Type/value |
|---:|---|---|
| 0 | magic | ASCII `KSEA` |
| 4 | version | unsigned 16-bit, `2` |
| 6 | header size | unsigned 16-bit, `40` |
| 8 | cell size | unsigned 32-bit, `100` |
| 12 | originX | signed 64-bit EPSG:3067 easting in metres |
| 20 | originY | signed 64-bit EPSG:3067 northing in metres |
| 28 | columns | unsigned 32-bit |
| 32 | rows | unsigned 32-bit |
| 36 | payload bytes | unsigned 32-bit |

The payload index is `row * columns + column`; columns increase eastward and
rows northward. Cell index 0 uses bit 7 of payload byte 0, then bit 6, down to
bit 0. The next cell starts at bit 7 of the next byte. Unused low bits of the
last byte are zero. The payload length is exactly
`ceil(columns * rows / 8)`.

`SeaGridBinaryReader` rejects bad magic, version, header or cell size,
non-positive or overflowing dimensions, implausible EPSG:3067 origins,
payload-size mismatches, non-zero unused bits, and any file whose total length
is not exactly `40 + payload bytes`.

## Runtime and accuracy limits

`FinlandSeaService.fromAssets(context)` loads this file from Android assets.
`isSea(latitude, longitude)` validates WGS84 ranges, transforms the coordinate
from EPSG:4326 to EPSG:3067 with Proj4J, and reads one bit from the grid.
Invalid values, including `NaN`, infinities, and coordinates outside the
latitude/longitude ranges, return `false`.

The result is a conservative 100 m-cell classification: a cell is true even
when only a positive-area sliver intersects the allowed sea. It is not a
precise shoreline test. MML source version, generalisation, download date,
and output dimensions must be recorded alongside each generated asset.

## Previous validated v1 asset (superseded by this v2 update)

The asset was generated on `2026-09-22` from an official MML-derived
GeoPackage in EPSG:3067. The input layers were `sea` and `shoreline`, with
classes `36211` (`Merivesi`) and `30223` (`Meren rantaviiva`). The resulting
file is `169,184` bytes with origin `(59,500, 6,603,500)`, dimensions
`970 × 1,395`, and `169,144` payload bytes; `237,499` payload bits are set.

The following geographic checks were made against this asset:

- `true`: Suomenlahti (`60.0, 25.5`), Pohjanlahti (`63.5, 21.5`), and
  Ahvenanmaan merialue (`60.0, 19.8`), Särkisalo (`60.132489, 22.898085`),
  Hangon edusta ulkona (`59.822092, 22.645795`), Mossala
  (`60.301151, 21.392096`), Porin edusta (`61.608949, 21.136415`),
  Ahvenanmeri (`60.238244, 19.274199`), and Kemijokisuu
  (`65.755745, 24.373240`).
- `false`: Helsinki (`60.1699, 24.9384`), Jyväskylä (`62.2426, 25.7473`),
  Joutsa (`61.74, 26.12`), Suonteen alue (`61.72, 26.35`), Björkboda
  Träsk, Kemiönsaari (`60.077577, 22.569815`), Kauniaisten Gallträsk
  (`60.218908, 24.730277`), Bodomjärvi Eteläranta (`60.241064, 24.661880`),
  Säkylän Pyhäjärvi (`61.000792, 22.300922`), Suontee
  (`61.712211, 26.266856`), Ruotsi, manner (`63.092523, 12.704246`),
  Norjanmeri (`64.622158, 8.925556`), and Sodankylä
  (`67.415421, 26.590946`).
- The projected cell with origin `(379000, 6640000)` has a positive sea
  intersection of approximately `747.7 m²` and is marked true. Its cell-centre
  query coordinate is `59.882119, 24.842572` WGS84, demonstrating that a small
  positive intersection—not the cell centre—is sufficient.

## Validated v2 asset

The v2 asset was generated on `2026-09-24` from the MML-derived 1:1,000,000
source described above. It uses EPSG:3067, origin `(59,700, 6,603,700)`,
`4,844 × 6,971` cells at `100 m`, a `4,220,941`-byte payload and a total
binary size of `4,220,981` bytes. It contains `5,651,494` sea bits out of
`33,767,524` cells. The shoreline-distance predicate is capped at `100,000 m`;
source polygon coverage can reduce the actual extent.
The matching 1-bit PNG preview is `111,214` bytes.

The updated geographic unit test checks the named sea, inland-water, inland,
and out-of-coverage coordinates listed above against this v2 asset. The
positive-area case is the projected cell with origin `(379000, 6640400)`: its
intersection with the sea polygon is `747.69 m²` (`7.48%` of the 10,000 m²
cell), and its centre is WGS84 `59.88385529222567, 24.838885306667358`.

Because a cell is marked when any part of it intersects the allowed geometry,
the query result is a conservative cell-level classification. A point near
the outer buffer edge can be classified true even when its exact coordinate
is outside the geometric 100 km mask, by up to one cell diagonal (about
141 m). This is inherent in the required positive-area cell rule.

### Visual inspection

![Generated Finland sea grid](finland_sea_grid.png)

The image is a 1-bit black-and-white rendering of the binary payload: white
pixels are sea cells, black pixels are other cells. Rows are flipped for
normal image coordinates. Regenerate it after updating the asset:

```text
python tools/render_finland_sea_grid.py
```

## Changing the cell size in a future format

Format v2 and the Android reader intentionally support only `100 m` cells.
Changing the cell size requires coordinated changes to the generator, reader,
fixture and geographic tests, renderer, and format documentation.

1. Estimate the new dimensions and payload before running the full-country
   job. Halving cell width and height makes the number of cells roughly four
   times larger and can substantially increase rasterisation time.
2. Change `CELL_SIZE` in `tools/finland_sea_grid_generator.py` and ensure the
   header records the selected value.
3. Add a new format version and update `FinlandSeaGrid.kt` and
   `SeaGridBinaryReader` together; the current reader must continue rejecting
   unsupported versions and cell sizes.
4. Extend `SeaGridBinaryReaderTest` with new header and boundary cases,
   regenerate geographic expectations from source geometry, and update the
   PNG renderer's dimensions and pixel encoding.
5. Generate to a temporary output, inspect its complete header and
   visualisation, run the geographic checks and full unit-test task, and
   replace the asset only after those checks pass. Record source release,
   command, extent, dimensions, and payload size here.

A 100 m cell size does not make this asset's 1:1,000,000 source geometry more
accurate. The conservative rule remains that any positive-area sea
intersection marks the whole cell.

## Runtime integration boundary

The service is currently safe to keep in the application without changing
existing user flows: it is not referenced by activities, controllers,
repositories, Room, GPS handling or map rendering. `fromAssets(context)` loads
the validated asset only when a caller explicitly creates the service, and
`isSea` is a synchronous, read-only query.

The planned sea-level feature can use `FinlandSeaService.isSea(latitude,
longitude)` as a classification step before requesting or interpreting sea
water-level data. Keep that network lookup and its lifecycle outside this
service; this grid is offline reference data, not a water-level or navigation
source.

## Updating

Use the current MML-derived source workflow above to reproduce this v2 asset.
For improved shoreline accuracy, obtain a bounded full-resolution MML
Maastotietokanta GeoPackage containing both required classes and pass it
directly to the generator; the MML file service requires an API key. Record
the exact source, release/date, CRS, output size, dimensions, payload size,
and geographic test results. Inspect the header and run `SeaGridBinaryReaderTest`
plus `testDebugUnitTest` before committing. Never replace missing source
coverage with hand-written geometry or by treating all water as sea.
