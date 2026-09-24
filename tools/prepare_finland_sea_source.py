"""Extract MML sea water and shoreline classes from its 1:1,000,000 ZIP."""

from __future__ import annotations

import argparse
from pathlib import Path

import fiona
from pyproj import CRS


TARGET_CRS = CRS.from_epsg(3067)
CLASS_FIELD = "Kohdeluokk"
LAYERS = (
    ("VesiAlue.shp", "sea", 36211),
    ("Maasto1Reuna.shp", "shoreline", 30223),
)


def source_layer(source_zip: Path, layer_name: str) -> str:
    return f"zip://{source_zip.resolve().as_posix()}!{layer_name}"


def prepare_source(source_zip: Path, output: Path) -> None:
    if not source_zip.is_file():
        raise ValueError(f"Source ZIP does not exist: {source_zip}")
    if output.exists():
        raise ValueError(f"Output already exists; remove it or choose another path: {output}")
    output.parent.mkdir(parents=True, exist_ok=True)

    with fiona.Env():
        with fiona.open(source_layer(source_zip, LAYERS[0][0])) as first_source:
            source_crs = CRS.from_user_input(first_source.crs_wkt or first_source.crs)
            if not source_crs.to_2d().equals(TARGET_CRS):
                raise ValueError(f"Expected an EPSG:3067 horizontal CRS, got {source_crs}")

        counts: list[int] = []
        for index, (source_name, output_layer, class_code) in enumerate(LAYERS):
            with fiona.open(source_layer(source_zip, source_name)) as source:
                if CLASS_FIELD not in source.schema.get("properties", {}):
                    raise ValueError(f"{source_name} has no {CLASS_FIELD} field")
                schema = {
                    "geometry": source.schema["geometry"],
                    "properties": {"classcode": "int"},
                }
                open_options = {
                    "driver": "GPKG",
                    "layer": output_layer,
                    "schema": schema,
                    "crs": "EPSG:3067",
                }
                if index:
                    open_options["append_subdataset"] = True
                count = 0
                with fiona.open(output, "w", **open_options) as destination:
                    for feature in source:
                        if int(feature["properties"][CLASS_FIELD]) != class_code:
                            continue
                        geometry = feature.get("geometry")
                        if geometry is None:
                            raise ValueError(
                                f"{source_name} has class {class_code} without geometry"
                            )
                        destination.write(
                            {
                                "geometry": geometry,
                                "properties": {"classcode": class_code},
                            }
                        )
                        count += 1
                if count == 0:
                    raise ValueError(f"{source_name} contains no class {class_code} features")
                counts.append(count)

    print(
        f"Wrote {output}: {counts[0]} sea polygons (36211), "
        f"{counts[1]} shoreline lines (30223), horizontal CRS EPSG:3067"
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_zip", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    prepare_source(args.source_zip, args.output)


if __name__ == "__main__":
    main()