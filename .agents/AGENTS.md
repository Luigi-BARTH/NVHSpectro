
## Android Deployment
To deploy and launch the Android application directly on a physical phone connected via USB debugging, follow this procedure:
1. Ensure the device is connected with USB Debugging enabled.
2. Build and push the APK: `.\gradlew installDebug`
3. Locate adb and find the device serial:
   `\ = "\C:\Users\Louis\AppData\Local\Android\Sdk\platform-tools\adb.exe"; & \ devices`
4. Launch the main activity on the specific device:
   `& \ -s <DEVICE_SERIAL> shell am start -n com.example.nvhspectro/com.example.nvhspectro.MainActivity`

