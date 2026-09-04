# 生成式插图替换记录

## 范围

- 保留且未修改：人物头像、人物与敌人建模、`login_screen.jpg`、`map_background.png`。
- 使用 OpenAI 内置图像生成工具重新生成并替换：86 张卡牌、34 张遗物、8 张地图节点、1 张状态图，共 129 张；其中商店节点已进一步替换为“土地庙”。
- 新增 1 张土地庙沉浸式商店背景，用于卡面、费用、数值和购买操作的完整展示。
- 删除未被代码引用的旧图：`shouye.png`、`test_img.jpg`。
- README 中的运行截图属于实机证据，不纳入插图库替换范围。

## 统一视觉规范

所有新插图使用相同的基础提示词约束：

```text
Use case: stylized-concept
Asset type: Journey to the West game card / relic / environment icon atlas
Style/medium: premium Chinese ink-and-gouache fantasy game art
Color palette: dark indigo background; warm gold, cinnabar red, jade teal,
pearl white and violet accents according to the subject
Composition: one centered readable subject per square; high contrast at small UI size
Constraints: no text, letters, numbers, logos, watermarks, title graphics,
UI labels, blank white backgrounds, or elements crossing cells
```

角色专属卡牌分别使用对应的保留建模图作为人物外观参考；通用卡牌、遗物和节点使用登录首页与地图背景作为色彩和水墨质感参考。每组按 4×4、3×3 或 2×2 素材表生成，再裁切为独立的 512×512 JPEG，并沿用原文件名，因此业务代码不需要改变资源映射。

## 生成主题组

- 通用卡牌 A：重击、致命一击、蓄势待发、蓄力、旋风斩、仙丹、突刺、铜皮铁骨等 16 张。
- 通用卡牌 B：迷魂术、烈焰掌、狂暴、金钟罩、金刚经、回春术、挥棒、格挡、毒雾等 12 张。
- 角色卡牌：孙悟空 12 张、猪八戒 12 张、沙僧 11 张、白龙马 10 张、唐三藏 13 张。
- 遗物：紧箍咒、龙鳞甲、蟠桃、八卦炉、芭蕉扇、七星剑、玉净瓶、镇妖塔、玉玺等 34 张。
- 地图节点：战斗、首领、休息、宝箱、商店、随机事件、篝火、皇宫 8 张。
- 土地庙商店场景：山间土地庙内院、供桌、卡牌陈列架与暖色灯笼 1 张。
- 状态图：虚弱 1 张。

## 验收

- 129 个替换文件均为唯一的 512×512 RGB JPEG；新增的土地庙宽幅背景为 RGB JPEG。
- 本地容器逐一请求全部资源，均返回 HTTP 200 与图片 Content-Type。
- 人物头像、建模、登录首页和地图背景共 78 个保留文件，替换前后聚合 SHA-256 一致。
- `npm run build`、本地 Demo 烟测和 Playwright E2E 均通过。
