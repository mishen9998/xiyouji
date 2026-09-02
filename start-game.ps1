# 西游记 Roguelike 卡牌游戏 - 启动器 (PowerShell 版)
# 此脚本由 启动游戏.bat 调用，避免 bat 文件的中文编码问题

$ErrorActionPreference = 'Stop'
$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendDir = $ProjectDir
$GameUrl = 'http://localhost:8080/'
$Port = 8080

$MavenCmd = Join-Path $ProjectDir 'mvnw.cmd'

# 设置控制台编码为 UTF-8，确保中文显示正确
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
try { chcp 65001 > $null } catch {}

function Write-Step($msg) { Write-Host $msg -ForegroundColor Cyan }
function Write-Ok($msg)    { Write-Host "    [OK] $msg" -ForegroundColor Green }
function Write-Err($msg)   { Write-Host "    [X]  $msg" -ForegroundColor Red }
function Write-Info($msg)  { Write-Host "    -    $msg" -ForegroundColor Gray }

function Test-PortListen($p) {
    try {
        $c = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction Stop
        if ($c) { return $true }
    } catch {}
    return $false
}

function Test-PortConnect($p) {
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        $c.Connect('localhost', $p)
        $c.Close()
        return $true
    } catch {}
    return $false
}

function Open-Browser {
    Write-Host ''
    Write-Host '============================================' -ForegroundColor Yellow
    Write-Host '   游戏已启动！正在打开浏览器...' -ForegroundColor Yellow
    Write-Host '============================================' -ForegroundColor Yellow
    Write-Host ''
    Write-Host "  访问地址: $GameUrl"
    Write-Host '  后端日志: 在"西游记后端服务"窗口中查看'
    Write-Host '  关闭游戏: 关闭"西游记后端服务"窗口即可'
    Write-Host '============================================'
    Write-Host ''
    Start-Process $GameUrl
    Write-Host '启动器将在 5 秒后自动关闭...' -ForegroundColor Gray
    Start-Sleep -Seconds 5
}

function Fail-Exit {
    Write-Host ''
    Write-Host '============================================' -ForegroundColor Red
    Write-Host '   启动失败！请根据上方提示解决问题后重试' -ForegroundColor Red
    Write-Host '============================================' -ForegroundColor Red
    Write-Host ''
    Write-Host '按任意键关闭此窗口...' -ForegroundColor Gray
    [void][System.Console]::ReadKey($true)
    exit 1
}

# ===== 主流程开始 =====
Write-Host ''
Write-Host '============================================' -ForegroundColor Yellow
Write-Host '   西游记 Roguelike 卡牌游戏 - 启动器' -ForegroundColor Yellow
Write-Host '============================================' -ForegroundColor Yellow
Write-Host ''
Write-Host "  项目目录: $ProjectDir"
Write-Host "  访问地址: $GameUrl"
Write-Host ''

# ===== 1. 检查后端是否已在运行 =====
Write-Step '[1/6] 检查后端服务状态...'
if (Test-PortListen $Port) {
    Write-Ok "后端已在运行 (端口 $Port 已被占用)"
    Open-Browser
    exit 0
}
Write-Info '后端未运行，准备启动...'
Write-Host ''

# ===== 2. 检查 Java =====
Write-Step '[2/6] 检查 Java 环境...'
$javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
if ($javaCommand) {
    $javaExe = $javaCommand.Source
    Write-Ok "Java 已找到: $javaExe"
} else {
    Write-Err '未在 PATH 中找到 Java 17'
    Write-Info '请安装 JDK 17 并配置 JAVA_HOME/PATH'
    Fail-Exit
}
Write-Host ''

# ===== 3. 检查 Maven =====
Write-Step '[3/6] 检查 Maven 环境...'
if (Test-Path $MavenCmd) {
    Write-Ok "Maven 已找到: $MavenCmd"
} else {
    Write-Err "未找到 Maven: $MavenCmd"
    Write-Info '请修改脚本中的 $MavenCmd 配置'
    Fail-Exit
}
Write-Host ''

# ===== 4. 检查依赖服务（可选，standalone profile 用 H2 内存数据库）=====
Write-Step '[4/6] 检查依赖服务 (MySQL / Redis) - 可选...'

$useStandalone = $true
if ((Test-PortListen 3306) -and (Test-PortListen 6379)) {
    Write-Ok "MySQL 和 Redis 已启动，将使用 distributed profile"
    $useStandalone = $false
} else {
    Write-Info "MySQL 未启动 - 将使用 H2 内存数据库 (standalone profile)"
}

if (Test-PortListen 6379) {
    Write-Ok "Redis 已启动 (端口 6379)"
} else {
    Write-Info "Redis 未启动 - 单机模式不需要 Redis"
}
Write-Host ''

# ===== 5. 启动后端 (新窗口) =====
Write-Step '[5/6] 启动 Spring Boot 后端服务...'
Write-Info '后端窗口标题: "西游记后端服务"'
Write-Info '首次启动需编译，请耐心等待 30-60 秒'
Write-Host ''

# 根据 MySQL 状态决定使用哪个 profile
if ($useStandalone) {
    $profileArg = '-Dspring-boot.run.profiles=standalone'
} else {
    $profileArg = '-Dspring-boot.run.profiles=distributed'
}

# 直接用 cmd /k 启动 mvn，避免中间 bat 文件的编码问题
$jarPath = Join-Path $ProjectDir 'xiyouji-bootstrap\target\xiyouji-bootstrap-1.0.0.jar'
$backendCmd = "title 西游记后端服务 && cd /d `"$BackendDir`" && `"$MavenCmd`" -pl xiyouji-bootstrap -am package -DskipTests -q && `"$javaExe`" -jar `"$jarPath`" --spring.profiles.active=$($profileArg.Replace('-Dspring-boot.run.profiles=', ''))"
Start-Process -FilePath 'cmd.exe' -ArgumentList '/k', $backendCmd -WindowStyle Normal
Write-Host ''

# ===== 6. 等待后端就绪 =====
Write-Step '[6/6] 等待后端就绪...'
$waitCount = 0
$maxWait = 60  # 最多等待 60 * 3 = 180 秒

while ($waitCount -lt $maxWait) {
    Start-Sleep -Seconds 3
    $waitCount++

    if (Test-PortConnect $Port) {
        Write-Ok "后端已就绪 (耗时约 $($waitCount * 3) 秒)"
        Open-Browser
        exit 0
    }

    Write-Info "已等待 $($waitCount * 3) 秒, 继续等待..."
}

# 超时
Write-Err "后端启动超时 (约 3 分钟)"
Write-Host ''
Write-Host '常见问题:' -ForegroundColor Yellow
Write-Info '1. 端口 8080 被占用 - 关闭其他占用进程'
Write-Info '2. MySQL 未启动 - 启动 MySQL 服务'
Write-Info '3. Redis 未启动 - 启动 Redis 服务'
Write-Info '4. 代码编译错误 - 请查看"西游记后端服务"窗口中的错误信息'
Fail-Exit
