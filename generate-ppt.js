// ====== 西行开发志 - 编年史 PPT 生成脚本（增强版）======
const pptxgen = require("pptxgenjs");

let pres = new pptxgen();
pres.author = "西游记开发组";
pres.title = "西行开发志 - 开发进程编年史";
pres.layout = "LAYOUT_16x9";

// ============================================================
// SLIDE DIMENSIONS
// ============================================================
const SLIDE_W = 10;
const SLIDE_H = 5.625;
const MARGIN = 0.5;
const CONTENT_X = MARGIN;
const CONTENT_Y = MARGIN;
const CONTENT_W = SLIDE_W - 2 * MARGIN;
const CONTENT_H = SLIDE_H - 2 * MARGIN;
const CENTER_X = SLIDE_W / 2;
const CENTER_Y = SLIDE_H / 2;

// ============================================================
// CONTAINER SYSTEM
// ============================================================
function createVirtualNode(type, data, parentX = 0, parentY = 0) {
  const opts = data.opts || {};
  const node = {
    type, data,
    absX: parentX + (opts.x || 0),
    absY: parentY + (opts.y || 0),
    w: opts.w || 0, h: opts.h || 0,
    children: []
  };
  node.addShape = function(shapeType, opts = {}) {
    const child = createVirtualNode('shape', { shapeType, opts }, node.absX, node.absY);
    node.children.push(child);
    return child;
  };
  node.addText = function(text, opts = {}) {
    const safeOpts = { fit: "shrink", ...opts };
    const bulletRe = /^(?:[\u2022\u2023\u25E6\u2043\u2219\u00B7\u25CF\u25CB\u2013\u2014]\s*|\-\s+)/;
    if (Array.isArray(text)) {
      text = text.map(item => {
        if (item && item.options && item.options.bullet && typeof item.text === 'string') {
          return { ...item, text: item.text.replace(bulletRe, '') };
        }
        return item;
      });
    }
    const child = createVirtualNode('text', { text, opts: safeOpts }, node.absX, node.absY);
    node.children.push(child);
    return child;
  };
  node.addImage = function(opts = {}) {
    const child = createVirtualNode('image', { opts }, node.absX, node.absY);
    node.children.push(child);
    return child;
  };
  node.addTable = function(tableData, opts = {}) {
    const child = createVirtualNode('table', { tableData, opts }, node.absX, node.absY);
    node.children.push(child);
    return child;
  };
  return node;
}

function flattenNode(node, realSlide, pres) {
  const absOpts = { ...node.data.opts, x: node.absX, y: node.absY };
  if (node.type === 'shape') realSlide.addShape(node.data.shapeType, absOpts);
  else if (node.type === 'text') realSlide.addText(node.data.text, absOpts);
  else if (node.type === 'image') realSlide.addImage(absOpts);
  else if (node.type === 'table') realSlide.addTable(node.data.tableData, absOpts);
  node.children.forEach(child => flattenNode(child, realSlide, pres));
}

const originalAddSlide = pres.addSlide.bind(pres);
pres.addSlide = function(options) {
  const realSlide = originalAddSlide(options);
  const virtualSlide = {
    children: [],
    _realSlide: realSlide,
    set background(val) { realSlide.background = val; },
    get background() { return realSlide.background; },
    addShape: function(shapeType, opts = {}) {
      const node = createVirtualNode('shape', { shapeType, opts }, 0, 0);
      this.children.push(node);
      return node;
    },
    addText: function(text, opts = {}) {
      const safeOpts = { fit: "shrink", ...opts };
      const node = createVirtualNode('text', { text, opts: safeOpts }, 0, 0);
      this.children.push(node);
      return node;
    },
    addImage: function(opts = {}) {
      const node = createVirtualNode('image', { opts }, 0, 0);
      this.children.push(node);
      return node;
    },
    addTable: function(tableData, opts = {}) {
      const node = createVirtualNode('table', { tableData, opts }, 0, 0);
      this.children.push(node);
      return node;
    },
    addChart: function(chartType, data, opts = {}) {
      realSlide.addChart(chartType, data, opts);
    },
    render: function() {
      this.children.forEach(child => flattenNode(child, realSlide, pres));
    }
  };
  return virtualSlide;
};

// ============================================================
// COLOR PALETTE - 暗金朱砂（西游神话）
// ============================================================
const C = {
  ink: "1A1A2E",       // 墨夜底色
  inkLight: "2A2A4E",  // 浅墨
  red: "8B0000",       // 暗红朱砂
  redBright: "C0392B", // 亮红
  gold: "D4AF37",      // 金
  goldLight: "F4E4BC", // 米黄
  cream: "FAF3E0",     // 奶白
  text: "F5E6CA",      // 正文米色
  textDark: "2C2C3E",  // 深色文字
  muted: "8B7E5C",     // 暗金褐
  green: "4A7C59",     // 松绿
  blue: "2B4C7E",      // 靛青
};

const FONT_TITLE = "SimSun";       // 宋体（史书感）
const FONT_BODY = "Microsoft YaHei"; // 雅黑

// 卷轴卡片装饰：金边矩形 + 左侧朱砂色条
function scrollCard(slide, x, y, w, h, opts = {}) {
  const fill = opts.fill || C.inkLight;
  slide.addShape(pres.shapes.RECTANGLE, {
    x, y, w, h, fill: { color: fill },
    line: { color: C.gold, width: 1.5 },
    shadow: { type: "outer", blur: 4, offset: 2, color: "000000", opacity: 0.3 }
  });
  slide.addShape(pres.shapes.RECTANGLE, {
    x, y, w: 0.08, h, fill: { color: C.red }
  });
}

// 章节标题（顶部金色横条 + 红字）
function chapterHeader(slide, num, title) {
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 0, w: SLIDE_W, h: 0.06, fill: { color: C.gold }
  });
  slide.addText(`第${num}纪 · ${title}`, {
    x: CONTENT_X, y: 0.25, w: CONTENT_W, h: 0.6,
    fontSize: 28, fontFace: FONT_TITLE, color: C.gold,
    bold: true, charSpacing: 1.5, align: "left", valign: "middle"
  });
  slide.addShape(pres.shapes.LINE, {
    x: CONTENT_X, y: 0.95, w: CONTENT_W, h: 0,
    line: { color: C.red, width: 1, dashType: "dash" }
  });
}

// 附录标题
function appendixHeader(slide, title) {
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 0, w: SLIDE_W, h: 0.06, fill: { color: C.red }
  });
  slide.addText(title, {
    x: CONTENT_X, y: 0.25, w: CONTENT_W, h: 0.6,
    fontSize: 28, fontFace: FONT_TITLE, color: C.red,
    bold: true, charSpacing: 1.5, align: "left", valign: "middle"
  });
  slide.addShape(pres.shapes.LINE, {
    x: CONTENT_X, y: 0.95, w: CONTENT_W, h: 0,
    line: { color: C.gold, width: 1, dashType: "dash" }
  });
}

// 底部页脚
function footer(slide, pageNum) {
  const nums = ["一","二","三","四","五","六","七","八","九","十","十一","十二","十三","十四","十五","十六"];
  slide.addText(`西行开发志 · 卷之${nums[pageNum-1] || pageNum}`, {
    x: CONTENT_X, y: SLIDE_H - 0.4, w: CONTENT_W, h: 0.3,
    fontSize: 9, fontFace: FONT_TITLE, color: C.muted,
    align: "center", charSpacing: 0.5
  });
}

// ============================================================
// SLIDE 1: 封面 - 西行开发志
// ============================================================
{
  let s = pres.addSlide();
  s.background = { color: C.ink };

  s.addShape(pres.shapes.RECTANGLE, { x: 0, y: 0, w: SLIDE_W, h: 0.12, fill: { color: C.gold } });
  s.addShape(pres.shapes.RECTANGLE, { x: 0, y: SLIDE_H - 0.12, w: SLIDE_W, h: 0.12, fill: { color: C.red } });

  scrollCard(s, 1.5, 1.2, 7, 3.2, { fill: C.inkLight });

  s.addText("西行开发志", {
    x: 1.5, y: 1.6, w: 7, h: 1.2,
    fontSize: 48, fontFace: FONT_TITLE, color: C.gold,
    bold: true, align: "center", valign: "middle", charSpacing: 2.5
  });

  s.addText("西游记 Roguelike 卡牌游戏 · 开发进程编年史", {
    x: 1.5, y: 2.9, w: 7, h: 0.6,
    fontSize: 18, fontFace: FONT_BODY, color: C.text,
    align: "center", charSpacing: 1
  });

  s.addText("天启元年七月廿一 至 廿六  ·  凡六日而成", {
    x: 1.5, y: 3.5, w: 7, h: 0.5,
    fontSize: 14, fontFace: FONT_TITLE, color: C.muted,
    align: "center", charSpacing: 1, italic: true
  });

  s.addShape(pres.shapes.OVAL, {
    x: 0.8, y: 2.4, w: 0.5, h: 0.5,
    fill: { color: C.red }, line: { color: C.gold, width: 1 }
  });
  s.addText("史", { x: 0.8, y: 2.4, w: 0.5, h: 0.5, fontSize: 16, fontFace: FONT_TITLE, color: C.goldLight, bold: true, align: "center", valign: "middle" });
  s.addShape(pres.shapes.OVAL, {
    x: 8.7, y: 2.4, w: 0.5, h: 0.5,
    fill: { color: C.red }, line: { color: C.gold, width: 1 }
  });
  s.addText("志", { x: 8.7, y: 2.4, w: 0.5, h: 0.5, fontSize: 16, fontFace: FONT_TITLE, color: C.goldLight, bold: true, align: "center", valign: "middle" });

  s.render();
}

// ============================================================
// SLIDE 2: 目录 - 编年史纲
// ============================================================
{
  let s = pres.addSlide();
  s.background = { color: C.ink };
  s.addShape(pres.shapes.RECTANGLE, { x: 0, y: 0, w: SLIDE_W, h: 0.06, fill: { color: C.gold } });

  s.addText("编年史纲", {
    x: CONTENT_X, y: 0.25, w: CONTENT_W, h: 0.55,
    fontSize: 30, fontFace: FONT_TITLE, color: C.gold,
    bold: true, charSpacing: 1.5, align: "left"
  });

  const chapters = [
    { date: "七月廿一", title: "草创纪 · 卡牌奠基", desc: "抽牌效果修复、力量重置、商店校验、存档持久化" },
    { date: "七月廿二", title: "成形纪 · 算法核心", desc: "四堆牌组、卡牌升级、虚弱减伤、5选1奖励" },
    { date: "七月廿二", title: "补卡纪 · 三批扩容", desc: "新增36张卡牌、增量迁移、消耗机制、插图整理" },
    { date: "七月廿二", title: "扩展纪 · 监控与移动", desc: "Redis集成、SonarQube分析、Prometheus监控、PWA" },
    { date: "七月廿二", title: "架构纪 · 分布式部署", desc: "双实例集群、Nginx负载、Docker八容器、跨实例验证" },
    { date: "七月廿三", title: "加固纪 · 安全工程化", desc: "JWT强制、限流修复、@Transactional、DTO重构" },
    { date: "七月廿三", title: "联机纪 · 多人协作", desc: "WebSocket房间、STOMP通信、联机地图、协同战斗" },
    { date: "七月廿六", title: "丰富纪 · 妖怪与宝物", desc: "51妖怪扩展、8件皇帝宝物、EMPEROR赐宝节点" },
    { date: "七月廿六", title: "打磨纪 · 图标与清理", desc: "emoji换卡通图、UI放大优化、结构清理、文档更新" },
  ];

  let y = 0.9;
  const rowH = 0.48;
  chapters.forEach((ch, i) => {
    s.addShape(pres.shapes.OVAL, {
      x: CONTENT_X, y: y, w: 0.36, h: 0.36,
      fill: { color: C.red }, line: { color: C.gold, width: 1 }
    });
    const cnNums = ["一","二","三","四","五","六","七","八","九"];
    s.addText(cnNums[i], {
      x: CONTENT_X, y: y, w: 0.36, h: 0.36,
      fontSize: 11, fontFace: FONT_TITLE, color: C.goldLight,
      bold: true, align: "center", valign: "middle"
    });
    s.addText(ch.date, {
      x: CONTENT_X + 0.48, y: y, w: 1.1, h: 0.36,
      fontSize: 11, fontFace: FONT_TITLE, color: C.gold,
      align: "left", valign: "middle", charSpacing: 0.5
    });
    s.addText(ch.title, {
      x: CONTENT_X + 1.65, y: y, w: 3.2, h: 0.36,
      fontSize: 13, fontFace: FONT_BODY, color: C.text,
      bold: true, align: "left", valign: "middle"
    });
    s.addText(ch.desc, {
      x: CONTENT_X + 4.9, y: y, w: 4.1, h: 0.36,
      fontSize: 10, fontFace: FONT_BODY, color: C.muted,
      align: "left", valign: "middle"
    });
    y += rowH;
  });

  // 附录提示
  s.addShape(pres.shapes.LINE, {
    x: CONTENT_X, y: y + 0.1, w: CONTENT_W, h: 0,
    line: { color: C.gold, width: 0.5, dashType: "dot" }
  });
  s.addText("附录：除弊志（十三虫录）· 卡牌志 · 妖怪志 · 工程纪要 · 终章", {
    x: CONTENT_X, y: y + 0.2, w: CONTENT_W, h: 0.35,
    fontSize: 11, fontFace: FONT_TITLE, color: C.muted,
    italic: true, align: "center", charSpacing: 0.5
  });

  footer(s, 2);
  s.render();
}

// ============================================================
// SLIDE 3: 第一纪·草创 (7/21)
// ============================================================
{
  let s = pres.addSlide();
  s.background = { color: C.ink };
  chapterHeader(s, "一", "草创纪 · 卡牌奠基");
  s.addText("天启元年七月廿一日  ·  万事草创，卡牌奠基，存档立宗", {
    x: CONTENT_X, y: 1.0, w: CONTENT_W, h: 0.35,
    fontSize: 13, fontFace: FONT_TITLE, color: C.muted, italic: true, charSpacing: 1
  });

  const events = [
    { time: "戌时", title: "抽牌效果失灵", desc: "蓄力、龙吟等卡牌下回合抽牌失效。根因：Card模型缺drawNextTurn/energyNextTurn字段。修补：新增字段，playCard累积，startTurn生效并重置。" },
    { time: "亥时", title: "力量跨战叠加", desc: "力量值在战斗间未清零。修补：initBattle()中重置strength/dexterity/drawNextTurn/energyNextTurn为0，列为硬约束。" },
    { time: "子时", title: "商店购买漏洞", desc: "前端忽略后端bought字段，没钱也能买。修补：前端金币预检查+后端返回状态同步+不足提示，杜绝越权购买。" },
    { time: "丑时", title: "存档持久化", desc: "主菜单无法加载存档。新增：sessionId存localStorage，deleteSession接口，加载/删除存档弹窗，断线可续。" },
  ];

  let y = 1.5;
  events.forEach(ev => {
    scrollCard(s, CONTENT_X, y, CONTENT_W, 0.82);
    s.addText(ev.time, {
      x: CONTENT_X + 0.2, y: y + 0.08, w: 0.7, h: 0.3,
      fontSize: 12, fontFace: FONT_TITLE, color: C.gold, bold: true, align: "center"
    });
    s.addText(ev.title, {
      x: CONTENT_X + 1.0, y: y + 0.06, w: 2.8, h: 0.3,
      fontSize: 13, fontFace: FONT_BODY, color: C.goldLight, bold: true
    });
    s.addText(ev.desc, {
      x: CONTENT_X + 1.0, y: y + 0.36, w: CONTENT_W - 1.2, h: 0.4,
      fontSize: 10, fontFace: FONT_BODY, color: C.text, valign: "top"
    });
    y += 0.95;
  });

  footer(s, 3);
  s.render();
}

// ============================================================
// SLIDE 4: 第二纪·成形 (7/22 上) - 算法核心
// ============================================================
{
  let s = pres.addSlide();
  s.background = { color: C.ink };
  chapterHeader(s, "二", "成形纪 · 算法核心");
  s.addText("天启元年七月廿二日  ·  核心算法定型，卡牌升级，虚弱初现", {
    x: CONTENT_X, y: 1.0, w: CONTENT_W, h: 0.35,
    fontSize: 13, fontFace: FONT_TITLE, color: C.muted, italic: true, charSpacing: 1
  });

  // 左：算法体系
  scrollCard(s, CONTENT_X, 1.5, 4.3, 3.6);
  s.addText("核心算法体系", {
    x: CONTENT_X + 0.2, y: 1.6, w: 3.9, h: 0.4,
    fontSize: 16, fontFace: FONT_TITLE, color: C.gold, bold: true, charSpacing: 1
  });
  s.addText([
    { text: "四堆牌组循环", options: { bullet: true, breakLine: true, bold: true, color: C.goldLight } },
    { text: "抽牌堆→手牌→弃牌堆→洗牌", options: { breakLine: true, color: C.muted, fontSize: 10 } },
    { text: "Fisher-Yates 洗牌算法", options: { breakLine: true, bold: true, color: C.goldLight } },
    { text: "原型模式深拷贝 copy()", options: { breakLine: true, color: C.muted, fontSize: 10 } },
    { text: "引用相等(==)移除手牌", options: { breakLine: true, bold: true, color: C.goldLight } },
    { text: "修复copy()未复制id的冲突", options: { breakLine: true, color: C.muted, fontSize: 10 } },
    { text: "战斗有限状态机", options: { breakLine: true, bold: true, color: C.goldLight } },
    { text: "回合管理+敌人AI循环模式", options: { breakLine: true, color: C.muted, fontSize: 10 } },
    { text: "伤害公式", options: { breakLine: true, bold: true, color: C.goldLight } },
    { text: "(基础+力量)×虚弱×脆弱系数", options: { breakLine: true, color: C.muted, fontSize: 10 } },
    { text: "Buff系统", options: { breakLine: true, bold: true, color: C.goldLight } },
    { text: "Map merge()叠加+tickBuffs()衰减", options: { color: C.muted, fontSize: 10 } },
  ], { x: CONTENT_X + 0.2, y: 2.1, w: 3.9, h: 2.9, fontSize: 12, fontFace: FONT_BODY, paraSpaceAfter: 2 });

  // 右：功能实现
  scrollCard(s, CONTENT_X + 4.5, 1.5, 4.5, 3.6);
  s.addText("功能实现", {
    x: CONTENT_X + 4.7, y: 1.6, w: 4.1, h: 0.4,
    fontSize: 16, fontFace: FONT_TITLE, color: C.gold, bold: true, charSpacing: 1
  });
  s.addText([
    { text: "卡牌升级", options: { bullet: true, breakLine: true, bold: true, color: C.goldLight } },
    { text: "移除@Transient，upgrade()+3伤害/格挡", options: { breakLine: true, color: C.muted, fontSize: 10 } },
    { text: "虚弱Debuff", options: { breakLine: true, bold: true, color: C.goldLight } },
    { text: "25%减伤，火柴人捂腹插图", options: { breakLine: true, color: C.muted, fontSize: 10 } },
    { text: "5选1卡牌奖励", options: { breakLine: true, bold: true, color: C.goldLight } },
    { text: "击败小怪也掉卡，5张候选", options: { breakLine: true, color: C.muted, fontSize: 10 } },
    { text: "篝火限升2次", options: { breakLine: true, bold: true, color: C.goldLight } },
    { text: "bonfireUpgradesLeft追踪", options: { breakLine: true, color: C.muted, fontSize: 10 } },
    { text: "脆弱即时生效", options: { breakLine: true, bold: true, color: C.goldLight } },
    { text: "debuff移至伤害计算之前", options: { breakLine: true, color: C.muted, fontSize: 10 } },
    { text: "遗物战斗触发", options: { breakLine: true, bold: true, color: C.goldLight } },
    { text: "作用于battle.getEnemy()副本", options: { color: C.muted, fontSize: 10 } },
  ], { x: CONTENT_X + 4.7, y: 2.1, w: 4.1, h: 2.9, fontSize: 12, fontFace: FONT_BODY, paraSpaceAfter: 2 });

  footer(s, 4);
  s.render();
}

// ============================================================
// SLIDE 5: 第三纪·补卡 (7/22 中) - 三批卡牌扩容
// ============================================================
{
  let s = pres.addSlide();
  s.background = { color: C.ink };
  chapterHeader(s, "三", "补卡纪 · 三批扩容");
  s.addText("天启元年七月廿二日  ·  三批三十六卡入册，增量迁移，消耗机制立", {
    x: CONTENT_X, y: 1.0, w: CONTENT_W, h: 0.35,
    fontSize: 13, fontFace: FONT_TITLE, color: C.muted, italic: true, charSpacing: 1
  });

  // 三批卡牌横向排列
  const batches = [
    { batch: "第二批", count: "12张", checkpoint: "怒目金刚", color: C.red, items: [
      "怒目金刚 · 金刚经 · 迷魂术",
      "法天象地（孙悟空专属）",
      "天蓬之怒（猪八戒专属）",
      "罗汉金身（沙僧专属）",
      "龙威（白龙马专属）",
      "大乘佛法（唐三藏专属）",
      "修复：遗物战斗开始效果未触发",
      "修复：脆弱未影响同回合攻击",
    ]},
    { batch: "第三批", count: "12张", checkpoint: "烈焰掌", color: C.gold, items: [
      "烈焰掌 · 铜皮铁骨 · 毒雾弥漫",
      "蓄势待发（下回合抽2牌）",
      "筋斗云翻（孙悟空专属）",
      "九齿横扫（猪八戒专属）",
      "降妖钵盂（沙僧专属）",
      "龙卷风暴（白龙马专属）",
      "般若波罗蜜（唐三藏专属）",
      "migrateThirdBatchCards()增量迁移",
    ]},
    { batch: "消耗机制", count: "完善", checkpoint: "exhaust", color: C.green, items: [
      "新增exhaust字段标记消耗牌",
      "斗战胜佛 · 天蓬元帅",
      "般若波罗蜜 · 设exhaust=true",
      "syncCardNextTurnEffects()",
      "自动修正遗漏消耗标记的旧卡",
      "扫描描述含'消耗'的卡牌",
      "38项测试全部通过",
      "插图同步至images.js/images.ts",
    ]},
  ];

  const colW = (CONTENT_W - 0.6) / 3;
  batches.forEach((b, i) => {
    const cx = CONTENT_X + i * (colW + 0.3);
    scrollCard(s, cx, 1.5, colW, 3.5);
    s.addShape(pres.shapes.RECTANGLE, { x: cx, y: 1.5, w: colW, h: 0.08, fill: { color: b.color } });
    s.addText(b.batch, {
      x: cx + 0.15, y: 1.62, w: colW - 0.3, h: 0.35,
      fontSize: 14, fontFace: FONT_TITLE, color: b.color, bold: true, align: "center", charSpacing: 0.5
    });
    s.addText(`${b.count} · 检查点：${b.checkpoint}`, {
      x: cx + 0.15, y: 1.97, w: colW - 0.3, h: 0.25,
      fontSize: 9, fontFace: FONT_BODY, color: C.muted, align: "center", italic: true
    });
    const bullets = b.items.map((it, idx) => ({
      text: it,
      options: { bullet: true, breakLine: idx < b.items.length - 1, color: C.text, fontSize: 9 }
    }));
    s.addText(bullets, {
      x: cx + 0.15, y: 2.3, w: colW - 0.3, h: 2.5,
      fontSize: 10, fontFace: FONT_BODY, paraSpaceAfter: 2, valign: "top"
    });
  });

  footer(s, 5);
  s.render();
}

// ============================================================
// SLIDE 6: 第四纪·扩展 (7/22 下) - 监控与移动
// ============================================================
{
  let s = pres.addSlide();
  s.background = { color: C.ink };
  chapterHeader(s, "四", "扩展纪 · 监控与移动");
  s.addText("天启元年七月廿二日  ·  Redis入局，静态扫描，监控可视化，移动适配", {
    x: CONTENT_X, y: 1.0, w: CONTENT_W, h: 0.35,
    fontSize: 13, fontFace: FONT_TITLE, color: C.muted, italic: true, charSpacing: 1
  });

  const items = [
    { title: "Redis 集成", icon: "R", desc: "增强序列化/反序列化\n内存存储降级兜底\n解决JPA实体缓存序列化失败\n移除4个@Repository @Cacheable" },
    { title: "SonarQube 分析", icon: "S", desc: "Docker部署SonarQube\nMaven插件集成\n静态代码质量扫描\n识别技术债务与代码异味" },
    { title: "Prometheus 监控", icon: "P", desc: "Grafana预置仪表盘\nJVM指标+HTTP请求\n系统资源可视化\n31项测试全部通过" },
    { title: "移动端适配", icon: "M", desc: "标题64px→36px\n卡牌140px→100px\nPWA全屏+触摸优化\niPhone添加主屏幕全屏" },
  ];

  const cardW = (CONTENT_W - 0.3) / 2;
  const cardH = 1.55;
  let positions = [
    { x: CONTENT_X, y: 1.5 },
    { x: CONTENT_X + cardW + 0.3, y: 1.5 },
    { x: CONTENT_X, y: 1.5 + cardH + 0.25 },
    { x: CONTENT_X + cardW + 0.3, y: 1.5 + cardH + 0.25 },
  ];

  items.forEach((it, i) => {
    const pos = positions[i];
    scrollCard(s, pos.x, pos.y, cardW, cardH);
    s.addShape(pres.shapes.OVAL, {
      x: pos.x + 0.2, y: pos.y + 0.2, w: 0.5, h: 0.5,
      fill: { color: C.red }, line: { color: C.gold, width: 1 }
    });
    s.addText(it.icon, {
      x: pos.x + 0.2, y: pos.y + 0.2, w: 0.5, h: 0.5,
      fontSize: 18, fontFace: FONT_TITLE, color: C.goldLight, bold: true, align: "center", valign: "middle"
    });
    s.addText(it.title, {
      x: pos.x + 0.85, y: pos.y + 0.2, w: cardW - 1, h: 0.5,
      fontSize: 15, fontFace: FONT_TITLE, color: C.gold, bold: true, valign: "middle", charSpacing: 0.5
    });
    s.addText(it.desc, {
      x: pos.x + 0.2, y: pos.y + 0.8, w: cardW - 0.4, h: 0.7,
      fontSize: 10, fontFace: FONT_BODY, color: C.text, valign: "top"
    });
  });

  footer(s, 6);
  s.render();
}

// ============================================================
// SLIDE 7: 第五纪·架构 (7/22-23) - 分布式部署
// ============================================================
{
  let s = pres.addSlide();
  s.background = { color: C.ink };
  chapterHeader(s, "五", "架构纪 · 分布式部署");
  s.addText("天启元年七月廿二至廿三日  ·  双实例集群，容器化部署，跨实例验证", {
    x: CONTENT_X, y: 1.0, w: CONTENT_W, h: 0.35,
    fontSize: 13, fontFace: FONT_TITLE, color: C.muted, italic: true, charSpacing: 1
  });

  const archLayers = [
    { label: "Nginx 负载均衡 (8080)", color: C.gold, sub: "轮询转发 → instance1 / instance2" },
    { label: "Spring Boot 实例1 (8081)", color: C.red, sub: "Spring Boot 3.4 + JWT + 限流" },
    { label: "Spring Boot 实例2 (8082)", color: C.red, sub: "共享会话与战斗状态" },
    { label: "Redis 会话共享 + MySQL 持久化", color: C.green, sub: "Redisson分布式锁 · HikariCP连接池" },
    { label: "Prometheus + Grafana 监控", color: C.muted, sub: "JVM / HTTP / 系统资源指标" },
  ];

  const nodeW = 6, nodeH = 0.55, gap = 0.5;
  const startX = (SLIDE_W - nodeW) / 2, startY = 1.45;
  const nodeShadow = { type: "outer", blur: 4, offset: 2, color: "000000", opacity: 0.3 };

  archLayers.forEach((layer, i) => {
    const y = startY + i * gap;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x: startX, y, w: nodeW, h: nodeH,
      fill: { color: layer.color }, rectRadius: 0.1, shadow: nodeShadow
    });
    s.addText(layer.label, {
      x: startX, y, w: nodeW, h: nodeH,
      align: "center", valign: "middle",
      color: "FFFFFF", fontSize: 12, fontFace: FONT_BODY, bold: true
    });
    s.addText(layer.sub, {
      x: startX + nodeW + 0.15, y, w: 3.3, h: nodeH,
      align: "left", valign: "middle",
      color: C.muted, fontSize: 9, fontFace: FONT_BODY
    });
    if (i < archLayers.length - 1) {
      s.addShape(pres.shapes.LINE, {
        x: startX + nodeW / 2, y: y + nodeH,
        w: 0, h: gap - nodeH,
        line: { color: C.gold, width: 1.5, endArrowType: "triangle" }
      });
    }
  });

  scrollCard(s, CONTENT_X, 4.4, CONTENT_W, 0.8);
  s.addText("跨实例验证通过：实例1(8081)建游戏 → 实例2(8082)移动与战斗 → 状态一致，Redis会话共享生效", {
    x: CONTENT_X + 0.2, y: 4.45, w: CONTENT_W - 0.4, h: 0.7,
    fontSize: 12, fontFace: FONT_BODY, color: C.goldLight, bold: true, valign: "middle"
  });

  footer(s, 7);
  s.render();
}

// ============================================================
// SLIDE 8: 第六纪·加固 (7/23) - 安全工程化
// ============================================================
{
  let s = pres.addSlide();
  s.background = { color: C.ink };
  chapterHeader(s, "六", "加固纪 · 安全工程化");
  s.addText("天启元年七月廿三日  ·  四阶段工程化加固，安全为基，质量为器", {
    x: CONTENT_X, y: 1.0, w: CONTENT_W, h: 0.35,
    fontSize: 13, fontFace: FONT_TITLE, color: C.muted, italic: true, charSpacing: 1
  });

  const phases = [
    { title: "阶段一·数据一致性", desc: "@Transactional加持\n自定义异常体系\n@Valid校验DTO\n全局异常处理器\n覆盖5类错误", color: C.red },
    { title: "阶段二·Controller DTO", desc: "PlayerSummaryAssembler\n消除76行重复代码\n@JsonInclude(NON_NULL)\n保持JSON契约\n8个Map接口保留", color: C.gold },
    { title: "阶段三·包结构重构", desc: "session子包拆分\n5个类迁移\nimport更新\n职责清晰化\nservice.session命名", color: C.green },
    { title: "阶段四·测试覆盖", desc: "AuthServiceTest 6用例\nJaCoCo行覆盖≥20%\n中文路径修复\n44单元+10集成测试\n全部通过无回归", color: C.muted },
  ];

  const nodeW = 2.1, nodeH = 2.0, hGap = 0.25;
  const totalW = phases.length * nodeW + (phases.length - 1) * hGap;
  const startX = (SLIDE_W - totalW) / 2, nodeY = 1.5;
  const nodeShadow = { type: "outer", blur: 4, offset: 2, color: "000000", opacity: 0.3 };

  phases.forEach((p, i) => {
    const x = startX + i * (nodeW + hGap);
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x, y: nodeY, w: nodeW, h: nodeH,
      fill: { color: C.inkLight }, rectRadius: 0.1, shadow: nodeShadow,
      line: { color: p.color, width: 2 }
    });
    s.addShape(pres.shapes.RECTANGLE, {
      x, y: nodeY, w: nodeW, h: 0.08, fill: { color: p.color }
    });
    s.addText(p.title, {
      x: x + 0.1, y: nodeY + 0.15, w: nodeW - 0.2, h: 0.5,
      fontSize: 12, fontFace: FONT_TITLE, color: p.color, bold: true, align: "center", valign: "middle", charSpacing: 0.5
    });
    s.addText(p.desc, {
      x: x + 0.1, y: nodeY + 0.7, w: nodeW - 0.2, h: 1.2,
      fontSize: 9, fontFace: FONT_BODY, color: C.text, valign: "top"
    });
    if (i < phases.length - 1) {
      s.addShape(pres.shapes.LINE, {
        x: x + nodeW, y: nodeY + nodeH / 2, w: hGap, h: 0,
        line: { color: C.gold, width: 1.5, endArrowType: "triangle" }
      });
    }
  });

  scrollCard(s, CONTENT_X, 3.75, CONTENT_W, 1.1);
  s.addText("安全加固与限流演进", {
    x: CONTENT_X + 0.2, y: 3.8, w: 3, h: 0.35,
    fontSize: 13, fontFace: FONT_TITLE, color: C.gold, bold: true
  });
  s.addText([
    { text: "JWT强制(ENFORCE_JWT=true) · CORS白名单 · 密钥环境变量化 · 8位traceId追踪 · JSON结构化日志", options: { bullet: true, breakLine: true, color: C.text, fontSize: 10 } },
    { text: "限流演进：最小间隔算法(100ms)误杀快速连续请求(move→startBattle被429拦截) → 滑动窗口算法(30 req/s)，误报消除", options: { bullet: true, color: C.text, fontSize: 10 } },
  ], { x: CONTENT_X + 0.2, y: 4.15, w: CONTENT_W - 0.4, h: 0.6, fontFace: FONT_BODY, paraSpaceAfter: 3 });

  footer(s, 8);
  s.render();
}

// ============================================================
// SLIDE 9: 第七纪·联机 (7/23-24) - 多人协作
// ============================================================
{
  let s = pres.addSlide();
  s.background = { color: C.ink };
  chapterHeader(s, "七", "联机纪 · 多人协作");
  s.addText("天启元年七月廿三至廿四日  ·  房间系统立，STOMP通，联机地图成", {
    x: CONTENT_X, y: 1.0, w: CONTENT_W, h: 0.35,
    fontSize: 13, fontFace: FONT_TITLE, color: C.muted, italic: true, charSpacing: 1
  });

  const flow = [
    { label: "创建房间", desc: "RoomService\n生成房间码" },
    { label: "STOMP连接", desc: "WebSocket\n实时通信层" },
    { label: "选角准备", desc: "5角色选择\n全员准备" },
    { label: "联机地图", desc: "MultiplayerMapView\n复用单机UI" },
    { label: "协同战斗", desc: "MultiplayerBattle\n同步状态" },
  ];

  const nodeW = 1.6, nodeH = 1.0, hGap = 0.35;
  const totalW = flow.length * nodeW + (flow.length - 1) * hGap;
  const startX = (SLIDE_W - totalW) / 2, nodeY = 1.45;
  const nodeShadow = { type: "outer", blur: 4, offset: 2, color: "000000", opacity: 0.3 };

  flow.forEach((f, i) => {
    const x = startX + i * (nodeW + hGap);
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x, y: nodeY, w: nodeW, h: nodeH,
      fill: { color: C.red }, rectRadius: 0.12, shadow: nodeShadow
    });
    s.addText(f.label, {
      x, y: nodeY + 0.1, w: nodeW, h: 0.4,
      fontSize: 13, fontFace: FONT_TITLE, color: C.goldLight, bold: true, align: "center", valign: "middle"
    });
    s.addText(f.desc, {
      x: x + 0.1, y: nodeY + 0.5, w: nodeW - 0.2, h: 0.45,
      fontSize: 9, fontFace: FONT_BODY, color: "FFFFFF", align: "center", valign: "top"
    });
    if (i < flow.length - 1) {
      s.addShape(pres.shapes.LINE, {
        x: x + nodeW, y: nodeY + nodeH / 2, w: hGap, h: 0,
        line: { color: C.gold, width: 2, endArrowType: "triangle" }
      });
    }
  });

  scrollCard(s, CONTENT_X, 2.7, CONTENT_W, 2.2);
  s.addText("关键实现", {
    x: CONTENT_X + 0.2, y: 2.78, w: 3, h: 0.35,
    fontSize: 15, fontFace: FONT_TITLE, color: C.gold, bold: true
  });

  s.addText([
    { text: "后端", options: { bold: true, color: C.goldLight, breakLine: true } },
    { text: "RoomService：建房/加入/准备/选角/开局", options: { bullet: true, breakLine: true, color: C.text, fontSize: 10 } },
    { text: "MultiplayerMapService：复用单机地图算法", options: { bullet: true, breakLine: true, color: C.text, fontSize: 10 } },
    { text: "MultiplayerBattleService：IN_MAP触发战斗", options: { bullet: true, breakLine: true, color: C.text, fontSize: 10 } },
    { text: "Redisson分布式锁：并发角色选择/奖励领取", options: { bullet: true, breakLine: true, color: C.text, fontSize: 10 } },
    { text: "startGame/moveToNode/handleEvent/nextLayer", options: { bullet: true, color: C.text, fontSize: 10 } },
  ], { x: CONTENT_X + 0.2, y: 3.15, w: 4.3, h: 1.6, fontSize: 11, fontFace: FONT_BODY, paraSpaceAfter: 2 });

  s.addText([
    { text: "前端", options: { bold: true, color: C.goldLight, breakLine: true } },
    { text: "room.ts store：房间/战斗状态管理", options: { bullet: true, breakLine: true, color: C.text, fontSize: 10 } },
    { text: "useStomp composable：WS连接/订阅/重连", options: { bullet: true, breakLine: true, color: C.text, fontSize: 10 } },
    { text: "MultiplayerMapView.vue：联机地图UI", options: { bullet: true, breakLine: true, color: C.text, fontSize: 10 } },
    { text: "路由 /room/:code/map 进入联机地图", options: { bullet: true, breakLine: true, color: C.text, fontSize: 10 } },
    { text: "端到端验证：23节点地图+5牌起手+敌人意图", options: { bullet: true, color: C.text, fontSize: 10 } },
  ], { x: CONTENT_X + 4.7, y: 3.15, w: 4.3, h: 1.6, fontSize: 11, fontFace: FONT_BODY, paraSpaceAfter: 2 });

  footer(s, 9);
  s.render();
}

// ============================================================
// SLIDE 10: 第八纪·丰富 (7/26 上) - 妖怪与宝物
// ============================================================
{
  let s = pres.addSlide();
  s.background = { color: C.ink };
  chapterHeader(s, "八", "丰富纪 · 妖怪与宝物");
  s.addText("天启元年七月廿六日  ·  五十一妖怪入册，太宗御赐八宝，EMPEROR赐宝节点立", {
    x: CONTENT_X, y: 1.0, w: CONTENT_W, h: 0.35,
    fontSize: 13, fontFace: FONT_TITLE, color: C.muted, italic: true, charSpacing: 1
  });

  // 左：51妖怪
  scrollCard(s, CONTENT_X, 1.5, 4.3, 3.4);
  s.addText("五十一妖怪入册", {
    x: CONTENT_X + 0.2, y: 1.6, w: 3.9, h: 0.4,
    fontSize: 16, fontFace: FONT_TITLE, color: C.gold, bold: true, charSpacing: 1
  });

  s.addChart(pres.charts.BAR, [{
    name: "数量",
    labels: ["小怪级", "精英怪", "Boss级"],
    values: [22, 6, 23]
  }], {
    x: CONTENT_X + 0.2, y: 2.1, w: 3.9, h: 2.0,
    barDir: "col",
    chartColors: [C.red],
    chartArea: { fill: { color: C.inkLight }, roundedCorners: true },
    catAxisLabelColor: C.goldLight, catAxisLabelFontSize: 11,
    valAxisLabelColor: C.muted, valAxisLabelFontSize: 9,
    valGridLine: { color: C.inkLight, size: 0.5 },
    catGridLine: { style: "none" },
    showValue: true, dataLabelColor: C.goldLight, dataLabelFontSize: 10,
    showLegend: false,
    showTitle: false
  });

  s.addText("migrateExtraEnemies()增量迁移 · 黑熊精为幂等检查点", {
    x: CONTENT_X + 0.2, y: 4.25, w: 3.9, h: 0.5,
    fontSize: 9, fontFace: FONT_BODY, color: C.muted, italic: true
  });

  // 右：8件皇帝宝物
  scrollCard(s, CONTENT_X + 4.5, 1.5, 4.5, 3.4);
  s.addText("太宗御赐八宝", {
    x: CONTENT_X + 4.7, y: 1.6, w: 4.1, h: 0.4,
    fontSize: 16, fontFace: FONT_TITLE, color: C.gold, bold: true, charSpacing: 1
  });

  const relics = [
    "御赐金钵 — 战斗开始+30金币",
    "紫金钵盂 — 战斗开始+1能量",
    "大唐通关文牒 — 每层+20生命",
    "李世民御剑 — 战斗开始+2力量",
    "玄奘九环锡杖 — 战斗开始+2敏捷",
    "御林军虎符 — 战斗开始+10格挡",
    "御赐琉璃盏 — 每回合多抽1牌",
    "太宗玉玺 — 战斗金币翻倍",
  ];
  let ry = 2.1;
  relics.forEach(r => {
    s.addText("◆", { x: CONTENT_X + 4.7, y: ry, w: 0.25, h: 0.3, fontSize: 10, color: C.gold, align: "center", valign: "middle" });
    s.addText(r, { x: CONTENT_X + 5.0, y: ry, w: 3.8, h: 0.3, fontSize: 10, fontFace: FONT_BODY, color: C.text, valign: "middle" });
    ry += 0.33;
  });

  footer(s, 10);
  s.render();
}

// ============================================================
// SLIDE 11: 第九纪·打磨 (7/26 下) - 图标与清理
// ============================================================
{
  let s = pres.addSlide();
  s.background = { color: C.ink };
  chapterHeader(s, "九", "打磨纪 · 图标与清理");
  s.addText("天启元年七月廿六日  ·  emoji换卡通图，UI放大优化，结构清理，文档更新", {
    x: CONTENT_X, y: 1.0, w: CONTENT_W, h: 0.35,
    fontSize: 13, fontFace: FONT_TITLE, color: C.muted, italic: true, charSpacing: 1
  });

  const cols = [
    { title: "emoji 换图片", color: C.red, items: [
      "51敌人 emoji → 卡通半身像",
      "8件宝物 emoji → 道具图标",
      "EMPEROR节点 👑 → 唐太宗头像",
      "共生成60张 .jpg",
      "enemy-image-data.json提示词复用",
      "images.ts/images.js双向同步",
    ]},
    { title: "UI 放大优化", color: C.gold, items: [
      "卡牌奖励弹窗 1/2 界面",
      "卡牌 140px→220px",
      "宝物图标 48px→72px",
      "emperor/treasure 大模态",
      "EventModal isLargeModal",
      "移动端响应式适配",
    ]},
    { title: "结构清理", color: C.green, items: [
      "删除 frontend/ 旧前端",
      "删除 enemy-image-data.json",
      "删除重复 .bat 启动器",
      "清理 backend/target 产物",
      "删除旧 game.css 残留",
      "更新 README.md",
    ]},
  ];

  const colW = (CONTENT_W - 0.6) / 3;
  cols.forEach((col, i) => {
    const cx = CONTENT_X + i * (colW + 0.3);
    scrollCard(s, cx, 1.5, colW, 3.3);
    s.addShape(pres.shapes.RECTANGLE, { x: cx, y: 1.5, w: colW, h: 0.08, fill: { color: col.color } });
    s.addText(col.title, {
      x: cx + 0.15, y: 1.65, w: colW - 0.3, h: 0.4,
      fontSize: 14, fontFace: FONT_TITLE, color: col.color, bold: true, align: "center", charSpacing: 0.5
    });
    const bullets = col.items.map((it, idx) => ({
      text: it,
      options: { bullet: true, breakLine: idx < col.items.length - 1, color: C.text, fontSize: 10 }
    }));
    s.addText(bullets, {
      x: cx + 0.15, y: 2.15, w: colW - 0.3, h: 2.5,
      fontSize: 11, fontFace: FONT_BODY, paraSpaceAfter: 3, valign: "top"
    });
  });

  footer(s, 11);
  s.render();
}

// ============================================================
// SLIDE 12: 附录·除弊志 - 十三虫录
// ============================================================
{
  let s = pres.addSlide();
  s.background = { color: C.ink };
  appendixHeader(s, "除弊志 · 十三虫录");
  s.addText("开发六日，除虫十三，录之以为鉴", {
    x: CONTENT_X, y: 1.0, w: CONTENT_W, h: 0.35,
    fontSize: 13, fontFace: FONT_TITLE, color: C.muted, italic: true, charSpacing: 1
  });

  const bugs = [
    { id: "01", desc: "copy()未复制id致手牌误删", fix: "引用相等(==)替代equals()" },
    { id: "02", desc: "力量跨战叠加不重置", fix: "initBattle()归零" },
    { id: "03", desc: "脆弱未影响同回合攻击", fix: "debuff移至伤害计算前" },
    { id: "04", desc: "遗物战斗开始效果未触发", fix: "作用于battle.getEnemy()副本" },
    { id: "05", desc: "JPA实体序列化Redis失败", fix: "移除4个@Cacheable" },
    { id: "06", desc: "BAT文件中文乱码", fix: "纯英文脚本+系统mvn" },
    { id: "07", desc: "战斗界面无法打开(5因)", fix: "字段补全+类型修复+错误检查" },
    { id: "08", desc: "存档加载失败+Buff图标缺失", fix: "Pinia响应式+BuffBar组件" },
    { id: "09", desc: "Docker容器启动即退出", fix: "Dockerfile配置修复" },
    { id: "10", desc: "Nginx语法/健康检查异常", fix: "配置修正" },
    { id: "11", desc: "Redis连接前缀问题", fix: "连接配置修正" },
    { id: "12", desc: "前端加载卡死(Nginx白名单)", fix: "IP白名单调整" },
    { id: "13", desc: "开始游戏400错误(枚举大小写)", fix: "CharacterClass大小写不敏感解析" },
  ];

  // 两列布局
  const colW = (CONTENT_W - 0.3) / 2;
  const rowH = 0.42;
  bugs.forEach((bug, i) => {
    const col = i < 7 ? 0 : 1;
    const rowIdx = i < 7 ? i : i - 7;
    const x = CONTENT_X + col * (colW + 0.3);
    const y = 1.45 + rowIdx * rowH;

    s.addShape(pres.shapes.OVAL, {
      x, y, w: 0.35, h: 0.35,
      fill: { color: C.red }, line: { color: C.gold, width: 0.5 }
    });
    s.addText(bug.id, {
      x, y, w: 0.35, h: 0.35,
      fontSize: 8, fontFace: FONT_TITLE, color: C.goldLight,
      bold: true, align: "center", valign: "middle"
    });
    s.addText(bug.desc, {
      x: x + 0.42, y, w: colW - 1.8, h: 0.35,
      fontSize: 9, fontFace: FONT_BODY, color: C.text, valign: "middle"
    });
    s.addText(`→ ${bug.fix}`, {
      x: x + colW - 1.35, y, w: 1.35, h: 0.35,
      fontSize: 8, fontFace: FONT_BODY, color: C.green, valign: "middle", italic: true
    });
  });

  // 底部限流修复特记
  scrollCard(s, CONTENT_X, 4.55, CONTENT_W, 0.65);
  s.addText("特记：限流算法演进 — 最小间隔(100ms)误杀连续请求 → 滑动窗口(30 req/s)，move→startBattle不再被429拦截", {
    x: CONTENT_X + 0.2, y: 4.58, w: CONTENT_W - 0.4, h: 0.55,
    fontSize: 11, fontFace: FONT_BODY, color: C.goldLight, bold: true, valign: "middle"
  });

  footer(s, 12);
  s.render();
}

// ============================================================
// SLIDE 13: 附录·卡牌志 - 八十卡牌
// ============================================================
{
  let s = pres.addSlide();
  s.background = { color: C.ink };
  appendixHeader(s, "卡牌志 · 八十卡牌");
  s.addText("五角各执其器，八十余卡入册，四类分宗", {
    x: CONTENT_X, y: 1.0, w: CONTENT_W, h: 0.35,
    fontSize: 13, fontFace: FONT_TITLE, color: C.muted, italic: true, charSpacing: 1
  });

  // 左上：角色卡牌分布（饼图）
  scrollCard(s, CONTENT_X, 1.5, 4.3, 1.85);
  s.addText("角色卡牌分布", {
    x: CONTENT_X + 0.2, y: 1.55, w: 3.9, h: 0.3,
    fontSize: 13, fontFace: FONT_TITLE, color: C.gold, bold: true
  });
  s.addChart(pres.charts.PIE, [{
    name: "卡牌数",
    labels: ["孙悟空", "猪八戒", "沙僧", "白龙马", "唐三藏", "通用"],
    values: [14, 12, 11, 11, 12, 20]
  }], {
    x: CONTENT_X + 0.2, y: 1.85, w: 3.9, h: 1.4,
    chartColors: [C.red, C.gold, C.green, C.blue, C.muted, C.redBright],
    showLegend: true, legendPos: "r", legendColor: C.goldLight, legendFontSize: 8,
    showValue: false,
    showTitle: false,
    dataLabelColor: C.goldLight, dataLabelFontSize: 8
  });

  // 右上：卡牌类型
  scrollCard(s, CONTENT_X + 4.5, 1.5, 4.5, 1.85);
  s.addText("卡牌类型与特色", {
    x: CONTENT_X + 4.7, y: 1.55, w: 4.1, h: 0.3,
    fontSize: 13, fontFace: FONT_TITLE, color: C.gold, bold: true
  });
  s.addText([
    { text: "攻击（红）", options: { bold: true, color: C.redBright, breakLine: true, fontSize: 11 } },
    { text: "金箍棒法·九齿钉耙·烈焰掌·天雷破", options: { color: C.text, breakLine: true, fontSize: 9 } },
    { text: "技能（蓝）", options: { bold: true, color: C.blue, breakLine: true, fontSize: 11 } },
    { text: "七十二变·筋斗云·毒雾弥漫·迷魂术", options: { color: C.text, breakLine: true, fontSize: 9 } },
    { text: "防御（绿）", options: { bold: true, color: C.green, breakLine: true, fontSize: 11 } },
    { text: "格挡·铁壁·铜皮铁骨·金刚不坏", options: { color: C.text, breakLine: true, fontSize: 9 } },
    { text: "能力（紫）", options: { bold: true, color: C.muted, breakLine: true, fontSize: 11 } },
    { text: "蓄势待发·狂暴·金刚经·大乘佛法", options: { color: C.text, fontSize: 9 } },
  ], { x: CONTENT_X + 4.7, y: 1.85, w: 4.1, h: 1.4, fontFace: FONT_BODY, paraSpaceAfter: 1 });

  // 下方：三批扩容时间线
  scrollCard(s, CONTENT_X, 3.5, CONTENT_W, 1.65);
  s.addText("三批扩容时间线", {
    x: CONTENT_X + 0.2, y: 3.55, w: 3, h: 0.35,
    fontSize: 13, fontFace: FONT_TITLE, color: C.gold, bold: true
  });

  const timeline = [
    { phase: "初始", count: "44张", desc: "基础卡牌+角色专属" },
    { phase: "第二批", count: "+12张", desc: "怒目金刚为检查点" },
    { phase: "第三批", count: "+12张", desc: "烈焰掌为检查点" },
    { phase: "消耗机制", count: "完善", desc: "exhaust字段+自动修正" },
  ];
  const tlW = (CONTENT_W - 0.4) / 4;
  timeline.forEach((t, i) => {
    const x = CONTENT_X + 0.2 + i * tlW;
    s.addShape(pres.shapes.OVAL, {
      x: x + tlW / 2 - 0.12, y: 3.95, w: 0.24, h: 0.24,
      fill: { color: C.red }, line: { color: C.gold, width: 1 }
    });
    s.addText(t.phase, {
      x, y: 4.2, w: tlW, h: 0.25,
      fontSize: 11, fontFace: FONT_TITLE, color: C.gold, bold: true, align: "center"
    });
    s.addText(t.count, {
      x, y: 4.45, w: tlW, h: 0.2,
      fontSize: 10, fontFace: FONT_BODY, color: C.goldLight, align: "center", bold: true
    });
    s.addText(t.desc, {
      x, y: 4.65, w: tlW, h: 0.3,
      fontSize: 8, fontFace: FONT_BODY, color: C.muted, align: "center"
    });
    if (i < timeline.length - 1) {
      s.addShape(pres.shapes.LINE, {
        x: x + tlW / 2 + 0.12, y: 4.07, w: tlW - 0.24, h: 0,
        line: { color: C.gold, width: 1, dashType: "dash" }
      });
    }
  });

  footer(s, 13);
  s.render();
}

// ============================================================
// SLIDE 14: 附录·妖怪志 - 六十三妖
// ============================================================
{
  let s = pres.addSlide();
  s.background = { color: C.ink };
  appendixHeader(s, "妖怪志 · 六十三妖");
  s.addText("原有十二妖，扩展五十一，合六十三，皆配卡通画像", {
    x: CONTENT_X, y: 1.0, w: CONTENT_W, h: 0.35,
    fontSize: 13, fontFace: FONT_TITLE, color: C.muted, italic: true, charSpacing: 1
  });

  // 左：妖怪分布柱状图
  scrollCard(s, CONTENT_X, 1.5, 4.3, 3.4);
  s.addText("妖怪等级分布", {
    x: CONTENT_X + 0.2, y: 1.6, w: 3.9, h: 0.35,
    fontSize: 14, fontFace: FONT_TITLE, color: C.gold, bold: true
  });
  s.addChart(pres.charts.BAR, [{
    name: "原有",
    labels: ["小怪级", "精英怪", "Boss级"],
    values: [6, 0, 6]
  }, {
    name: "扩展",
    labels: ["小怪级", "精英怪", "Boss级"],
    values: [22, 6, 23]
  }], {
    x: CONTENT_X + 0.2, y: 2.0, w: 3.9, h: 2.2,
    barDir: "col",
    chartColors: [C.muted, C.red],
    chartArea: { fill: { color: C.inkLight }, roundedCorners: true },
    catAxisLabelColor: C.goldLight, catAxisLabelFontSize: 11,
    valAxisLabelColor: C.muted, valAxisLabelFontSize: 9,
    valGridLine: { color: C.inkLight, size: 0.5 },
    catGridLine: { style: "none" },
    showValue: true, dataLabelColor: C.goldLight, dataLabelFontSize: 9,
    showLegend: true, legendPos: "b", legendColor: C.goldLight, legendFontSize: 9,
    showTitle: false
  });
  s.addText("原有12妖 + 扩展51妖 = 合计63妖", {
    x: CONTENT_X + 0.2, y: 4.3, w: 3.9, h: 0.4,
    fontSize: 10, fontFace: FONT_BODY, color: C.goldLight, bold: true, align: "center"
  });

  // 右：代表妖怪名录
  scrollCard(s, CONTENT_X + 4.5, 1.5, 4.5, 3.4);
  s.addText("代表妖怪名录", {
    x: CONTENT_X + 4.7, y: 1.6, w: 4.1, h: 0.35,
    fontSize: 14, fontFace: FONT_TITLE, color: C.gold, bold: true
  });
  s.addText([
    { text: "小怪级（代表）", options: { bold: true, color: C.green, breakLine: true, fontSize: 11 } },
    { text: "寅将军 · 熊山君 · 特处士 · 白衣秀士", options: { color: C.text, breakLine: true, fontSize: 9 } },
    { text: "小钻风 · 奔波儿灞 · 灌子波 · 巴波尔本", options: { color: C.text, breakLine: true, fontSize: 9 } },
    { text: "精英怪（代表）", options: { bold: true, color: C.gold, breakLine: true, fontSize: 11 } },
    { text: "黑熊精 · 黄风怪 · 白面狐狸 · 黄袍怪", options: { color: C.text, breakLine: true, fontSize: 9 } },
    { text: "灵感大王 · 赛太岁", options: { color: C.text, breakLine: true, fontSize: 9 } },
    { text: "Boss级（代表）", options: { bold: true, color: C.redBright, breakLine: true, fontSize: 11 } },
    { text: "牛魔王 · 铁扇公主 · 大鹏金翅 · 九灵元圣", options: { color: C.text, breakLine: true, fontSize: 9 } },
    { text: "六耳猕猴 · 黄眉大王 · 青牛精 · 独角兕", options: { color: C.text, breakLine: true, fontSize: 9 } },
    { text: "蝎子精 · 蜘蛛精 · 玉兔精 · 老鼋", options: { color: C.text, breakLine: true, fontSize: 9 } },
    { text: "", options: { breakLine: true, fontSize: 4 } },
    { text: " migrateExtraEnemies()增量迁移 · 黑熊精幂等检查点", options: { color: C.muted, fontSize: 8, italic: true } },
  ], { x: CONTENT_X + 4.7, y: 2.0, w: 4.1, h: 2.8, fontFace: FONT_BODY, paraSpaceAfter: 1 });

  footer(s, 14);
  s.render();
}

// ============================================================
// SLIDE 15: 附录·工程纪要 - 架构演进
// ============================================================
{
  let s = pres.addSlide();
  s.background = { color: C.ink };
  appendixHeader(s, "工程纪要 · 架构演进");
  s.addText("单体起，持久化，分布式，监控成，联机通 — 五阶演进", {
    x: CONTENT_X, y: 1.0, w: CONTENT_W, h: 0.35,
    fontSize: 13, fontFace: FONT_TITLE, color: C.muted, italic: true, charSpacing: 1
  });

  const stages = [
    { stage: "单体", desc: "Spring Boot\nH2内存", color: C.muted },
    { stage: "持久化", desc: "MySQL + Redis\n会话存储", color: C.green },
    { stage: "分布式", desc: "双实例 + Nginx\nDocker集群", color: C.gold },
    { stage: "监控", desc: "Prometheus\nGrafana + Zipkin", color: C.red },
    { stage: "联机", desc: "WebSocket STOMP\n房间协作", color: C.redBright },
  ];

  const nodeW = 1.6, nodeH = 1.2, hGap = 0.25;
  const totalW = stages.length * nodeW + (stages.length - 1) * hGap;
  const startX = (SLIDE_W - totalW) / 2, nodeY = 1.45;
  const nodeShadow = { type: "outer", blur: 4, offset: 2, color: "000000", opacity: 0.3 };

  stages.forEach((st, i) => {
    const x = startX + i * (nodeW + hGap);
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x, y: nodeY, w: nodeW, h: nodeH,
      fill: { color: st.color }, rectRadius: 0.1, shadow: nodeShadow
    });
    s.addText(st.stage, {
      x, y: nodeY + 0.15, w: nodeW, h: 0.4,
      fontSize: 15, fontFace: FONT_TITLE, color: "FFFFFF", bold: true, align: "center", valign: "middle"
    });
    s.addText(st.desc, {
      x: x + 0.1, y: nodeY + 0.6, w: nodeW - 0.2, h: 0.55,
      fontSize: 9, fontFace: FONT_BODY, color: "FFFFFF", align: "center", valign: "top"
    });
    if (i < stages.length - 1) {
      s.addShape(pres.shapes.LINE, {
        x: x + nodeW, y: nodeY + nodeH / 2, w: hGap, h: 0,
        line: { color: C.gold, width: 2, endArrowType: "triangle" }
      });
    }
  });

  // 技术栈表
  scrollCard(s, CONTENT_X, 2.95, CONTENT_W, 1.9);
  s.addText("技术栈全景", {
    x: CONTENT_X + 0.2, y: 3.0, w: 3, h: 0.35,
    fontSize: 14, fontFace: FONT_TITLE, color: C.gold, bold: true
  });

  const techTable = [
    [
      { text: "层级", options: { fill: { color: C.red }, color: C.goldLight, bold: true, align: "center", fontFace: FONT_TITLE } },
      { text: "技术", options: { fill: { color: C.red }, color: C.goldLight, bold: true, align: "center", fontFace: FONT_TITLE } },
    ],
    [{ text: "后端", options: { color: C.goldLight, bold: true } }, { text: "Java 17 · Spring Boot 3.4 · JPA · Flyway", options: { color: C.text } }],
    [{ text: "数据", options: { color: C.goldLight, bold: true } }, { text: "MySQL · Redis · Redisson 分布式锁", options: { color: C.text } }],
    [{ text: "安全", options: { color: C.goldLight, bold: true } }, { text: "JWT · Spring Security · 滑动窗口限流(30req/s)", options: { color: C.text } }],
    [{ text: "前端", options: { color: C.goldLight, bold: true } }, { text: "Vue 3 · TypeScript · Vite · Pinia · STOMP", options: { color: C.text } }],
    [{ text: "运维", options: { color: C.goldLight, bold: true } }, { text: "Docker Compose · Nginx · Prometheus · Grafana · Zipkin", options: { color: C.text } }],
  ];
  s.addTable(techTable, {
    x: CONTENT_X + 0.2, y: 3.4, w: CONTENT_W - 0.4,
    colW: [1.2, 7.6],
    border: { pt: 0.5, color: C.inkLight },
    rowH: 0.26,
    fontSize: 10, fontFace: FONT_BODY,
    align: "left", valign: "middle"
  });

  footer(s, 15);
  s.render();
}

// ============================================================
// SLIDE 16: 终章 - 项目数据与未来
// ============================================================
{
  let s = pres.addSlide();
  s.background = { color: C.ink };
  s.addShape(pres.shapes.RECTANGLE, { x: 0, y: 0, w: SLIDE_W, h: 0.12, fill: { color: C.gold } });
  s.addShape(pres.shapes.RECTANGLE, { x: 0, y: SLIDE_H - 0.12, w: SLIDE_W, h: 0.12, fill: { color: C.red } });

  s.addText("终章 · 志成", {
    x: CONTENT_X, y: 0.35, w: CONTENT_W, h: 0.7,
    fontSize: 36, fontFace: FONT_TITLE, color: C.gold,
    bold: true, charSpacing: 2.5, align: "center", valign: "middle"
  });
  s.addText("六日功成，西行路通，十三虫除，八十卡成", {
    x: CONTENT_X, y: 1.05, w: CONTENT_W, h: 0.4,
    fontSize: 15, fontFace: FONT_TITLE, color: C.muted,
    italic: true, align: "center", charSpacing: 1
  });

  // 数据统计 - 大数字卡片
  const stats = [
    { num: "80+", label: "卡牌", color: C.gold },
    { num: "63", label: "妖怪", color: C.red },
    { num: "30+", label: "遗物宝物", color: C.green },
    { num: "5", label: "可操控角色", color: C.redBright },
    { num: "54+", label: "单元/集成测试", color: C.blue },
    { num: "13", label: "除虫录", color: C.muted },
  ];

  const statW = (CONTENT_W - 0.5) / 6;
  stats.forEach((st, i) => {
    const x = CONTENT_X + i * (statW + 0.1);
    scrollCard(s, x, 1.65, statW, 1.35);
    s.addText(st.num, {
      x, y: 1.72, w: statW, h: 0.65,
      fontSize: 28, fontFace: FONT_TITLE, color: st.color,
      bold: true, align: "center", valign: "middle", charSpacing: 1
    });
    s.addText(st.label, {
      x, y: 2.4, w: statW, h: 0.4,
      fontSize: 11, fontFace: FONT_BODY, color: C.text,
      align: "center", valign: "middle"
    });
  });

  // 未来展望
  scrollCard(s, CONTENT_X, 3.25, CONTENT_W, 1.7);
  s.addText("未竟之业", {
    x: CONTENT_X + 0.2, y: 3.3, w: 3, h: 0.35,
    fontSize: 14, fontFace: FONT_TITLE, color: C.gold, bold: true
  });
  s.addText([
    { text: "测试深化", options: { bold: true, color: C.goldLight, breakLine: true, fontSize: 11 } },
    { text: "Controller MockMvc · Repository层 · DTO命名标准化", options: { bullet: true, breakLine: true, color: C.text, fontSize: 10 } },
    { text: "运维演进", options: { bold: true, color: C.goldLight, breakLine: true, fontSize: 11 } },
    { text: "SkyWalking链路追踪 · RabbitMQ异步 · MySQL会话持久化 · K8s编排", options: { bullet: true, breakLine: true, color: C.text, fontSize: 10 } },
    { text: "玩法拓展", options: { bold: true, color: C.goldLight, breakLine: true, fontSize: 11 } },
    { text: "PWA离线 · 联机第三阶段（协作玩法深化）· 更多妖怪与卡牌", options: { bullet: true, color: C.text, fontSize: 10 } },
  ], { x: CONTENT_X + 0.2, y: 3.65, w: CONTENT_W - 0.4, h: 1.2, fontFace: FONT_BODY, paraSpaceAfter: 2 });

  // 落款
  s.addText("— 西游记开发组  ·  天启元年七月廿六日 志 —", {
    x: CONTENT_X, y: SLIDE_H - 0.5, w: CONTENT_W, h: 0.3,
    fontSize: 10, fontFace: FONT_TITLE, color: C.muted,
    align: "center", charSpacing: 1, italic: true
  });

  s.render();
}

// ============================================================
// WRITE FILE
// ============================================================
pres.writeFile({ fileName: "c:\\Users\\20126\\Desktop\\西游记\\西行开发志.pptx" })
  .then(() => console.log("PPT 生成成功：西行开发志.pptx（16页增强版）"))
  .catch(err => console.error("生成失败:", err));
