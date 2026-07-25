# NVH Spectro 🚗🔊

**NVH Spectro** est une application Android professionnelle conçue pour l'analyse des harmoniques acoustiques et vibratoires en temps réel lors d'essais sur véhicules (thermiques, hybrides et électriques).

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-brightgreen.svg)

---

## 🎯 Fonctionnalités Principales

### 1. Spectrogramme / Colormap Haute Performance
- **Rendu Matériel ultra-fluide** : Utilisation de `Bitmap.setPixels` direct pour un défilement continu sans ralentissement (30-60 FPS).
- **Échelle de Couleurs "Jet"** : Palette thermique standard NVH allant du bleu profond (bruit de fond) au rouge intense (harmoniques fortes).
- **Échelle dB FS (Full Scale)** : Calcul mathématique rigoureux normalisé sur 16-bit avec correction du gain cohérent de la fenêtre de Hanning (`20 * log10(mag / (N/4))`).

### 2. Réglages Experts NVH en Temps Réel
- **Temps d'affichage (Fenêtre Glissante)** : Réglable en direct de **3.0 s à 30.0 s** avec graduations temporelles réelles sur l'Axe X (ex: `-30s` à `0s`).
- **Zoom Fréquentiel (Axe Y)** : Réduction et sélection de la plage de fréquence affichée (ex: `0 - 8000 Hz` ou `0 - 10000 Hz`).
- **Résolution FFT** : Ajustement à la volée de la taille du buffer FFT (`1024`, `2048`, `4096` points).
- **Niveaux de Couleurs Min / Max** : Calibrage dynamique de la plage dynamique dB FS.

### 3. Tableau de Bord Télémétrie Véhicule
- **Vitesse Véhicule** (en km/h via le récepteur GPS du téléphone).
- **Altitude** (en m via GPS).
- **Accélération Longitudinal** (en *g* via l'accéléromètre du téléphone).
- **Statut de Réception GPS**.

### 4. Fonctions d'Analyse et d'Exportation
- **Bouton Figer / Dégeler** : Gel immédiat de l'affichage du spectrogramme pour analyser une trame ou une fréquence résiduelle précise.
- **Export & Annotations** : Fenêtre de saisie pour ajouter les métadonnées de roulage (% d'enfoncement pédale, commentaires de l'essayeur). Génération automatique d'une image PNG complète avec incrustation du spectrogramme et des métadonnées, sauvegardée dans la galerie d'images du téléphone (`Pictures/NVHSpectro`).

---

## 🛠️ Architecture Technique

```
app/src/main/java/com/example/nvhspectro/
├── AudioRepository.kt      # Acquisition audio micro 16-bit (Buffer avec 50% Overlap)
├── FFTProcessor.kt         # Fenêtrage Hanning, calcul JTransforms & normalisation dBFS
├── TelemetryRepository.kt  # Gestionnaires GPS (Location) et Accéléromètre
├── SpectrogramColormap.kt # Moteur de rendu graphique Canvas & Bitmap Jet
├── MainViewModel.kt        # State Management, Coroutines & Moteur d'exportation
├── MainScreen.kt           # Interface utilisateur Jetpack Compose
└── ui/
    ├── SettingsDialog.kt   # Dialog de réglages NVH
    └── ExportDialog.kt     # Dialog d'annotation & d'export
```

---

## 🚀 Compilation & Installation

### Prérequis
- Android Studio avec SDK 34+
- Java JDK 17 (JBR Android Studio)
- Appareil physique Android (Android 8.0+) avec **Débogage USB** activé.

### Déploiement via Ligne de Commande (PowerShell)

1. **Compilation et Installation de l'APK** :
   ```powershell
   $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
   .\gradlew installDebug
   ```

2. **Lancement Automatique de l'Application sur Téléphone Connecté** :
   ```powershell
   $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
   & $adb -s <DEVICE_SERIAL> shell am start -n com.example.nvhspectro/com.example.nvhspectro.MainActivity
   ```

---

## 📄 Licence
Projet développé pour l'analyse des harmoniques acoustiques et vibratoires automobiles.
