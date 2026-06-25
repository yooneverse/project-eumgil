@echo off
setlocal

set "ROOT=%~dp0.."
set "REF=%ROOT%\codex\design-md\awesome-design-md"
set "DESIGNS=%REF%\design-md"

if "%~1"=="" goto usage

if /I "%~1"=="list" goto list
if /I "%~1"=="add" goto add
if /I "%~1"=="update" goto update

goto usage

:list
if not exist "%DESIGNS%" (
  echo Missing local reference clone: %REF%
  echo Run: git clone https://github.com/VoltAgent/awesome-design-md.git codex\design-md\awesome-design-md
  exit /b 1
)
dir /b /ad "%DESIGNS%"
exit /b 0

:add
if "%~2"=="" (
  echo Usage: scripts\design-md.cmd add ^<brand^>
  echo Example: scripts\design-md.cmd add vercel
  exit /b 1
)
pushd "%ROOT%" >nul
npx.cmd getdesign@latest add %~2
set "STATUS=%ERRORLEVEL%"
popd >nul
exit /b %STATUS%

:update
if not exist "%REF%\.git" (
  echo Missing local reference clone: %REF%
  echo Run: git clone https://github.com/VoltAgent/awesome-design-md.git codex\design-md\awesome-design-md
  exit /b 1
)
git -C "%REF%" pull --ff-only
exit /b %ERRORLEVEL%

:usage
echo Usage:
echo   scripts\design-md.cmd list
echo   scripts\design-md.cmd add ^<brand^>
echo   scripts\design-md.cmd update
exit /b 1
