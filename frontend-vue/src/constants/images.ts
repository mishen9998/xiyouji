// ====== 图片资源映射常量 ======

export const CARD_IMG: Record<string, string> = {
  '挥棒': 'card_huibang', '格挡': 'card_gedang', '蓄力': 'card_xuli', '闪避': 'card_shanbi',
  '重击': 'card_zhongji', '铁壁': 'card_tiebi', '突刺': 'card_tuci', '冥想': 'card_mingxiang',
  '金钟罩': 'card_jinzhongzhao', '天雷破': 'card_tianleipo', '致命一击': 'card_zhimingyiji',
  '仙丹': 'card_xiandan', '毒镖': 'card_dubiao', '横扫千军': 'card_hengsaoqianjun',
  '金箍棒法': 'card_jingubangfa', '七十二变': 'card_qishierbian', '筋斗云': 'card_jindouyun',
  '火眼金睛': 'card_huoyanjinjing', '大闹天宫': 'card_danaotiangong', '毫毛分身': 'card_haomaofenshen',
  '九齿钉耙': 'card_jiuchidingpa', '狼吞虎咽': 'card_langtunhuyan', '厚皮': 'card_houpi',
  '天河水军': 'card_tianheshuijun', '贪食': 'card_tanshi', '天蓬之怒': 'card_tianpengzhinu',
  '降妖宝杖': 'card_xiangyaobaozhang', '金刚不坏': 'card_jingangbuhuai', '流沙河': 'card_liushahe',
  '负重前行': 'card_fuzhongqianxing', '罗汉金身': 'card_luohanjinshen', '天河倒灌': 'card_tianhedaoguan',
  '龙吟': 'card_longyin', '腾云驾雾': 'card_tengyunjiawu', '疾风步': 'card_jifengbu',
  '龙爪': 'card_longzhua', '呼风唤雨': 'card_hufenghuanyu', '龙威': 'card_longwei',
  '紧箍咒念': 'card_jinguzhounian', '大乘佛法': 'card_dachengfofa', '金蝉脱壳': 'card_jinchantuoqiao',
  '普渡众生': 'card_puduzhongsheng', '心经': 'card_xinjing', '取经誓愿': 'card_qujingshiyuan',
  '旋风斩': 'card_xuanfengzhan', '铁布衫': 'card_tiebushan', '飞镖': 'card_feibiao',
  '回春术': 'card_huichunshu', '狂暴': 'card_kuangbao', '天罡阵': 'card_tiangangzhen',
  '定海神针': 'card_dinghaishenzhen', '身外身': 'card_shenwaishen',
  '三十六变': 'card_sanshiliubian', '净坛使者': 'card_jingtanshizhe',
  '降魔阵': 'card_xiangmozhen', '琉璃盏': 'card_liulizhan',
  '龙息': 'card_longxi', '水遁': 'card_shuidun',
  '九环锡杖': 'card_jiuhuanxizhang', '袈裟护体': 'card_jiashahuti',
  '超度': 'card_chaodu', '金身护体': 'card_jinshenhuti',
  // 第二批扩展卡牌
  '怒目金刚': 'card_numujingang', '金刚经': 'card_jingangjing', '迷魂术': 'card_mihunshu',
  '破甲击': 'card_pojiaji', '法天象地': 'card_fatianxiangdi', '哮天犬': 'card_xiaotianquan',
  '倒打一耙': 'card_daodayipao', '饕餮之口': 'card_taotiezhikou',
  '降妖宝杖·真': 'card_xiangyaobaozhang_zhen', '龙战于野': 'card_longzhanyuye',
  '佛光普照': 'card_foguangpuhzhao', '紧箍咒·禁': 'card_jinguzhou_jin',
  // 第三批扩展卡牌
  '烈焰掌': 'card_lieyanzhang', '铜皮铁骨': 'card_tongpitiegu', '毒雾弥漫': 'card_duwumiman',
  '蓄势待发': 'card_xushidaifa', '筋斗云翻': 'card_jindouyunfan', '斗战胜佛': 'card_douzhanshengfo',
  '九齿横扫': 'card_jiuchihengsao', '天蓬元帅': 'card_tianpengyuanshuai',
  '降妖钵盂': 'card_xiangyaoboyu', '金身罗汉': 'card_jinshenluohan',
  '龙卷风暴': 'card_longjuanfengbao', '般若波罗蜜': 'card_boreboluomi'
}

/**
 * 角色专属卡牌所在的图库目录。
 *
 * 通用卡牌没有角色归属，统一放在「宝物/卡牌/通用」；这里仅维护有
 * 明确 CharacterClass 的卡牌。这样新增通用卡牌时无需再改路径映射。
 */
export const CARD_CHARACTER_DIR: Record<string, string> = {
  // 孙悟空
  '金箍棒法': '孙悟空', '七十二变': '孙悟空', '筋斗云': '孙悟空',
  '火眼金睛': '孙悟空', '大闹天宫': '孙悟空', '毫毛分身': '孙悟空',
  '定海神针': '孙悟空', '身外身': '孙悟空', '法天象地': '孙悟空',
  '哮天犬': '孙悟空', '筋斗云翻': '孙悟空', '斗战胜佛': '孙悟空',
  // 猪八戒
  '九齿钉耙': '猪八戒', '狼吞虎咽': '猪八戒', '厚皮': '猪八戒',
  '天河水军': '猪八戒', '贪食': '猪八戒', '天蓬之怒': '猪八戒',
  '三十六变': '猪八戒', '净坛使者': '猪八戒', '倒打一耙': '猪八戒',
  '饕餮之口': '猪八戒', '九齿横扫': '猪八戒', '天蓬元帅': '猪八戒',
  // 沙僧
  '降妖宝杖': '沙僧', '金刚不坏': '沙僧', '流沙河': '沙僧',
  '负重前行': '沙僧', '罗汉金身': '沙僧', '天河倒灌': '沙僧',
  '降魔阵': '沙僧', '琉璃盏': '沙僧', '降妖宝杖·真': '沙僧',
  '降妖钵盂': '沙僧', '金身罗汉': '沙僧',
  // 白龙马
  '龙吟': '白龙马', '腾云驾雾': '白龙马', '疾风步': '白龙马',
  '龙爪': '白龙马', '呼风唤雨': '白龙马', '龙威': '白龙马',
  '龙息': '白龙马', '水遁': '白龙马', '龙战于野': '白龙马',
  '龙卷风暴': '白龙马',
  // 唐三藏
  '紧箍咒念': '唐三藏', '大乘佛法': '唐三藏', '金蝉脱壳': '唐三藏',
  '普渡众生': '唐三藏', '心经': '唐三藏', '取经誓愿': '唐三藏',
  '九环锡杖': '唐三藏', '袈裟护体': '唐三藏', '超度': '唐三藏',
  '金身护体': '唐三藏', '佛光普照': '唐三藏', '紧箍咒·禁': '唐三藏',
  '般若波罗蜜': '唐三藏',
}

export const FULL_IMG: Record<string, string> = {
  SUN_WUKONG: 'full_sunwukong', ZHU_BAJIE: 'full_zhubajie',
  SHA_SENG: 'full_shaseng', BAI_LONGMA: 'full_bailongma',
  TANG_SANZANG: 'full_tangsanzang'
}

/** CharacterClass -> Chinese图库目录，供立绘/模型及头像路径共用。 */
export const CHARACTER_DIR: Record<string, string> = {
  SUN_WUKONG: '孙悟空',
  ZHU_BAJIE: '猪八戒',
  SHA_SENG: '沙僧',
  BAI_LONGMA: '白龙马',
  TANG_SANZANG: '唐三藏',
}

/** 角色选择页使用的头像文件（白龙马使用透明 PNG 头像）。 */
export const CHARACTER_AVATAR: Record<string, string> = {
  SUN_WUKONG: 'avatar_sunwukong.jpg',
  ZHU_BAJIE: 'avatar_zhubajie.jpg',
  SHA_SENG: 'avatar_shawujing.jpg',
  BAI_LONGMA: 'bailongma.png',
  TANG_SANZANG: 'avatar_tangsanzang.jpg',
}

export const ENEMY_IMG: Record<string, string> = {
  '小妖': 'enemy_xiaoyao', '妖兵': 'enemy_yaobing', '白骨精': 'enemy_baigujing',
  '蜘蛛精': 'enemy_zhizhujing', '红孩儿': 'enemy_honghaier', '黄风怪': 'enemy_huangfengguai',
  '银角大王': 'enemy_yinjiaodawang', '牛魔王': 'enemy_niumowang', '铁扇公主': 'enemy_tieshangongzhu',
  '金角大王': 'enemy_jinjiaodawang', '黄袍怪': 'enemy_huangpaoguai', '灵感大王': 'enemy_linggandawang',
  // 51个扩展敌人（卡通图片）
  '寅将军': 'enemy_yinjiangjun', '熊山君': 'enemy_xiongshanjun', '特处士': 'enemy_techushi',
  '白衣秀士': 'enemy_baiyixiushi', '凌虚子': 'enemy_lingxuzi', '精细鬼': 'enemy_jingxigui',
  '伶俐虫': 'enemy_linglichong', '巴山虎': 'enemy_bashanhu', '倚海龙': 'enemy_yihailong',
  '压龙大仙': 'enemy_yalongdaxian', '六健将': 'enemy_liujianjiang', '奔波儿灞': 'enemy_benboerba',
  '灞波儿奔': 'enemy_baboerben', '小钻风': 'enemy_xiaozuanfeng', '刁钻古怪': 'enemy_diaozhuanguai',
  '古怪刁钻': 'enemy_guzhuaidiaozuan', '有来有去': 'enemy_youlaiyouqu', '斑衣鳜婆': 'enemy_banyiguepo',
  '玉面公主': 'enemy_yumianzhuhou', '白面狐狸': 'enemy_baimianhuli', '杏仙': 'enemy_xingxian',
  '虫妖干儿子': 'enemy_chongyaoganerzi', '虎先锋': 'enemy_huxianfeng', '狐阿七大王': 'enemy_huaqiyueiwang',
  '如意真仙': 'enemy_ruyizhenxian', '黄狮精': 'enemy_huangshijing', '七狮': 'enemy_qishi',
  '铁背苍狼怪': 'enemy_tiebeicanglangguai', '黑熊精': 'enemy_heixiongjing', '鼍龙': 'enemy_tuolong',
  '虎力大仙': 'enemy_hulidaxian', '鹿力大仙': 'enemy_lulidaxian', '羊力大仙': 'enemy_yanglidaxian',
  '独角兕大王': 'enemy_dujiaosidawang', '蝎子精': 'enemy_xiezijing', '六耳猕猴': 'enemy_liuerimihou',
  '九头虫': 'enemy_jiutouchong', '黄眉大王': 'enemy_huangmeidawang', '赛太岁': 'enemy_saitaisui',
  '百眼魔君': 'enemy_baiyanmojun', '青狮': 'enemy_qingshi', '白象': 'enemy_baixiang',
  '大鹏': 'enemy_dapeng', '白鹿精': 'enemy_bailujing', '金鼻白毛老鼠精': 'enemy_jinbibaimaolaoshujing',
  '南山大王': 'enemy_nanshandawang', '玉兔精': 'enemy_yutujing', '辟寒大王': 'enemy_pihandawang',
  '辟暑大王': 'enemy_pishudawang', '辟尘大王': 'enemy_pichendawang', '九灵元圣': 'enemy_jiulingyuansheng'
}

export const RELIC_IMG: Record<string, string> = {
  '紧箍咒': 'relic_jinguzhou', '龙鳞甲': 'relic_longlinjia', '蟠桃': 'relic_pantao',
  '八卦炉': 'relic_bagualu', '紫金铃': 'relic_zijinling', '袈裟': 'relic_jiasha',
  '通关文牒': 'relic_tongguanwendie', '人参果': 'relic_renshenguo', '避水珠': 'relic_bishuizhu',
  '风火轮': 'relic_fenghuolun', '照妖镜': 'relic_zhoyaojing', '甘露瓶': 'relic_ganluping',
  '九齿钉耙': 'relic_jiuchidingpa', '降妖宝杖': 'relic_xiangyabaozhang', '降魔宝杖': 'relic_xiangyabaozhang', '定海神针': 'relic_dinghaishenzhen',
  '锦襕袈裟': 'relic_jinlanjiasha',
  // 扩展宝物
  '芭蕉扇': 'relic_bajiaoshan', '九转金丹': 'relic_jiuzhuanjindan', '七星剑': 'relic_qixingjian',
  '紫金红葫芦': 'relic_zijinHonghulu', '玉净瓶': 'relic_yujingping', '生死簿': 'relic_shengsibu',
  '金刚琢': 'relic_jingangzhuo', '炼妖壶': 'relic_liaoyaoohu', '镇妖塔': 'relic_zhenyaota',
  '捆仙绳': 'relic_kunxiansheng',
  // 8件唐朝皇帝御赐宝物（卡通图片）
  '御赐金钵': 'relic_yucijinbo', '紫金钵盂': 'relic_zijinboyu', '大唐通关文牒': 'relic_datatongguanwendie',
  '李世民御剑': 'relic_lishiminyujian', '玄奘九环锡杖': 'relic_xuanzangjiuhuanxizhang',
  '御林军虎符': 'relic_yulinjunhufu', '御赐琉璃盏': 'relic_yuciliulizhan', '太宗玉玺': 'relic_taizongyuxi'
}

export const NODE_IMG: Record<string, string> = {
  BATTLE: 'node_battle', BOSS: 'node_boss', REST: 'node_rest', TREASURE: 'node_treasure',
  SHOP: 'node_shop', RANDOM: 'node_random', BONFIRE: 'node_bonfire'
  // 注：EMPEROR节点无图片资源，回退到 👑 emoji 显示
}

export const NODE_ICON: Record<string, string> = {
  BATTLE: '⚔️', BOSS: '👑', REST: '🏕️', TREASURE: '📦',
  SHOP: '🏯', RANDOM: '❓', BONFIRE: '🔥', EMPEROR: '👑'
}

// 皇帝宝物图片：8件御赐宝物已纳入 RELIC_IMG，复用 relicImgUrl 返回路径
export function emperorRelicImgUrl(name: string): string | null {
  return relicImgUrl(name)
}

export const EMOJI_MAP: Record<string, string> = {
  SUN_WUKONG: '🐵', ZHU_BAJIE: '🐷', SHA_SENG: '🟤', BAI_LONGMA: '🐴',
  TANG_SANZANG: '🧘'
}

export const INTENT_ICONS: Record<string, string> = {
  ATTACK: '⚔️', DEFEND: '🛡️', BUFF: '✨', DEBUFF: '☠️', SPECIAL: '🌟'
}

export const INTENT_LABELS: Record<string, string> = {
  ATTACK: '攻击', DEFEND: '防御', BUFF: '增益', DEBUFF: '减益'
}

export const TYPE_LABELS: Record<string, string> = {
  ATTACK: '攻击', SKILL: '技能', DEFENSE: '防御', POWER: '能力', STATUS: '状态'
}

export const DEBUFF_NAMES: Record<string, boolean> = {
  '虚弱': true, '易伤': true, '中毒': true, '灼烧': true, '冰冻': true
}

export const BUFF_ICONS: Record<string, string> = {
  '力量': '💪', '敏捷': '🌀', '虚弱': '😵‍💫', '易伤': '📉',
  '中毒': '☠️', '灼烧': '🔥', '冰冻': '🧊', '护盾': '🛡️',
  '力量增益': '💪', '敏捷增益': '🌀'
}

// ====== 辅助函数 ======

export function cardImgUrl(name: string, _upgraded?: boolean): string | null {
  const f = CARD_IMG[name]
  if (!f) return null
  const characterDir = CARD_CHARACTER_DIR[name]
  return characterDir
    ? `/images/${characterDir}/卡牌/${f}.jpg`
    : `/images/宝物/卡牌/通用/${f}.jpg`
}

export function fullImgUrl(charClass: string): string | null {
  const f = FULL_IMG[charClass]
  if (!f) return null
  const characterDir = CHARACTER_DIR[charClass]
  if (!characterDir) return null
  return `/images/${characterDir}/建模/${f}.jpg`
}

export function characterAvatarUrl(charClass: string): string | null {
  const characterDir = CHARACTER_DIR[charClass]
  const filename = CHARACTER_AVATAR[charClass]
  if (!characterDir || !filename) return null
  return `/images/${characterDir}/头像/${filename}`
}

export function enemyImgUrl(name: string): string | null {
  const f = ENEMY_IMG[name]
  if (!f) return null
  return `/images/敌人/建模/${f}.jpg`
}

export function relicImgUrl(name: string): string | null {
  const f = RELIC_IMG[name]
  if (!f) return null
  return `/images/宝物/遗物/${f}.jpg`
}

export function nodeImgUrl(type: string): string | null {
  const f = NODE_IMG[type]
  if (!f) return null
  return `/images/宝物/场景/${f}.jpg`
}
