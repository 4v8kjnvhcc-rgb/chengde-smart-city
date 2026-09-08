@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion

REM 双击：按 origin/<分支> 构建生产镜像到 release/
REM 依赖：Git、Docker Desktop

cd /d "%~dp0.."
set ERR=1
if not exist ".git" (
  echo [错误] 未找到 Git 仓库，请把本脚本放在仓库 scripts 目录下。
  goto end_pause
)

echo ========================================
echo   承德平台 - 生产镜像一键构建
echo   仓库: %CD%
echo   输出: %CD%\release
echo   来源: origin/^<分支^> 先 fetch，非本地工作区
echo ========================================
echo.
set /p BRANCH=请输入远程分支名 ^(如 feature_yrj / main^): 
if "%BRANCH%"=="" (
  echo [错误] 分支名不能为空。
  goto end_pause
)

echo.
echo ==^> 远程分支: origin/%BRANCH%
echo ==^> 输出: %CD%\release
echo.
echo 注意: 以远程已 push 的提交为准；本地未 push 的改动不会进镜像。
echo.

where docker >nul 2>&1
if errorlevel 1 (
  echo [错误] 未找到 docker 命令，请先安装并启动 Docker Desktop。
  goto end_pause
)

docker info >nul 2>&1
if errorlevel 1 goto start_docker
goto run_build

:start_docker
echo [提示] Docker 未运行，尝试启动 Docker Desktop...
if exist "%LOCALAPPDATA%\Programs\DockerDesktop\Docker Desktop.exe" (
  start "" "%LOCALAPPDATA%\Programs\DockerDesktop\Docker Desktop.exe"
  goto wait_docker
)
if exist "%ProgramFiles%\Docker\Docker\Docker Desktop.exe" (
  start "" "%ProgramFiles%\Docker\Docker\Docker Desktop.exe"
  goto wait_docker
)
echo [错误] 未找到 Docker Desktop，请手动启动后再执行。
goto end_pause

:wait_docker
echo [提示] 等待 Docker 引擎就绪（最长约 90 秒）...
set /a _i=0

:wait_docker_loop
timeout /t 5 /nobreak >nul
docker info >nul 2>&1
if not errorlevel 1 (
  echo [提示] Docker 已就绪。
  goto run_build
)
set /a _i+=1
if !_i! geq 18 (
  echo [错误] Docker 仍未就绪，请确认 Desktop 已启动后再双击本脚本。
  goto end_pause
)
goto wait_docker_loop

:run_build
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build_prod_images.ps1" -Branch "%BRANCH%"
set ERR=!ERRORLEVEL!
echo.
if not "!ERR!"=="0" (
  echo [失败] 退出码 !ERR!
  echo 若报表达式解析错误：请确认 scripts\build_prod_images.ps1 为 UTF-8 BOM。
  echo 若 git fetch 失败：先检查网络，或在命令行加 -LocalOnly。
) else (
  echo [完成] 已写入 release\ : 镜像 *.tar + prod-mid.env + prod-app.env
  echo        .51 用 mid.env，.57 用 app.env + docker load
)

:end_pause
echo.
pause
exit /b !ERR!