# 双端体验与本地验收（T5）

本阶段不部署云服务器。前端默认使用轻量插画，战斗页面可主动切换 3D；低端设备、系统减少动画、后台或不可见场景不会持续运行 WebGL 动画。

## 启动隔离的本地体验环境

要求 Java 17、Node 20.18.1。不要使用占用中的 80/443 或其它项目容器；以下端口仅为本轮示例，可自行换空闲端口。

项目根目录构建纯后端（未改变后端业务）：

```powershell
.\mvnw.cmd -B package -DskipTests
java -jar xiyouji-bootstrap/target/xiyouji-bootstrap-1.0.0.jar --spring.profiles.active=standalone --server.port=18085 --app.security.enforce-jwt=true --jwt.secret=t5-local-only-measurement-key-not-a-production-key-20260917 --app.cors.allowed-origins=http://127.0.0.1:4175,http://127.0.0.1:5175
```

standalone 是内存 H2/JVM 会话，重启会失去本地进度，不是 Redis 跨实例验收环境。示例密钥只供本机，不得用于部署。

另一个终端进入 `frontend-vue`：

```powershell
npm ci
npm exec --package=node@20.18.1 -- node node_modules/vue-tsc/bin/vue-tsc.js --noEmit
npm exec --package=node@20.18.1 -- node node_modules/vite/bin/vite.js build
$env:VITE_API_TARGET = 'http://127.0.0.1:18085'
npm exec --package=node@20.18.1 -- node node_modules/vite/bin/vite.js preview --host 127.0.0.1 --port 4175 --strictPort
```

访问 http://127.0.0.1:4175 ，点击游客模式，单人游戏→选角色→开始西行；多人游戏→创建房间→分享八位房间码→选择角色→准备。界面与 API 同源代理；若使用其它前端端口，要同时改本地后端 CORS 白名单。不要用外部静态路径覆盖旧 JAR 来测量新 UI。

## 回归与证据

```powershell
$env:PLAYWRIGHT_BASE_URL = 'http://127.0.0.1:4175'
npm exec --package=node@20.18.1 -- node node_modules/vitest/vitest.mjs run
npm exec --package=node@20.18.1 -- node node_modules/@playwright/test/cli.js test e2e/t5-experience.spec.ts --workers=1
npm exec --package=node@20.18.1 -- node node_modules/@playwright/test/cli.js test e2e/t5-battle-viewport.spec.ts --workers=1 --retries=0
npm exec --package=node@20.18.1 -- node node_modules/@playwright/test/cli.js test e2e/t4-recovery.spec.ts e2e/reward-confirmation.spec.ts --workers=1
```

T5 experience 中的视口矩阵使用固定 API JSON，验证的是实际生产组件、图片加载与交互，不将它称作完整后端通关测试。独立的真实本地用例覆盖游客建游戏与多人建房/选角/准备；T4 用例仍连接真实后端校验断线和未知回执。三层、五人、双实例完整流程由 T6 最终集成执行。

`t5-battle-viewport` 额外验证单双人战斗与五目标攻击的预告、生命、法力、格挡和确认按钮首屏可见，检查滚动祖先裁切与中心点遮挡，不只检查 DOM 存在。200% 文本用根字号翻倍模拟（并断言实际预告字体翻倍），放大后允许战场/底栏滚动，不宣称所有内容仍同时放入首屏。多目标逐人伤害明细保留全部数据，自己的结果优先，可键盘或触控滚动。

性能脚本、固定条件与原始数据含义见 `frontend-vue/e2e/T5-PERFORMANCE.md`。测量时不要并行构建或跑其它浏览器。报告中的 CPU 4 倍/网络模拟不是手机真机成绩或生产 SLA；正式 T6 素材接入后必须整套重测。

## T6 美术接入边界

- 原图保留于 `assets/images`；`frontend-vue/scripts/derive-images.mjs` 生成 320/640/960 像素 AVIF/WebP，记录源 SHA256，单张派生图不得超过 200 KiB。
- 修改 `frontend-vue/src/constants/scene-images.json` 的 journey/camp/blackwind/firemountain/lionridge/completion/blackbear/bullking/roc，然后按 scripts/README.md 重跑生成。当前映射明确复用旧插画，不是已经生成的九张正式素材。
- 背景、立绘使用 `ResponsiveImage`，传入准确 `sizes`、关键图 `critical`；列表默认 lazy/async 并保留失败占位。不要把全部素材预加载。地图点击战斗节点时仅预载下一战的一张背景。
- `EnemyIntent` 使用服务端锁定动作/目标/次数/状态/逐目标伤害，前端不自行推测伤害。T4 的 UNKNOWN 只查询原回执，不因版本增长或网络恢复自动重执。
- MapView/MultiplayerMapView 共用 `useMapLayout`，以容器 ResizeObserver 更新节点与 SVG 连线。手机横屏/字号放大时战场和底栏可独立滚动，主按钮保留安全区。
