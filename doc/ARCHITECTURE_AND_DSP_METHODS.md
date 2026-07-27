# NVH Spectro - Architecture & Méthodes de Calcul DSP / NVH

**Auteur** : Louis BARTHELEMY  
**Société** : VIBRATEAM [Vibratec (Everenn Group)]  
**Application** : NVH Spectro  
**Version** : v7.0.0 (Build 2026)  
**Contact** : www.louis.barthelemy@vibrateam.fr  

---

## 1. Vue d'Ensemble & Architecture Logicielle

`NVH Spectro` est une application Android professionnelle conçue pour les ingénieurs et techniciens NVH (*Noise, Vibration, and Harshness*) dans le secteur automobile et industriel. Elle permet l'acquisition acoustique temps réel par microphone et la synchronisation milliseconde par milliseconde avec la télémétrie véhicule GPS (Vitesse, Accélération, Altitude).

### 🏛️ Architecture MVVM (Model-View-ViewModel)

```
       ┌────────────────────────────────────────────────────────┐
       │                 Jetpack Compose UI                     │
       │  (MainScreen, SpectrogramCanvas, TelemetryGraph, etc.) │
       └───────────────────────────▲────────────────────────────┘
                                   │ StateFlow
       ┌───────────────────────────┴────────────────────────────┐
       │                     MainViewModel                      │
       │       (Gestion d'état, synchronisation 1-to-1)          │
       └───────────────▲────────────────────────▲───────────────┘
                       │ Flow                   │ Flow
       ┌───────────────┴───────────────┐ ┌──────┴───────────────┐
       │        AudioRepository        │ │ TelemetryRepository  │
       │   (AudioRecord + FFT 50% OL)  │ │ (GPS LocationManager)│
       └───────────────▲───────────────┘ └──────────────────────┘
                       │
               ┌───────┴───────┐
               │  FFTProcessor │
               │ (DSP & TTNR)  │
               └───────────────┘
```

1. **Layer UI (Jetpack Compose & Custom Canvas 2D)** :
   - `SpectrogramCanvas.kt` : Canvas 2D haute performance affichant le spectrogramme déroulant (Waterfall) avec balises clignotantes pulsantes d'émergence tonale (v7.0.0).
   - `TelemetryGraph.kt` : Graphique 2D déroulant synchronisé (Vitesse, Accélération, Altitude) et Mode Spectre 2D TTNR (Émergence vs Fréquence).
   - `SettingsDialog.kt` : Dialogue de configuration avec sélecteur de taille N, tableau récapitulatif DSP et curseurs du Détecteur d'Émergence.
   - `InfoDialog.kt` : Fiche auteur (Louis BARTHELEMY), société VIBRATEAM Vibratec et détails métier.
   - `ExportDialog.kt` : Générateur de rapport PNG complet.

2. **Layer Core & DSP** :
   - `AudioRepository.kt` : Continuous 16-bit PCM Audio Capture à 44.1 kHz avec buffer glissant et recouvrement fixe à 50% (Constant Overlap-Add Hanning).
   - `FFTProcessor.kt` : Calcul FFT via `JTransforms` et algorithme d'émergence tonale ECMA-74 / ISO 1996-2 optimisé NVH Véhicule v7.0.0.
   - `TelemetryRepository.kt` : Détection GPS haute fréquence et calcul d'accélération différentielle ($g$).

---

## 2. Protection du Code & Nommage d'APK (V7.0.0)

Dans la version **V7.0.0**, les mécanismes de protection de propriété intellectuelle et de nommage personnalisé sont actifs :

- 🔐 **Offusquation R8 / ProGuard** : Activation de `isMinifyEnabled = true` et `isShrinkResources = true` sur le build de Release.
- 📦 **Nommage personnalisé d'APK** : Génération directe sous le nom explicite `APP_NVH_Spectro_v7.apk` (ou `app-debug.apk` en mode développement direct).

---

## 3. Méthodes de Calcul DSP & Traitement du Signal (Spécificités NVH V7.0.0)

### 3.1. Acquisition & Fenêtrage Hanning Compensé
- **Fréquence d'échantillonnage** : $F_s = 44\,100\text{ Hz}$.
- **Taille de fenêtre FFT** : $N \in \{512, 1024, 2048, 4096\}$ points (par défaut $N = 2048$).
- **Recouvrement (Overlap)** : Fixe à **50%** ($N/2$ échantillons), garantissant la propriété **COLA** (*Constant Overlap-Add*) :
  $$w(n) + w(n - N/2) = 1.0$$
- **Fenêtre de Hanning** :
  $$w(n) = 0.5 \cdot \left(1 - \cos\left(\frac{2\pi n}{N-1}\right)\right)$$
- **Normalisation en Amplitude (dBFS)** :
  $$\text{Magnitude}(i) = 20 \cdot \log_{10}\left(\frac{|X(i)|}{N / 4}\right)$$

---

### 3.2. Calcul du TTNR (Tone-to-Noise Ratio - ISO 1996-2 / ECMA-74 NVH Adaptatif v7)

Le TTNR mesure l'émergence d'une raie tonale émergente (ordres moteur combustion H1.5, sifflement d'engrenage réducteur VE, turbo, hachage inverter) par rapport au niveau du bruit de masque ambiant.

#### Step 1 : Masquage Local Adaptatif NVH & Bande Critique
Pour chaque raie fréquentielle $f = i \cdot \Delta f$ (avec $\Delta f = F_s / N$) :
- Bande critique de Terhardt :
  $$\Delta f_c(f) = 25.0 + 75.0 \cdot \left(1 + 1.4 \cdot \left(\frac{f}{1000}\right)^2\right)^{0.69} \quad [\text{Hz}]$$
- **Fenêtre de Masquage Local Bornée à $400\text{ Hz}$** : En milieu véhicule non-plat (bruit aérodynamique broadband HF), l'estimation de la densité de bruit locale est bornée à $\min(\Delta f_c, 400\text{ Hz})$ pour éviter d'intégrer le bruit de vent hors-bande qui étouffe le TTNR à $4\text{ kHz}+$.

#### Step 2 : Puissance du ton vs Puissance du bruit de masque
- **Puissance de la raie tonale ($P_{\text{tone}}$)** : Somme du pic $i$ et de ses 4 raies adjacentes de leakage ($i-2 \dots i+2$).
- **Puissance du bruit de masque ($P_{\text{noise}}$)** : Densité spectrale moyenne mesurée sur la fenêtre de masquage locale, en excluant la traînée du ton ($|j - i| > 3$).

#### Step 3 : Émergence brute
$$\text{TTNR}_{\text{raw}}(i) = 10 \cdot \log_{10}\left(\frac{P_{\text{tone}}}{P_{\text{noise\_total}}}\right) \quad [\text{dB}]$$

### 3.3. Triade de Filtrage NVH Dynamique v7.0.0

1. **Porte d'Amplitude Absolue Haute Sensibilité (-85 dBFS)** :
   Autorise les ordres de combustion basse fréquence ($f \ge 15\text{ Hz}$, ex: H1.5 à $37.5\text{ Hz}$) et les sifflements HF à faible niveau absolu mais forte émergence.

2. **Lissage Spectral Preservateur d'Émergence (90%)** :
   $$\text{TTNR}_{\text{smooth}}(i) = 0.05 \cdot \text{TTNR}_{\text{raw}}(i-1) + 0.90 \cdot \text{TTNR}_{\text{raw}}(i) + 0.05 \cdot \text{TTNR}_{\text{raw}}(i+1)$$
   Conserve la hauteur exacte des pics émergents sans les raboter.

3. **Persistence Temporelle EMA Réactive ($\alpha = 0.75$)** :
   $$\text{TTNR}_{\text{final}}(i, t) = 0.75 \cdot \text{TTNR}_{\text{smooth}}(i, t) + 0.25 \cdot \text{TTNR}_{\text{final}}(i, t-1)$$
   Garantit un suivi instantané lors des rampes d'accélération et variations de régime véhicule (*orders tracking*).

---

## 4. Matrice des Indicateurs DSP (Réglages)

| Taille $N$ | Recouvrement | Pas Temporel ($\Delta t$) | Bloc Temporel ($1/\Delta f$) | Cadence (FPS) | Résolution ($\Delta f$) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **512 pts** | 50 % | 5.8 ms | 11.6 ms | 172.3 trames/s | 86.1 Hz |
| **1024 pts** | 50 % | 11.6 ms | 23.2 ms | 86.1 trames/s | 43.1 Hz |
| **2048 pts** | 50 % | 23.2 ms | 46.4 ms | 43.1 trames/s | 21.5 Hz |
| **4096 pts** | 50 % | 46.4 ms | 92.9 ms | 21.5 trames/s | 10.8 Hz |

---

## 5. Fonctionnalités Complètes de l'Application

- 🎨 **Spectrogramme Bimodal** : Bascule 1-clic entre Niveau Absolu (dBFS) et Émergence Tonale TTNR (dB).
- 📈 **Graphiques 2D Synchronisés 1-to-1** : Vitesse ($km/h$), Accélération ($g$), Altitude ($m$), et Spectre 2D TTNR ($dB$ vs $Hz$).
- ℹ️ **Fiche Auteur & Entreprise** : Présentation complète VIBRATEAM Vibratec (Everenn Group), auteur Louis BARTHELEMY.
- 💾 **Exportation Rapport HD PNG** : Génération de rapport complet incluant cartouche métadonnées, logo Vibratec, spectrogramme et courbes épilées.
- 🧊 **Mode Figer / Dégeler** : Analyse à l'arrêt sur une trame temporelle précise.

---

## 6. Procédure de Maintenance de la Documentation

> [!IMPORTANT]
> **Règle d'Agent / Procédure de Commit :**
> Ce fichier `doc/ARCHITECTURE_AND_DSP_METHODS.md` **DOIT être relu et mis à jour systématiquement avant tout nouveau commit Git** apportant une modification d'architecture, d'algorithme DSP ou de fonctionnalité.
