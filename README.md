# 🐵 西游记：西行之路 — Roguelike卡牌游戏

基于《杀戮尖塔》(Slay the Spire) 玩法的**西游记题材** Roguelike 卡牌构筑游戏。

**技术栈**: Java 17 + Spring Boot 3.4 + H2/MySQL + Redis + Vue 3 / TypeScript / Vite

## 🎮 游戏特色

- **5位可操控角色**：孙悟空、猪八戒、沙僧、白龙马、唐三藏，各有专属卡牌和遗物
- **卡牌对战系统**：攻击/技能/防御/能力 四种卡牌类型，80+ 张卡牌
- **Roguelike地图**：27层随机生成的地图，包含战斗、Boss、休息、宝箱、商店、篝火、随机事件、唐朝皇帝赐宝
- **Buff/Debuff系统**：力量、敏捷、虚弱、易伤、中毒、灼烧、冰冻、格挡等
- **遗物系统**：30+ 种遗物，含角色专属遗物与 8 件唐朝皇帝御赐宝物
- **卡牌升级**：在篝火处可升级卡牌
- **商店系统**：购买卡牌，通关文牒遗物享8折优惠
- **多人协作**：WebSocket(STOMP) 实时联机，支持房间组队共同战斗

## 🚀 一键启动

### 环境要求

| 软件 | 版本要求 |
|------|---------|
| JDK | 17 或更高 |
| Maven | 3.6+ (或使用 mvnw) |
| Node.js | 18+ (仅前端开发时需要) |

### 启动步骤

1. **双击运行** `start_game.bat`
2. 等待编译完成（首次需要下载Maven依赖，约2-5分钟）
3. 浏览器打开 **http://localhost:8080/index.html**

### 手动启动

```bash
cd backend
mvn clean package -DskipTests
java -jar target/xiyouji-roguelike-1.0.0.jar
# 访问 http://localhost:8080/index.html
```

## 🗄️ 数据库

默认使用 **H2 内存数据库** (文件模式)，开箱即用无需额外配置。

### 切换 MySQL

编辑 `backend/src/main/resources/application.yml`，取消注释 MySQL 配置。

或在启动时指定 profile：

```bash
java -jar target/xiyouji-roguelike-1.0.0.jar --spring.profiles.active=mysql
```

手动创建 MySQL 数据库：

```sql
CREATE DATABASE xiyouji CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

或用 `sql/init.sql` 初始化。

## 📂 项目结构

```
西游记/
├── backend/                          # Spring Boot 后端
│   ├── pom.xml                       # Maven 构建配置
│   └── src/main/java/com/xiyouji/
│       ├── XiyoujiApplication.java   # 启动类
│       ├── config/WebConfig.java     # Web/CORS 配置
│       ├── controller/
│       │   └── GameController.java   # REST API 控制器
│       ├── model/
│       │   ├── Card.java             # 卡牌实体
│       │   ├── Enemy.java            # 敌人实体
│       │   ├── GameCharacter.java    # 角色实体
│       │   ├── MapNode.java          # 地图节点
│       │   ├── Relic.java            # 遗物实体
│       │   └── enums/               # 枚举类
│       ├── repository/               # JPA Repository
│       └── service/
│           ├── BattleState.java      # 战斗状态
│           ├── BattleService.java    # 战斗服务
│           ├── DataInitializer.java  # 数据初始化
│           ├── GameService.java      # 游戏主服务
│           └── GameSession.java      # 游戏会话
├── frontend/                         # 前端页面
│   ├── index.html                    # 主页面
│   ├── css/game.css                  # 样式表
│   └── js/game.js                    # 游戏逻辑
├── sql/init.sql                      # MySQL 初始化脚本
├── 一键启动.bat                       # Windows 一键启动
└── README.md
```

## 🃏 卡牌系统

### 角色专属卡牌

| 角色 | 卡牌示例 | 特点 |
|------|----------|------|
| 孙悟空 | 金箍棒法、七十二变、大闹天宫 | 高攻击、变化多端 |
| 猪八戒 | 九齿钉耙、狼吞虎咽、天蓬之怒 | 攻守兼备、回血 |
| 沙僧 | 金刚不坏、流沙河、罗汉金身 | 高防御、护盾 |
| 白龙马 | 腾云驾雾、疾风步、龙威 | 抽牌、速度 |

### 卡牌类型

- **攻击** (红色) - 造成伤害
- **技能** (蓝色) - 特殊效果
- **防御** (绿色) - 获得格挡
- **能力** (紫色) - 永久性Buff

## ⚔️ 敌人

普通敌人 (6种): 小妖、妖兵、白骨精、蜘蛛精、红孩儿、黄风怪

Boss (6种): 银角大王、牛魔王、铁扇公主、金角大王、黄袍怪、灵感大王

扩展敌人 (51种): 寅将军、熊山君、黑熊精、蝎子精、九灵元圣等西游妖怪（均配卡通图片）

## 🎯 快捷键

战斗中使用键盘快捷键:
- **1-9** - 打出对应位置的手牌
- **E** - 结束回合

## 🛠️ 开发

### IDE 导入 (IntelliJ IDEA)

1. File → Open → 选择 `backend/pom.xml` → Open as Project
2. 等待 IDEA 下载依赖
3. 运行 `XiyoujiApplication.java`
4. 浏览器打开 **http://localhost:8080/index.html**

### 前端开发

```bash
cd frontend-vue
npm install
npm run dev    # 开发服务器 http://localhost:5173（代理 API 到 8080）
npm run build  # 构建产物输出到 backend/static
```

### 扩展开发

- **添加新卡牌**: 在 `DataInitializer.java` 中添加 `cardOf()` 调用
- **添加新敌人**: 在 `DataInitializer.java` 中添加 `enemy()` 调用
- **添加新遗物**: 在 `DataInitializer.java` 中添加 `relic()` 调用
- **添加新角色**: 在 `CharacterClass` 枚举中添加，并在 `DataInitializer` 中配置
