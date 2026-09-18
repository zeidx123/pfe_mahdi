#!/usr/bin/env python3
"""Generate small abstract PNGs for API transport checks, never accuracy evaluation."""
import struct
import zlib
from pathlib import Path


def chunk(kind, data):
    return struct.pack("!I", len(data)) + kind + data + struct.pack("!I", zlib.crc32(kind + data) & 0xffffffff)


def image(square_color):
    width = height = 64
    rows = []
    for y in range(height):
        row = bytearray([0])  # PNG filter: none.
        for x in range(width):
            color = square_color if 20 <= x < 44 and 20 <= y < 44 else (220, 225, 232)
            row.extend(color)
        rows.append(row)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack("!IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(b"".join(rows))) + chunk(b"IEND", b""))


if __name__ == "__main__":
    destination = Path(__file__).resolve().parent
    (destination / "abstract-before.png").write_bytes(image((80, 100, 180)))
    (destination / "abstract-after.png").write_bytes(image((180, 110, 80)))
    print("Generated two abstract PNGs for unreviewed transport checks only.")
