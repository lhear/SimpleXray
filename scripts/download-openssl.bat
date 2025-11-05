@echo off
REM OpenSSL Download Script for Android (Windows)
REM Downloads prebuilt OpenSSL libraries for Android

setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..\
set OPENSSL_DIR=%PROJECT_ROOT%app\src\main\jni\openssl

echo OpenSSL Download Script for Android
echo ==========================================

REM Create OpenSSL directory structure
if not exist "%OPENSSL_DIR%\include" mkdir "%OPENSSL_DIR%\include"
if not exist "%OPENSSL_DIR%\lib\arm64-v8a" mkdir "%OPENSSL_DIR%\lib\arm64-v8a"
if not exist "%OPENSSL_DIR%\lib\armeabi-v7a" mkdir "%OPENSSL_DIR%\lib\armeabi-v7a"
if not exist "%OPENSSL_DIR%\lib\x86" mkdir "%OPENSSL_DIR%\lib\x86"
if not exist "%OPENSSL_DIR%\lib\x86_64" mkdir "%OPENSSL_DIR%\lib\x86_64"

echo.
echo OpenSSL directory structure created at:
echo   %OPENSSL_DIR%
echo.
echo Next Steps:
echo 1. Download OpenSSL libraries from:
echo    - https://github.com/leenjewel/openssl_for_ios_and_android
echo    - https://github.com/viperforge/android-openssl-prebuilt
echo.
echo 2. Extract libraries to:
echo    %OPENSSL_DIR%\lib\arm64-v8a\
echo    %OPENSSL_DIR%\lib\armeabi-v7a\
echo.
echo 3. Extract headers to:
echo    %OPENSSL_DIR%\include\openssl\
echo.
echo 4. Verify installation:
echo    dir "%OPENSSL_DIR%\include\openssl\evp.h"
echo    dir "%OPENSSL_DIR%\lib\arm64-v8a\libcrypto.a"
echo    dir "%OPENSSL_DIR%\lib\arm64-v8a\libssl.a"
echo.

REM Create README
(
echo # OpenSSL for Android
echo.
echo This directory should contain OpenSSL libraries for Android.
echo.
echo ## Required Structure
echo.
echo openssl/
echo ├── include/
echo │   └── openssl/
echo │       ├── evp.h
echo │       ├── aes.h
echo │       ├── chacha.h
echo │       └── ...
echo └── lib/
echo     ├── arm64-v8a/
echo     │   ├── libcrypto.a
echo     │   └── libssl.a
echo     └── armeabi-v7a/
echo         ├── libcrypto.a
echo         └── libssl.a
echo.
echo ## Quick Setup
echo.
echo Download prebuilt libraries from:
echo - https://github.com/leenjewel/openssl_for_ios_and_android
echo - https://github.com/viperforge/android-openssl-prebuilt
echo.
echo Extract to this directory.
) > "%OPENSSL_DIR%\README.md"

echo README.md created at: %OPENSSL_DIR%\README.md
echo.
echo Setup complete! Please download and install OpenSSL libraries.
pause


