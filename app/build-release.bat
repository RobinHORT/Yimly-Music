@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo Yimly Music - Release APK Build Script (Windows)
echo ===================================================

:: 1. Confirm it is being run from the Android project root
if not exist "gradlew.bat" (
    echo [ERROR] gradlew.bat not found in the current directory.
    echo Please run this script from the root directory of the Android project.
    goto :error
)

:: 2 & 3. Locate Android SDK automatically
set "SDK_PATH="
if defined ANDROID_HOME (
    if exist "%ANDROID_HOME%" set "SDK_PATH=%ANDROID_HOME%"
)
if not defined SDK_PATH if defined ANDROID_SDK_ROOT (
    if exist "%ANDROID_SDK_ROOT%" set "SDK_PATH=%ANDROID_SDK_ROOT%"
)
if not defined SDK_PATH (
    if exist "%LOCALAPPDATA%\Android\Sdk" set "SDK_PATH=%LOCALAPPDATA%\Android\Sdk"
)

if not defined SDK_PATH (
    echo [ERROR] Android SDK could not be found!
    echo Please install Android Studio and ensure the Android SDK is installed,
    echo or set the ANDROID_HOME / ANDROID_SDK_ROOT environment variable.
    goto :error
)

echo [INFO] Found Android SDK at: %SDK_PATH%

:: 4. Create/update the project local.properties with the detected SDK path
echo sdk.dir=%SDK_PATH:\=\\%> local.properties
echo [INFO] Updated local.properties with SDK path.

:: 5 & 6. Require a complete release signing configuration before building
if not defined KEYSTORE_PATH (
    echo [ERROR] KEYSTORE_PATH environment variable is not set.
    goto :error
)
if not exist "%KEYSTORE_PATH%" (
    echo [ERROR] Keystore file not found at KEYSTORE_PATH.
    goto :error
)
if not defined STORE_PASSWORD (
    echo [ERROR] STORE_PASSWORD environment variable is not set.
    goto :error
)
if not defined KEY_ALIAS (
    echo [ERROR] KEY_ALIAS environment variable is not set.
    goto :error
)
if not defined KEY_PASSWORD (
    echo [ERROR] KEY_PASSWORD environment variable is not set.
    goto :error
)
echo [INFO] Release signing configuration is present and the keystore file exists.

:: 7. Verify the existing Gradle wrapper works
echo [INFO] Verifying Gradle wrapper version...
call gradlew.bat --version
if errorlevel 1 (
    echo [ERROR] Gradle wrapper verification failed.
    goto :error
)

:: 8. Stop old Gradle daemons
echo [INFO] Stopping existing Gradle daemons...
call gradlew.bat --stop

:: 9. Build the signed release APK
echo [INFO] Building signed release APK...
call gradlew.bat assembleRelease --no-configuration-cache
if errorlevel 1 (
    goto :build_failed
)

:: 11. Locate newest APK in app\build\outputs\apk\release
set "LATEST_APK="
for /f "delims=" %%f in ('powershell -NoProfile -Command "Get-ChildItem -Path app/build/outputs/apk/release -Filter *.apk -File -Recurse | Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName"') do (
    set "LATEST_APK=%%f"
)

if not defined LATEST_APK (
    echo [ERROR] Gradle reported success but no release APK was found.
    goto :build_failed
)

echo.
echo ===================================================
echo BUILD SUCCESSFUL
echo ===================================================
for %%I in ("%LATEST_APK%") do (
    echo APK Filename: %%~nxI
    echo Full Path:    %%~fI
    echo APK Size:     %%~zI bytes
)
echo ===================================================
goto :end

:build_failed
echo.
echo ===================================================
echo BUILD FAILED
echo ===================================================
goto :error_exit

:error
echo.
echo ===================================================
echo BUILD FAILED DUE TO SETUP OR ENVIRONMENT ERROR
echo ===================================================

:error_exit
echo Press any key to exit...
pause >nul
exit /b 1

:end
echo Press any key to exit...
pause >nul
exit /b 0
