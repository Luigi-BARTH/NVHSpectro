# NVHSpectro - Agent Guidelines & Procedures

## Architecture Overview
- **Pattern**: MVVM (Model-View-ViewModel) + Jetpack Compose.
- **Signal Processing**: 
  - 16-bit PCM Audio input via `AudioRecord` with **50% overlap** sliding window.
  - Hanning Windowing + FFT via `JTransforms` library.
  - dB FS scaling: `20 * log10(mag / (fftSize / 4.0))` normalized to `[-1.0, 1.0]` PCM full scale with coherent gain compensation.
- **Rendering**:
  - High-performance bitmap rendering (`Bitmap.setPixels`) with Jet Colormap.
  - Hardware accelerated Canvas rendering in Compose.
  - Dynamic frequency zoom (0 to `maxFreq` Hz) and configurable time window (3s to 30s).

## Android Build & Deployment Procedure
When building and launching on a connected Android phone (via USB Debugging):
1. **Set Java Environment & Compile**:
   ```powershell
   $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
   .\gradlew installDebug
   ```
2. **Locate ADB Executable**:
   ```powershell
   $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
   ```
3. **List Connected Devices**:
   ```powershell
   & $adb devices
   ```
4. **Launch Main Activity directly on Target Device**:
   ```powershell
   & $adb -s <DEVICE_SERIAL> shell am start -n com.example.nvhspectro/com.example.nvhspectro.MainActivity
   ```

## Git & Release Best Practices
- **Branch**: `master` / `main`.
- **Pre-built APK**: `app/build/outputs/apk/debug/app-debug.apk` is force-tracked in git (`git add -f ...`) for quick manual testing without building from source.
