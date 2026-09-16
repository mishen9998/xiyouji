# T6 Boss 立绘生成记录

使用内置 image_gen（未调用 CLI/API fallback），遵循 imagegen skill。三张立绘均为新生成的项目素材；原始生成输出保留在 Codex generated_images，下列最终源图复制到项目后未做抠图、绘制、重采样或 alpha 修改。

## 风格参考

`assets/images/旅程/journey.png` 是唯一首轮风格参考，已由执行代理通过 view_image 查看。明快国风、彩墨/gouache、青绿朱红与适量金；真实透明背景；无文字或水印。

## 最终源图

|角色|项目路径|内置工具最终输出|
|---|---|---|
|黑熊精|assets/images/旅程/blackbear.png|C:/Users/91miking/.codex/generated_images/01a0ac5c-055f-75e3-9906-6c434204ca63/exec-c450ee3e-e34a-46a4-8134-1c6162b074ab.png|
|牛魔王|assets/images/旅程/bullking.png|C:/Users/91miking/.codex/generated_images/01a0ac5c-055f-75e3-9906-6c434204ca63/exec-1bb32301-0e6e-4ade-8078-b61d454a558e.png|
|大鹏|assets/images/旅程/roc.png|C:/Users/91miking/.codex/generated_images/01a0ac5c-055f-75e3-9906-6c434204ca63/exec-532d6a32-c1da-4635-a965-3160ae589bf1.png|

首轮输出经过检查后发现构图偏贴边，故每图做一次仅构图修正的 imagegen 编辑。黑熊精同时改为朝左；大鹏补全武器尖端。最终三图再次由 view_image 逐一查看：全身和武器完整，朝左，无额外场景或地面，视觉上与总图风格一致。

## 黑熊精：首轮提示词

```text
Use case: stylized-concept
Asset type: transparent full-body boss character illustration for a Chinese Journey to the West card adventure game.
Input image 1: style reference only. Create a NEW character asset, not an edit of the landscape. Match the reference's bright Chinese mineral-color ink and gouache brushwork, readable illustrated details, teal/green, warm ivory, vermilion and restrained gold palette.
Subject: 黑熊精, the Black Bear Demon of Black Wind Mountain: powerful bulky anthropomorphic black bear with unmistakable rounded bear ears, black fur, long bear muzzle, large paws; dressed in tasteful teal armor and an asymmetrically draped vermilion Buddhist-style kasaya robe with gold edges. Holds one long black-shaft spear with a simple silver spearhead. A formidable but adventure-friendly boss, not horror, not a human face.
Composition: single full-body figure standing in a strong grounded three-quarter stance facing slightly toward viewer-left. Entire head, ears, feet, robe and spear must fit inside the image with generous clear padding on all four sides. Clean easily readable silhouette at small game size; 2D painterly rendering with visible ink/gouache strokes, no photo, no 3D.
Background: genuinely transparent RGBA alpha. Character and spear only. No floor, scenery, pedestal, cast ground shadow, background haze, painted paper rectangle or simulated checkerboard.
Constraints: absolutely no text, letters, calligraphy, UI, logo, watermark, border, extra characters or cropped parts. Preserve actual alpha transparency in the output PNG.
```

### 定向修改提示词

```text
Use case: precise-object-edit.
Input image 1 is the edit target: the existing Black Bear Demon game cutout.
Change only composition and facing direction: mirror the entire complete character horizontally so his face looks toward the LEFT edge of the image; preserve exact bear identity, proportions, spear, robe, armor, colors and painterly texture. Then zoom OUT the entire figure so both feet, full spear tip and every fabric edge are comfortably inside a roomy SQUARE transparent canvas. Subject and weapon together occupy only the CENTRAL 75 PERCENT of the canvas height and width. A clear fully transparent margin surrounds the entire figure on every side. Do not crop to the alpha bounding box.
Maintain genuinely transparent RGBA PNG background, no new scenery, floor, shadow, checkerboard, backdrop or opaque rectangle. No new objects, lettering, logo or watermark. Keep all other visual details unchanged.
```

## 牛魔王：首轮提示词

```text
Use case: stylized-concept
Asset type: transparent full-body boss character illustration for a Chinese Journey to the West card adventure game.
Input image 1: style reference only. Create a NEW character asset, not an edit of the landscape. Match the reference's bright Chinese mineral-color ink and gouache brushwork, readable painterly details, teal/green, warm ivory, vermilion and restrained gold palette.
Subject: 牛魔王, the Bull Demon King: imposing anthropomorphic white bull demon, unmistakable white bovine head and muzzle, two strong curved horns, mighty muscular body, ornate Chinese dark teal heavy armor with restrained warm gold fittings and vermilion cloth. One broad heavy Chinese dao blade in hand. A formidable adventure boss, not horror, not a human face.
Composition: one full-body standing character in a steady battle-ready three-quarter stance. Face and chest turn toward the LEFT EDGE OF THE IMAGE. Both feet, both horns, head, blade and all cloth fully contained, with at least 8 percent transparent empty padding on ALL four sides. Readable iconic silhouette when scaled small; hand-painted 2D ink/gouache style, not photography, not 3D.
Background: genuinely transparent RGBA alpha. Character and dao weapon only. No environment, floor, pedestal, ground shadow, paper panel, opaque white backdrop, haze or drawn checkerboard.
Constraints: no text, calligraphy, UI, logo, watermark, border, extra character or cropped parts. Preserve actual alpha transparency in output PNG.
```

### 定向修改提示词

```text
Use case: precise-object-edit.
Input image 1 is the edit target: the existing white Bull Demon King game cutout.
Change ONLY framing: zoom OUT the entire figure so both horns, both feet, full sword tip and every fabric edge are comfortably inside a roomy SQUARE transparent canvas. Subject and weapon together occupy only the CENTRAL 75 PERCENT of the canvas height and width. A clear fully transparent margin surrounds the entire figure on ALL four sides. Do not crop to the alpha bounding box. Reconstruct a tiny missing edge if necessary so the complete sword and feet are shown.
Preserve the exact bull identity, left-facing pose, white fur, horns, proportions, heavy armor, dao blade, robe, colors and hand-painted ink/gouache texture.
Maintain genuinely transparent RGBA PNG background, no new scenery, floor, shadow, checkerboard, backdrop or opaque rectangle. No extra objects, text, logo or watermark. Keep all other visual details unchanged.
```

## 大鹏：首轮提示词

```text
Use case: stylized-concept
Asset type: transparent full-body boss illustration for a bright Chinese Journey to the West card adventure game.
Input image 1 is ONLY a visual style reference. Create a NEW boss asset, not a landscape edit. Match the reference's Chinese mineral-color ink and gouache painting, distinct painterly brush texture, teal/green, ivory, vermilion with warm gold highlights.
Subject: 大鹏金翅雕, the Golden-winged Great Peng demon king. A tall regal anthropomorphic bird demon, clearly avian face with a long curved golden beak, feather crown/crest, two large golden feathered wings, teal Chinese warrior armor, restrained vermilion sash, clawed bird feet. Holds one long Chinese polearm. Fierce but suitable for an inviting all-ages adventure, not horror; no human face.
Composition: entire body standing firmly in a proud three-quarter pose facing toward the LEFT EDGE of the image, the two wings partially spread in a controlled compact arc so all feathers and polearm fit. Both bird feet, feather crown, beak, wings and weapon fully visible. Leave empty transparent padding around the whole silhouette, at least 8 percent on every side. One character only, crisp silhouette readable at game size; hand-painted 2D illustration, not photography or 3D.
Background: genuinely transparent PNG with actual RGBA alpha, isolated figure only. No floor, pedestal, ground shadow, sky, clouds, scenery, paper backdrop, opaque white or fake transparency checkerboard.
Constraints: no lettering, text, calligraphy, UI, border, logo, watermark, extra people or cropped body parts.
```

### 定向修改提示词

```text
Use case: precise-object-edit.
Input image 1 is the edit target: the existing Golden-winged Great Peng bird demon cutout.
Change ONLY framing: zoom OUT the entire figure into a roomy SQUARE transparent canvas. Reconstruct the complete upper tip of the currently clipped polearm. Both bird feet, the full weapon tip, both wings and every feather/fabric edge must be comfortably inside the image. Subject and weapon together occupy only the CENTRAL 75 PERCENT of the canvas height and width. A clear fully transparent margin surrounds the entire figure on ALL four sides. Do not crop to alpha bounding box.
Preserve exact avian identity, golden hooked beak, feather crest, left-facing standing pose, body proportions, golden wings, teal armor, ivory and vermilion garments and painterly ink/gouache texture.
Maintain genuinely transparent RGBA PNG background, no new scenery, floor, shadow, checkerboard, backdrop or opaque rectangle. No extra objects, text, logo or watermark. Keep all other visual details unchanged.
```

## 只读 alpha 检查

Pillow 读取结果：均 1254 × 1254、RGBA、alpha 范围 [0,255]。未以黑/白/棋盘背景伪装透明。

|文件|字节|alpha=0 占比|0<alpha<255 占比|alpha≥240 占比|alpha≥128 主体边界 (left,top,right,bottom)|
|---|---:|---:|---:|---:|---|
|blackbear.png|1218598|69.2219%|30.6983%|28.7118%|(173,116,993,1159)|
|bullking.png|1407193|65.9889%|33.9217%|30.7915%|(112,113,1152,1148)|
|roc.png|1529295|61.9964%|37.9225%|33.2959%|(188,95,1139,1164)|

生成器边缘包含极低 alpha 残余像素，原图完整保留未阈值化；主体区域有安全留白。这里是源图质量检查，运行时 WebP/AVIF 衍生资源与页面集成由 T6 开发负责。

