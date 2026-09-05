# 新电脑启动与双人协作

## 获取项目

安装 Git、JDK 17、Node.js 20 和 Docker Desktop（启用 Linux 容器），然后：

```bash
git clone https://github.com/mishen9998/xiyouji.git
cd xiyouji
git switch main
git pull --ff-only
```

Windows 双击根目录的 `启动演示.bat`，第一次会构建镜像并初始化 MySQL/Redis。打开 http://localhost:8080。本机密钥由脚本生成，无须共享服务器密码。

IDEA 打开根 pom.xml，选择 JDK 17，导入五个 Maven 模块。调试可运行 `com.xiyouji.XiyoujiApplication`，参数 `--spring.profiles.active=standalone`。另开终端：

```bash
npm ci --prefix frontend-vue
npm run dev --prefix frontend-vue
```

访问 http://localhost:5173。standalone 数据重启后清空；测试 MySQL/Redis 和多人跨实例行为用 Docker 方式。

## 协作约定

每人从更新后的 main 创建自己的功能分支，通过 PR 合并：

```bash
git switch main
git pull --ff-only
git switch -c feature/your-feature
# 修改并测试后
git add <本次修改的文件>
git commit -m "描述本次改动"
git push -u origin feature/your-feature
```

仓库所有者在 GitHub 仓库 Settings → Collaborators 中邀请伙伴，由伙伴接受后获得推送权限。也可以先 Fork 再提交 PR。不要共用账号或向对方发送 SSH 私钥、服务器 .env、生产数据。

提交前至少运行前端单测与构建、Maven verify。完整门禁在 GitHub Actions；不要在服务器直接编辑业务代码，也不要把线上发布等同于合并代码。演示发布由固定提交镜像进行。

```powershell
npm run test:unit --prefix frontend-vue
npm run build --prefix frontend-vue
.\mvnw.cmd -B -pl xiyouji-bootstrap -am verify
```

公网 IP 演示使用 HTTP/WS，地址与实际验收结果见部署记录。开发使用各自本机环境，避免多人共用线上数据库进行调试。
