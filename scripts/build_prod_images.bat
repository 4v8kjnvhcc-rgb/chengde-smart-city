@echo off
chcp 65001 >nul
setlocal EnableExtensions

REM 双击：输入远程分支名 → fetch 后按 origin/<分支> 构建生产镜像
REM 默认输出到仓库 release/
REM 依赖：Git（可访问 origin）、Docker Desktop

cd /d "%~dp0.."
if not exist ".git" (
  echo [错误] 未找到 Git 仓库，请把本脚本放在仓库 scripts 目录下。
  pause
  exit /b 1
)

echo ========================================
echo   承德平台 - 生产镜像一键构建
echo   仓库: %CD%
echo   输出: %CD%\release
echo   来源: origin/^<分支^>（先 fetch，非本地工作区）
echo ========================================
echo.
set /p BRANCH=请输入远程分支名 ^(如 feature_yxj / main^): 
if "%BRANCH%"=="" (
  echo [错误] 分支名不能为空。
  pause
  exit /b 1
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
  pause
  exit /b 1
)

docker info >nul 2>&1
if errorlevel 1 (
  echo [提示] Docker 未运行，尝试启动 Docker Desktop...
  if exist "%LOCALAPPDATA%\Programs\DockerDesktop\Docker Desktop.exe" (
    start "" "%LOCALAPPDATA%\Programs\DockerDesktop\Docker Desktop.exe"
  ) else if exist "%ProgramFiles%\Docker\Docker\Docker Desktop.exe" (
    start "" "%ProgramFiles%\Docker\Docker\Docker Desktop.exe"
  ) else (
    echo [错误] 未找到 Docker Desktop 可执行文件，请手动启动后再执行。
    pause
    exit /b 1
  )
  echo [提示] 等待 Docker 引擎就绪（最长约 90 秒）...
  set /a _i=0
  :wait_docker
  timeout /t 5 /nobreak >nul
  docker info >nul 2>&1
  if not errorlevel 1 goto docker_ready
  set /a _i+=1
  if %_i% geq 18 (
    echo [错误] Docker 仍未就绪，请确认 Desktop 已启动后再双击本脚本。
    pause
    exit /b 1
  )
  goto wait_docker
  :docker_ready
  echo [提示] Docker 已就绪。
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build_prod_images.ps1" -Branch "%BRANCH%"
set ERR=%ERRORLEVEL%
echo.
if %ERR% neq 0 (
  echo [失败] 退出码 %ERR%
  echo 若报「表达式中有意外的标记」：请确认 scripts\build_prod_images.ps1 为 UTF-8 BOM（仓库已修复）。
) else (
  echo [完成] 已写入 release\ : 镜像 *.tar + prod-mid.env + prod-app.env
  echo        .51 用 mid.env，.55 用 app.env + docker load，现场不用再填密码
  echo        注意: release\prod-*.env 已是 Unix LF，可直接拷到 Linux
)
pause
exit /b %ERR%
