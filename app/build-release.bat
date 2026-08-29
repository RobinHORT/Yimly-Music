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

:: 5 & 6. Check Windows user environment variables required for signing and verify keystore
if not defined KEYSTORE_PATH (
    echo [WARNING] KEYSTORE_PATH environment variable is not set.
) else (
    if not exist "%KEYSTORE_PATH%" (
        echo [WARNING] Keystore file not found at path specified by KEYSTORE_PATH: %KEYSTORE_PATH%
    ) else (
        echo [INFO] Keystore found at: %KEYSTORE_PATH%
    )
)
if not defined STORE_PASSWORD echo [WARNING] STORE_PASSWORD environment variable is not set.
if not defined KEY_ALIAS echo [WARNING] KEY_ALIAS environment variable is not set.
if not defined KEY_PASSWORD echo [WARNING] KEY_PASSWORD environment variable is not set.

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
for /f "delims=" %%f in ('dir /b /s "app\build\outputs\apk\release\*.apk" 2^>nul') do (
    set "LATEST_APK=%%f"
)

echo.
echo ===================================================
echo BUILD SUCCESSFUL
echo ===================================================
if defined LATEST_APK (
    for %%I in ("%LATEST_APK%") do (
        echo APK Filename: %%~nxI
        echo Full Path:    %%~fI
        echo APK Size:     %%~zI bytes
    )
) else (
    echo [INFO] APK built successfully. Check app\build\outputs\apk\release
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
