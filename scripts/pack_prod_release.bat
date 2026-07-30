@echo off
chcp 65001 >nul
setlocal EnableExtensions

REM 双击：输入远程分支名 → fetch 后按 origin/<分支> 打生产代码包
REM 默认输出到仓库 release/
REM 依赖：本机已安装 Git，且能访问 origin

cd /d "%~dp0.."
if not exist ".git" (
  echo [错误] 未找到 Git 仓库，请把本脚本放在仓库 scripts 目录下。
  pause
  exit /b 1
)

echo ========================================
echo   承德平台 - 生产代码包一键打包
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
echo 注意: 以远程已 push 的提交为准；本地未 push 的改动不会进包。
echo.

where git >nul 2>&1
if errorlevel 1 (
  echo [错误] 未找到 git 命令。
  pause
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0pack_prod_release.ps1" -Branch "%BRANCH%"
set ERR=%ERRORLEVEL%
echo.
if %ERR% neq 0 (
  echo [失败] 退出码 %ERR%
) else (
  echo [完成] 已写入 release\ : 代码 *.tar.gz + prod-mid.env + prod-app.env
  echo        拷到 .51 / .55 解压即可
)
pause
exit /b %ERR%
