# T6 场景素材：内置 imagegen 生成记录

Mode: built-in imagegen (not CLI/API fallback). All six outputs are original raster PNGs, visually inspected before integration. No text/UI is baked into any background. The first image is the approved style reference; the other five are distinct new scenes using it, not copies/recolors. The three Boss assets and exact first/revision prompts are documented in [t6-boss-prompts.md](t6-boss-prompts.md).

All final source files are retained in `assets/images/旅程`. Runtime files are WebP/AVIF derivatives from `frontend-vue/scripts/derive-images.mjs`, keyed through `frontend-vue/src/constants/scene-images.json`. `verify-art.mjs` verifies source hashes, dimensions, bytes and real alpha preservation; it does not modify pixels.

Default generator directory: `C:/Users/91miking/.codex/generated_images/01a0ac59-ecc2-7280-85a2-637b4672e141/`.

| Key | Original generator file | Final preserved source |
|---|---|---|
| journey | exec-07a69ce0-d276-4e5c-bbb2-b0bdd512dc5a.png | assets/images/旅程/journey.png |
| camp | exec-3cbc0cbc-4ee3-4f33-b9a4-06382eb96202.png | assets/images/旅程/camp.png |
| blackwind | exec-fbef3294-263c-44b3-866e-16996cf2f92f.png | assets/images/旅程/blackwind.png |
| firemountain | exec-f966fe03-2aea-4f6b-9e39-3bd552770d3c.png | assets/images/旅程/firemountain.png |
| lionridge | exec-783f575d-abb9-4d37-b946-ec521664fb20.png | assets/images/旅程/lionridge.png |
| completion | exec-c4fe7bb0-3133-4d9a-93b4-335c2ac8ca00.png | assets/images/旅程/completion.png |

## Exact prompt: journey (no input reference)

```text
Use case: stylized-concept
Asset type: polished wide game title / journey panorama background, approximately 16:9 landscape, for a Journey to the West co-op card adventure.
Primary request: A bright Chinese fantasy landscape showing the westward pilgrimage as one elegant continuous panoramic world: emerald Black Wind Mountain with a small vermilion-roof monastery; warm red-orange Flame Mountain in the middle distance; sculptural blue-green Lion Ridge peaks leading to a luminous golden Buddhist sanctuary in far-away clouds. A small winding ochre path threads between these landscapes. No characters needed.
Style/medium: premium hand-painted Chinese color-ink / gouache illustration on subtle warm rice-paper texture; controlled expressive brush contours, simplified readable silhouettes, layered atmospheric mountains, flowing cloud motifs, inviting adventure storybook feeling, not dark gritty realism, not 3D.
Composition: wide balanced scenery, spacious pale ivory sky across upper third and central distance; most detail along lower and outer regions to keep game DOM titles and controls readable; scene should crop gracefully for mobile. Full bleed image, no frame.
Palette: warm ivory parchment, jade / teal greens, vermilion accents, restrained antique gold; luminous daylight, cheerful and calm rather than desaturated or black.
Constraints: no text of any language, no calligraphy, no letters, no numbers, no UI, no buttons, no logo, no signature, no watermark, no map markers. Actual raster illustration. This is the style anchor for eight related assets.
```

## Exact prompt: camp (reference: journey.png)

```text
Use case: stylized-concept
Asset type: wide 16:9 cooperative game camp background.
Input image 1 is only a STYLE reference, not an edit target. Create a distinct new scene using the same refined Chinese gouache/color-ink brushwork, warm rice paper, jade/teal, vermilion and modest antique gold palette.
Scene: a welcoming pilgrimage camp in a sunlit mountain clearing, a small vermilion-roof roadside pavilion, five simple empty travel seating mats around a modest cooking fire and kettle, packed travel bundles beside the pavilion, a winding path toward distant jade mountains and flowing cream clouds. No people or creatures.
Composition: environment artwork only, full bleed wide landscape. Place decorative pavilion and trees around edges, center is spacious soft ivory clearing for actual multiplayer roster interface. Upper region luminous and uncluttered. Calm daylight, friendly adventure, clearly readable hand-painted forms rather than photorealistic/dark/cinematic.
Constraints: Absolutely no text, calligraphy, numbers, logos, signatures, watermark, map icons, UI or buttons. Do not reproduce the anchor panorama; this is a separate intimate camp scene.
```

## Exact prompt: blackwind (reference: journey.png)

```text
Use case: stylized-concept
Asset type: wide 16:9 turn-based card combat environment background.
Input image 1 is STYLE REFERENCE only. Make a distinct close ground-level Black Wind Mountain battlefield using the anchor's bright refined Chinese color-ink/gouache brushwork and warm paper texture.
Scene: a clear ochre stone clearing beside an ancient vermilion monastery wall and gate, emerald bamboo and sculpted jade pine cliffs, a distant soft waterfall and cream cloud ribbons. No enemies, no heroes, no people.
Composition: full bleed landscape. Clear wide empty center and lower middle area as arena, structures and tree details at lateral edges, distant mountain depth and pale ivory sky. Visual stage for DOM character portraits at center; softly controlled contrast so status and cards remain legible. Luminous morning, energetic welcoming adventure, not dark haunted forest.
Palette: warm ivory, jade and teal, accents of vermilion and restrained old gold.
Constraints: no text/calligraphy/numbers, no UI, no logos/signatures/watermarks, no fighting figures. This is a newly generated Black Wind Mountain arena, not the panorama.
```

## Exact prompt: firemountain (reference: journey.png)

```text
Use case: stylized-concept
Asset type: wide 16:9 turn-based card combat environment, Flame Mountain.
Input image 1 is only a style reference: create a genuinely new near-ground arena with the same hand-painted Chinese color-ink/gouache brushwork on warm rice paper.
Scene: an open pale ochre stone plateau amid sculpted vermilion/red-orange mountain ridges, restrained flowing stylized flame shapes along far crags, cream-gold cloud ribbons and luminous ivory sky, a distant small jade oasis and green banana leaves at one edge referencing the fan-borrowing journey. Adventurous and warm, never dark hellscape.
Composition: wide full bleed illustration, horizon above center, broad empty center and lower arena to receive real UI portraits/cards; frame with simple rocks at edges, clear silhouettes, atmospheric depth and restrained detail under battle numbers. No people or enemies.
Palette: ivory and ochre ground, vermilion mountains, teal/green accents, antique gold light; bright readable daylight.
Constraints: no text, calligraphy, labels, numbers, icons, UI, buttons, signature, logos or watermark. Distinct Flame Mountain location, not a recolored copy of the reference panorama.
```

## Exact prompt: lionridge (reference: journey.png)

```text
Use case: stylized-concept
Asset type: wide 16:9 turn-based card battle background for Lion Ridge to Spirit Mountain.
Input image 1 is only a style reference. Create a new intimate battlefield in the same bright Chinese painted color-ink/gouache art on warm ivory rice-paper, precise expressive contours and atmospheric blue-green peaks.
Scene: an elevated ancient stone terrace above cloud sea, flanked by weathered lion-shaped stone formations and jade pine trees, teal crags and a carved stone arch behind, far gold-lit temple roofs glimpsed in luminous cream clouds. Rescue-adventure mood: majestic and hopeful, not horror. No bodies, people, enemies or heroes.
Composition: full bleed wide landscape, broad open warm-ivory stone arena in central/lower half, low-detail center for real battle interface; detailed lion stones and sparse vermilion cloth trim only along sides. Pale sky, calm depth, consistent scale with Black Wind/Flame Mountain arenas.
Palette: jade, turquoise, warm parchment, small vermilion accents and old gold sunlight.
Constraints: no text, inscriptions, calligraphy, numbers, UI, logo, signature or watermark. Not a recolor of panorama; this is a unique Lion Ridge stone terrace.
```

## Exact prompt: completion (reference: journey.png)

```text
Use case: illustration-story
Asset type: wide 16:9 game completion illustration, arrival at Spirit Mountain.
Input image 1 is STYLE reference only; create a new final scene consistent with its luminous Chinese color-ink/gouache, warm paper texture, jade/teal, vermilion and antique gold palette.
Primary scene: pilgrimage fulfilled at a magnificent golden Buddhist temple rising from a sea of cream clouds. Wide ivory stairway and open vermilion gates lead toward radiant temple roofs, jade mountains now far below, delicate golden cloud curls and a few pale lotus blossoms near the lower corners. Solemn joy and relief after adventure, bright and welcoming daylight, not gloomy or photorealistic.
Composition: an inviting central path into the temple, landscape full bleed. Golden architecture in upper half and balanced open pale foreground for real DOM completion message, room for responsive cropping. No people; celebration conveyed by sunrise, open gates and light.
Constraints: no text, no Chinese characters, no calligraphy, no numbers, no logos or signatures, no watermark, no UI, no trophies or buttons. Do not copy anchor panorama; this is a close arrival view at the destination.
```
