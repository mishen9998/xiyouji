# Responsive game artwork

All 203 existing originals remain under `assets/images/`; this tool never rewrites them.
The committed `frontend-vue/public/illustrations/` outputs work without Node/Sharp at runtime.

Rebuild with Node 20.18.1 or newer:

```powershell
npm --prefix frontend-vue/scripts ci
npm --prefix frontend-vue/scripts run images
```

`sharp@0.33.5` is pinned in this isolated tooling package, not added to the game runtime.
The pipeline creates 320 / 640 / 960 width WebP and AVIF variants without upscaling,
reduces quality to enforce a 200 KiB per-image budget, and writes source SHA-256 hashes
and exact sizes into `src/constants/image-manifest.json` and `public/illustrations/manifest.json`.
It fails instead of silently emitting an over-budget file. The whole catalog is metadata,
not a request to load every image.

## UI API

- Existing `cardImgUrl`, `fullImgUrl`, `enemyImgUrl`, `relicImgUrl`, `nodeImgUrl` use 320w WebP.
- `imageUrl(sourceOrDerivedUrl, width)` selects a WebP variant for CSS / legacy `<img>`.
- `ResponsiveImage` uses AVIF then WebP, intrinsic ratio, asynchronous decoding, and a
  fixed-space emoji fallback. Pass accurate `sizes`; `critical` enables eager/high priority.
- `sceneImageUrl(key, width=960)` supports journey, camp, blackwind, firemountain,
  lionridge, completion, blackbear, bullking, roc.
- `preloadImages(urls, { width, limit })` / `preloadScene(key, width)` only add typed AVIF
  hints for the explicit next scene, never bulk-download the library. Avoid preloading
  sizes different from the next view. Hidden tabs / data-saving mode skip hints.

## Nine T6 generated assets

The nine actual built-in imagegen PNG originals now live in `assets/images/旅程/`.
The centralized mapping uses these sources for journey, camp, three chapter arenas,
completion and three transparent Boss portraits. Exact prompts, reference roles and
generator provenance are in `docs/art/t6-scene-prompts.md` and `t6-boss-prompts.md`.
All 203 pre-existing source files remain untouched. Boss portraits use `contain` and
alpha-preserving AVIF/WebP variants, not circular cropping or an artificial checkerboard.

After regeneration, run the read-only provenance/alpha/size audit:

```powershell
node frontend-vue/scripts/verify-art.mjs
```

Each final source has three widths in both formats (54 new runtime files). The audit
checks nine distinct source hashes, decoded dimensions, real alpha, and actual file
sizes against the 200 KiB budget. Do not confuse the whole catalog's bytes with
first-screen transfers; use the controlled browser performance test for network data.
