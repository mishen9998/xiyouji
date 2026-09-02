# ========================================
# 西游记 - 本地一键构建并打包更新包
# 用法: powershell -ExecutionPolicy Bypass -File build-update.ps1
# ========================================

$ErrorActionPreference = "Stop"
$PROJECT_ROOT = $PSScriptRoot
$STAGING_DIR = Join-Path $PROJECT_ROOT 'tmp\update-pkg'
$OUTPUT_ZIP = Join-Path $PROJECT_ROOT '西行更新包.zip'

Write-Host ""
Write-Host "============================================" -ForegroundColor Yellow
Write-Host "  西游记 - 本地构建 & 打包更新包" -ForegroundColor Yellow
Write-Host "============================================" -ForegroundColor Yellow
Write-Host ""

# ===== Step 1: 构建前端 =====
Write-Host "[1/5] 构建前端 (Vue3 + Vite)..." -ForegroundColor Cyan
$frontendDir = "$PROJECT_ROOT\frontend-vue"
Push-Location $frontendDir
try {
    Write-Host "  安装锁定版本依赖..." -ForegroundColor Gray
    npm ci 2>&1 | Out-Null
    npm run build 2>&1 | Select-Object -Last 5
    Write-Host "  前端构建完成" -ForegroundColor Green
} catch {
    Write-Host "  [ERROR] 前端构建失败: $_" -ForegroundColor Red
    exit 1
}
Pop-Location

# 前端 dist 和插图由 Maven resources 在打包时合入 JAR，
# 避免将构建产物回写到 src/main/resources 导致工作区变脏。
Write-Host "[2/5] 前端产物已就绪，将由 Maven 合入 JAR" -ForegroundColor Green

# ===== Step 3: 构建后端JAR =====
Write-Host "[3/5] 构建后端JAR (Maven)..." -ForegroundColor Cyan
Push-Location $PROJECT_ROOT
try {
    # 使用仓库内 Maven Wrapper，避免依赖本机 Maven 安装路径
    & "$PROJECT_ROOT\mvnw.cmd" -pl xiyouji-bootstrap -am clean package -DskipTests -q 2>&1 | Select-Object -Last 5
    $jarPath = "$PROJECT_ROOT\xiyouji-bootstrap\target\xiyouji-bootstrap-1.0.0.jar"
    if (-not (Test-Path $jarPath)) {
        Write-Host "  [ERROR] JAR构建失败，未找到输出文件" -ForegroundColor Red
        exit 1
    }
    $jarSize = [math]::Round((Get-Item $jarPath).Length / 1MB, 2)
    Write-Host "  JAR构建完成: $jarSize MB" -ForegroundColor Green
} catch {
    Write-Host "  [ERROR] Maven构建失败: $_" -ForegroundColor Red
    exit 1
}
Pop-Location

# ===== Step 4: 打包更新包（只含JAR + 更新脚本）=====
Write-Host "[4/5] 打包更新包..." -ForegroundColor Cyan
if (Test-Path $STAGING_DIR) { Remove-Item -Recurse -Force $STAGING_DIR }
New-Item -ItemType Directory -Path $STAGING_DIR -Force | Out-Null
New-Item -ItemType Directory -Path "$STAGING_DIR\xiyouji-bootstrap\target" -Force | Out-Null

# 只复制更新需要的文件
Copy-Item "$PROJECT_ROOT\xiyouji-bootstrap\target\xiyouji-bootstrap-1.0.0.jar" "$STAGING_DIR\xiyouji-bootstrap\target\"
Copy-Item "$PROJECT_ROOT\update-cloud.sh" "$STAGING_DIR\"

# 删除旧zip
if (Test-Path $OUTPUT_ZIP) { Remove-Item -Force $OUTPUT_ZIP }
Compress-Archive -Path "$STAGING_DIR\*" -DestinationPath $OUTPUT_ZIP -CompressionLevel Optimal

$zipSize = [math]::Round((Get-Item $OUTPUT_ZIP).Length / 1MB, 2)
Write-Host "  更新包已生成: $zipSize MB" -ForegroundColor Green

# ===== Step 5: 完成 =====
Write-Host ""
Write-Host "[5/5] 完成！" -ForegroundColor Cyan
Write-Host ""
Write-Host "============================================" -ForegroundColor Yellow
Write-Host "  构建完成！" -ForegroundColor Yellow
Write-Host "============================================" -ForegroundColor Yellow
Write-Host ""
Write-Host "  更新包路径: $OUTPUT_ZIP" -ForegroundColor White
Write-Host "  更新包大小: $zipSize MB" -ForegroundColor White
Write-Host ""
Write-Host "  上传步骤:" -ForegroundColor Cyan
Write-Host "    1. 宝塔面板上传 西行更新包.zip 到服务器 /www/wwwroot/xiyouji/"
Write-Host "    2. 终端执行:"
Write-Host "       cd /www/wwwroot/xiyouji" -ForegroundColor Gray
Write-Host "       unzip -o 西行更新包.zip   # 覆盖旧JAR" -ForegroundColor Gray
Write-Host "       bash update-cloud.sh      # 重新构建并启动" -ForegroundColor Gray
Write-Host ""
Write-Host "============================================" -ForegroundColor Yellow
