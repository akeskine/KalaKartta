"""Render the generated Finland sea grid as a PNG image.

The renderer intentionally uses only Python's standard library so that the
visual inspection does not require another GIS or image-processing package.
"""

from __future__ import annotations

import argparse
import struct
import zlib
from pathlib import Path


HEADER_FORMAT = "<4sHHIqqIII"
HEADER_SIZE = 40
MAGIC = b"KSEA"
VERSION = 2
DEFAULT_ASSET = Path("app/src/main/assets/finland_sea_grid.bin")
DEFAULT_OUTPUT = Path("docs/finland_sea_grid.png")


def read_grid(path: Path) -> tuple[int, int, bytes]:
    data = path.read_bytes()
    if len(data) < HEADER_SIZE:
        raise ValueError(f"Grid file is shorter than the {HEADER_SIZE}-byte header")

    magic, version, header_size, cell_size, _origin_x, _origin_y, columns, rows, payload_size = struct.unpack(
        HEADER_FORMAT, data[:HEADER_SIZE]
    )
    if magic != MAGIC or version != VERSION or header_size != HEADER_SIZE:
        raise ValueError("Unsupported grid header")
    if cell_size != 100 or columns == 0 or rows == 0:
        raise ValueError("Invalid grid metadata")
    expected_payload_size = (columns * rows + 7) // 8
    if payload_size != expected_payload_size or len(data) != HEADER_SIZE + payload_size:
        raise ValueError("Grid payload length does not match its header")
    return columns, rows, data[HEADER_SIZE:]


def png_chunk(chunk_type: bytes, payload: bytes) -> bytes:
    chunk = chunk_type + payload
    return struct.pack(">I", len(payload)) + chunk + struct.pack(">I", zlib.crc32(chunk) & 0xFFFFFFFF)


def render_png(columns: int, rows: int, payload: bytes, output: Path) -> None:
    row_bytes = (columns + 7) // 8
    scanlines = bytearray()

    # The binary grid grows northward from originY. PNG rows grow downward, so
    # the first image row must use the northernmost grid row.
    for image_row in range(rows):
        grid_row = rows - 1 - image_row
        row = bytearray(row_bytes)
        scanlines.append(0)
        for column in range(columns):
            index = grid_row * columns + column
            is_sea = payload[index // 8] & (1 << (7 - index % 8))
            if is_sea:
                row[column // 8] |= 1 << (7 - column % 8)
        scanlines.extend(row)

    png = bytearray(b"\x89PNG\r\n\x1a\n")
    png.extend(png_chunk(b"IHDR", struct.pack(">IIBBBBB", columns, rows, 1, 0, 0, 0, 0)))
    png.extend(png_chunk(b"IDAT", zlib.compress(bytes(scanlines), level=9)))
    png.extend(png_chunk(b"IEND", b""))
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(png)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("asset", nargs="?", type=Path, default=DEFAULT_ASSET)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    columns, rows, payload = read_grid(args.asset)
    render_png(columns, rows, payload, args.output)
    print(f"Wrote {args.output} ({columns} x {rows})")


if __name__ == "__main__":
    main()