@echo off
chcp 65001 >nul
setlocal EnableExtensions

REM 双击或 cmd 运行：输入分支名 → 构建该分支前后端生产镜像并导出 tar
REM 依赖：本机已安装 Git、Docker Desktop（且已启动）

cd /d "%~dp0.."
if not exist ".git" (
  echo [错误] 未找到 Git 仓库，请把本脚本放在仓库 scripts 目录下。
  pause
  exit /b 1
)

echo ========================================
echo   承德平台 - 生产镜像一键构建
echo   仓库: %CD%
echo ========================================
echo.
set /p BRANCH=请输入分支名 ^(如 feature_yxj / main^): 
if "%BRANCH%"=="" (
  echo [错误] 分支名不能为空。
  pause
  exit /b 1
)

set "OUTDIR=%USERPROFILE%\Desktop"
set /p OUTDIR_INPUT=输出目录 ^(直接回车=桌面^): 
if not "%OUTDIR_INPUT%"=="" set "OUTDIR=%OUTDIR_INPUT%"

echo.
echo ==> 分支: %BRANCH%
echo ==> 输出: %OUTDIR%
echo.

where docker >nul 2>&1
if errorlevel 1 (
  echo [错误] 未找到 docker 命令，请先安装并启动 Docker Desktop。
  pause
  exit /b 1
)

docker info >nul 2>&1
if errorlevel 1 (
  echo [错误] Docker 未运行，请先启动 Docker Desktop 后再执行。
  pause
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build_prod_images.ps1" -Branch "%BRANCH%" -OutDir "%OUTDIR%"
set ERR=%ERRORLEVEL%
echo.
if %ERR% neq 0 (
  echo [失败] 退出码 %ERR%
) else (
  echo [完成] 输出目录含: 镜像 *.tar + prod-mid.env + prod-app.env
  echo        .51 用 mid.env，.55 用 app.env + docker load，现场不用再填密码
)
pause
exit /b %ERR%
