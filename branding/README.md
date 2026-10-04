# Packet rhythm app icon

Source: https://github.com/yuhaiin/yuhaiin/blob/main/assets/icon-candidates/17-packet-rhythm.svg

`packet-rhythm.svg` preserves the upstream artwork. Android uses:

- Adaptive icon: the colored foreground scaled into the safe area on `#FFF3EE`.
- Android 13 themed icon and notification: a separate monochrome foreground with no solid background circle.
- About: the full colored artwork, without Material icon tint.
- Legacy launcher and Play Store PNGs: rendered from the original SVG.

To regenerate the raster assets using librsvg, run `python3 scripts/render-launcher-icons.py` from the repository root. Vector resources are reviewed separately because adaptive and monochrome composition differs from the source SVG.
