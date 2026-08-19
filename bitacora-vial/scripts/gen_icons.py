"""Generate simple placeholder PWA icons (solid background + road-sign diamond mark).

Stdlib-only PNG encoder (no Pillow available in this environment).
"""
import struct
import zlib
import os

BG = (0x24, 0x22, 0x20)     # charcoal
FG = (0xf2, 0xb7, 0x05)     # amber
STRIPE = (0xe8, 0x60, 0x0c) # orange

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "public", "icons")


def make_icon(size: int) -> bytes:
    pixels = [[BG for _ in range(size)] for _ in range(size)]
    cx = cy = size / 2
    r = size * 0.34

    for y in range(size):
        for x in range(size):
            dx = abs(x - cx) / r
            dy = abs(y - cy) / r
            if dx + dy <= 1.0:
                pixels[y][x] = FG

    band_h = max(1, int(size * 0.05))
    band_y = int(cy + r * 0.12)
    for y in range(band_y, min(size, band_y + band_h)):
        for x in range(size):
            dx = abs(x - cx) / r
            dy = abs(y - cy) / r
            if dx + dy <= 1.0:
                pixels[y][x] = STRIPE

    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for (r_, g_, b_) in row:
            raw += bytes((r_, g_, b_))

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0)
    idat = zlib.compress(bytes(raw), 9)
    png = sig + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b"")
    return png


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for size in (192, 512):
        data = make_icon(size)
        path = os.path.join(OUT_DIR, f"icon-{size}.png")
        with open(path, "wb") as f:
            f.write(data)
        print(f"wrote {path} ({len(data)} bytes)")


if __name__ == "__main__":
    main()
