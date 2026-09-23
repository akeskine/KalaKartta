#!/usr/bin/env python3
"""Generate the FinlandSeaService v1 grid from MML GeoPackage data.

The generator deliberately accepts only the MML sea-water class and the MML
sea-shoreline class. It never treats all water polygons as sea.
"""

from __future__ import annotations

import argparse
import math
import struct
import sys
from pathlib import Path
from typing import Iterable

try:
    import fiona
    import numpy as np
    from pyproj import CRS, Transformer
    from shapely import area as geometry_area
    from shapely import box, clip_by_rect, intersection as geometry_intersection
    from shapely.geometry import shape
    from shapely.ops import transform, unary_union
    from shapely.strtree import STRtree
except ImportError as error:  # pragma: no cover - exercised by the CLI environment
    raise SystemExit(
        "Missing generator dependency. Install tools/requirements-finland-sea-grid.txt "
        f"before running the generator ({error})."
    ) from error


TARGET_CRS = CRS.from_epsg(3067)
CELL_SIZE = 500
BUFFER_DISTANCE = 50_000
HEADER_SIZE = 40
MAGIC = b"KSEA"
VERSION = 1
BATCH_ROWS = 64
RASTER_TILE_SIZE = 100_000
DEFAULT_OUTPUT = Path("app/src/main/assets/finland_sea_grid.bin")
SEA_CLASS = "36211"
SHORELINE_CLASS = "30223"
CLASS_FIELD_NAMES = (
    "kohdeluokka",
    "kohdeluokan",
    "luokka",
    "classcode",
    "class_code",
)


class GeneratorError(RuntimeError):
    """Raised when the source cannot be safely converted to a sea grid."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="MML Maastotietokanta GeoPackage")
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT,
        help=f"Output grid path (default: {DEFAULT_OUTPUT})",
    )
    parser.add_argument(
        "--sea-layer",
        help="GeoPackage layer containing Merivesi (36211); auto-detected when omitted",
    )
    parser.add_argument(
        "--shoreline-layer",
        help="GeoPackage layer containing Meren rantaviiva (30223); auto-detected when omitted",
    )
    parser.add_argument(
        "--class-field",
        help="Class-code field; auto-detected from MML field names when omitted",
    )
    parser.add_argument("--sea-class", default=SEA_CLASS, help=f"Sea class code (default: {SEA_CLASS})")
    parser.add_argument(
        "--shoreline-class",
        default=SHORELINE_CLASS,
        help=f"Shoreline class code (default: {SHORELINE_CLASS})",
    )
    return parser.parse_args()


def normalise_code(value: object) -> str:
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    return str(value).strip()


def find_class_field(properties: dict[str, object]) -> str | None:
    fields = {field.lower().replace("_", ""): field for field in properties}
    for candidate in CLASS_FIELD_NAMES:
        field = fields.get(candidate.replace("_", ""))
        if field is not None:
            return field
    return None


def layer_candidates(
    source_path: Path,
    requested_layer: str | None,
    class_code: str,
    name_hints: Iterable[str],
) -> list[str]:
    layers = list(fiona.listlayers(source_path))
    if requested_layer is not None:
        if requested_layer not in layers:
            raise GeneratorError(
                f"Requested layer {requested_layer!r} is not present. Available layers: {layers}"
            )
        return [requested_layer]

    hints = tuple(hint.lower() for hint in name_hints)
    named = [
        layer
        for layer in layers
        if class_code.lower() in layer.lower() or any(hint in layer.lower() for hint in hints)
    ]
    if named:
        return named

    candidates: list[str] = []
    for layer in layers:
        with fiona.open(source_path, layer=layer) as collection:
            properties = collection.schema.get("properties", {})
            if find_class_field(properties) is not None:
                candidates.append(layer)
    if not candidates:
        raise GeneratorError(
            f"No layer with an MML class-code field was found for class {class_code}. "
            "Use --sea-layer/--shoreline-layer or check the GeoPackage."
        )
    return candidates


def source_crs(collection: fiona.Collection, layer: str) -> CRS:
    raw_crs = collection.crs_wkt or collection.crs
    if not raw_crs:
        raise GeneratorError(f"Layer {layer!r} has no CRS; refusing to guess one")
    try:
        return CRS.from_user_input(raw_crs)
    except Exception as error:
        raise GeneratorError(f"Layer {layer!r} has an invalid CRS: {error}") from error


def transformed_features(
    source_path: Path,
    layers: list[str],
    requested_field: str | None,
    class_code: str,
    geometry_kind: str,
) -> list[object]:
    geometries: list[object] = []
    found_class = False
    for layer in layers:
        with fiona.open(source_path, layer=layer) as collection:
            crs = source_crs(collection, layer)
            transformer = Transformer.from_crs(crs, TARGET_CRS, always_xy=True)
            properties = collection.schema.get("properties", {})
            class_field = requested_field or find_class_field(properties)
            if class_field is None:
                if requested_field is not None:
                    raise GeneratorError(
                        f"Layer {layer!r} has no requested class field {requested_field!r}"
                    )
                continue
            if class_field not in properties:
                raise GeneratorError(f"Layer {layer!r} has no class field {class_field!r}")

            for feature in collection:
                values = feature.get("properties") or {}
                if normalise_code(values.get(class_field)) != class_code:
                    continue
                found_class = True
                raw_geometry = feature.get("geometry")
                if raw_geometry is None:
                    raise GeneratorError(
                        f"Layer {layer!r} contains class {class_code} without geometry"
                    )
                geometry = shape(raw_geometry)
                if geometry.is_empty or not geometry.is_valid:
                    raise GeneratorError(
                        f"Layer {layer!r} contains empty or invalid class {class_code} geometry"
                    )
                if geometry_kind == "polygon" and geometry.geom_type not in {
                    "Polygon",
                    "MultiPolygon",
                }:
                    raise GeneratorError(
                        f"Layer {layer!r} class {class_code} is {geometry.geom_type}, expected polygon"
                    )
                if geometry_kind == "line" and geometry.geom_type not in {
                    "LineString",
                    "MultiLineString",
                }:
                    raise GeneratorError(
                        f"Layer {layer!r} class {class_code} is {geometry.geom_type}, expected line"
                    )
                geometries.append(transform(transformer.transform, geometry))

    if not found_class:
        raise GeneratorError(
            f"No geometry with MML class {class_code} was found in layers {layers}"
        )
    return geometries


def build_allowed_sea(
    source_path: Path,
    sea_layers: list[str],
    shoreline_layers: list[str],
    class_field: str | None,
    sea_class: str,
    shoreline_class: str,
) -> object:
    sea_geometries = transformed_features(
        source_path, sea_layers, class_field, sea_class, "polygon"
    )
    shoreline_geometries = transformed_features(
        source_path, shoreline_layers, class_field, shoreline_class, "line"
    )
    sea = unary_union(sea_geometries)
    shoreline = unary_union(shoreline_geometries)
    if sea.is_empty or shoreline.is_empty:
        raise GeneratorError("The MML sea or shoreline geometry is empty")

    if sea.distance(shoreline) > BUFFER_DISTANCE:
        raise GeneratorError("The 50 km shoreline mask has no positive-area sea geometry")

    shoreline_parts = []
    if shoreline.geom_type == "LineString":
        shoreline_parts.append(shoreline)
    elif shoreline.geom_type == "MultiLineString":
        shoreline_parts.extend(shoreline.geoms)
    else:
        raise GeneratorError(f"Unexpected shoreline geometry after union: {shoreline.geom_type}")
    if not shoreline_parts:
        raise GeneratorError("The 50 km shoreline mask has no line geometry")

    # A global 50 km buffer of the nationwide shoreline can require several
    # gigabytes of temporary geometry. The distance predicate below is
    # mathematically equivalent to intersecting with that buffer, while the
    # STRtree keeps only the shoreline pieces relevant to each cell batch.
    return sea, shoreline_parts


def rasterise(mask: tuple[object, list[object]]) -> tuple[int, int, int, int, bytearray]:
    sea, shoreline_parts = mask
    sea_min_x, sea_min_y, sea_max_x, sea_max_y = sea.bounds
    shoreline_min_x, shoreline_min_y, shoreline_max_x, shoreline_max_y = unary_union(
        shoreline_parts
    ).bounds
    min_x = max(sea_min_x, shoreline_min_x - BUFFER_DISTANCE)
    min_y = max(sea_min_y, shoreline_min_y - BUFFER_DISTANCE)
    max_x = min(sea_max_x, shoreline_max_x + BUFFER_DISTANCE)
    max_y = min(sea_max_y, shoreline_max_y + BUFFER_DISTANCE)
    origin_x = math.floor(min_x / CELL_SIZE) * CELL_SIZE
    origin_y = math.floor(min_y / CELL_SIZE) * CELL_SIZE
    columns = max(1, math.ceil((max_x - origin_x) / CELL_SIZE))
    rows = max(1, math.ceil((max_y - origin_y) / CELL_SIZE))
    cell_count = columns * rows
    payload_size = (cell_count + 7) // 8
    if columns > 0x7FFFFFFF or rows > 0x7FFFFFFF or payload_size > 0xFFFFFFFF:
        raise GeneratorError("The generated grid is too large for the v1 format")

    payload = np.zeros(payload_size, dtype=np.uint8)
    shoreline_array = np.asarray(shoreline_parts, dtype=object)
    shoreline_tree = STRtree(shoreline_array)
    tile_cells = RASTER_TILE_SIZE // CELL_SIZE
    tile_columns = math.ceil(columns / tile_cells)
    tile_rows = math.ceil(rows / tile_cells)

    # A global 50 km buffer of the nationwide shoreline can require several
    # gigabytes of temporary geometry. Each tile instead uses the distance
    # predicate against only its nearby shoreline pieces; this is equivalent
    # to intersecting the sea geometry with the 50 km shoreline buffer.
    for tile_row in range(tile_rows):
        row_start = tile_row * tile_cells
        row_end = min(rows, row_start + tile_cells)
        tile_min_y = origin_y + row_start * CELL_SIZE
        tile_max_y = origin_y + row_end * CELL_SIZE
        for tile_column in range(tile_columns):
            column_start = tile_column * tile_cells
            column_end = min(columns, column_start + tile_cells)
            tile_min_x = origin_x + column_start * CELL_SIZE
            tile_max_x = origin_x + column_end * CELL_SIZE
            expanded_tile = box(
                tile_min_x - BUFFER_DISTANCE,
                tile_min_y - BUFFER_DISTANCE,
                tile_max_x + BUFFER_DISTANCE,
                tile_max_y + BUFFER_DISTANCE,
            )
            local_line_indices = shoreline_tree.query(expanded_tile, predicate="intersects")
            if len(local_line_indices) == 0:
                continue

            print(
                f"Rasterizing tile {tile_row + 1}/{tile_rows}, "
                f"{tile_column + 1}/{tile_columns}",
                file=sys.stderr,
                flush=True,
            )
            local_shoreline = shoreline_array[local_line_indices]
            local_tree = STRtree(local_shoreline)
            local_sea = clip_by_rect(sea, tile_min_x, tile_min_y, tile_max_x, tile_max_y)
            if local_sea.is_empty:
                continue

            local_columns = np.arange(column_start, column_end, dtype=np.int64)
            x0 = origin_x + local_columns * CELL_SIZE
            x1 = x0 + CELL_SIZE
            for batch_start in range(row_start, row_end, BATCH_ROWS):
                batch_end = min(row_end, batch_start + BATCH_ROWS)
                local_rows = np.arange(batch_start, batch_end, dtype=np.int64)
                y0 = origin_y + local_rows * CELL_SIZE
                y1 = y0 + CELL_SIZE
                cells = box(
                    x0[np.newaxis, :],
                    y0[:, np.newaxis],
                    x1[np.newaxis, :],
                    y1[:, np.newaxis],
                )
                sea_cells = geometry_intersection(cells, local_sea)
                positive_area = geometry_area(sea_cells) > 0
                if not np.any(positive_area):
                    continue

                candidate_rows, candidate_columns = np.nonzero(positive_area)
                candidate_sea_cells = sea_cells[positive_area]
                nearby_pairs = local_tree.query(
                    candidate_sea_cells,
                    predicate="dwithin",
                    distance=BUFFER_DISTANCE,
                )
                if nearby_pairs.shape[1] == 0:
                    continue

                nearby_cells = np.zeros(len(candidate_sea_cells), dtype=bool)
                nearby_cells[np.unique(nearby_pairs[0])] = True
                selected_rows = local_rows[candidate_rows[nearby_cells]]
                selected_columns = local_columns[candidate_columns[nearby_cells]]
                indices = selected_rows * columns + selected_columns
                np.bitwise_or.at(
                    payload,
                    indices // 8,
                    (1 << (7 - indices % 8)).astype(np.uint8),
                )

    return origin_x, origin_y, columns, rows, bytearray(payload.tobytes())


def write_grid(
    output_path: Path,
    origin_x: int,
    origin_y: int,
    columns: int,
    rows: int,
    payload: bytearray,
) -> None:
    expected_payload_size = (columns * rows + 7) // 8
    if len(payload) != expected_payload_size:
        raise GeneratorError("Internal error: generated payload size is inconsistent")
    header = struct.pack(
        "<4sHHIqqIII",
        MAGIC,
        VERSION,
        HEADER_SIZE,
        CELL_SIZE,
        origin_x,
        origin_y,
        columns,
        rows,
        len(payload),
    )
    if len(header) != HEADER_SIZE:
        raise GeneratorError("Internal error: v1 header is not 40 bytes")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(header + payload)
    expected_size = HEADER_SIZE + expected_payload_size
    if output_path.stat().st_size != expected_size:
        raise GeneratorError("Generated file length does not match its header")


def generate(args: argparse.Namespace) -> None:
    if not args.input.is_file():
        raise GeneratorError(f"Input GeoPackage does not exist: {args.input}")
    if args.sea_class != SEA_CLASS or args.shoreline_class != SHORELINE_CLASS:
        raise GeneratorError(
            "This generator is restricted to MML Merivesi (36211) and "
            "Meren rantaviiva (30223); custom classes are not permitted"
        )

    sea_layers = layer_candidates(
        args.input, args.sea_layer, args.sea_class, ("merivesi", "meri", "alue")
    )
    shoreline_layers = layer_candidates(
        args.input,
        args.shoreline_layer,
        args.shoreline_class,
        ("rantaviiva", "shoreline", "reunaviiva"),
    )
    allowed_sea = build_allowed_sea(
        args.input,
        sea_layers,
        shoreline_layers,
        args.class_field,
        args.sea_class,
        args.shoreline_class,
    )
    origin_x, origin_y, columns, rows, payload = rasterise(allowed_sea)
    write_grid(args.output, origin_x, origin_y, columns, rows, payload)
    print(
        f"Wrote {args.output} ({args.output.stat().st_size} bytes): "
        f"origin=({origin_x}, {origin_y}), dimensions={columns}x{rows}, "
        f"payload={len(payload)} bytes"
    )


def main() -> int:
    args = parse_args()
    try:
        generate(args)
    except GeneratorError as error:
        print(f"finland_sea_grid_generator: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())