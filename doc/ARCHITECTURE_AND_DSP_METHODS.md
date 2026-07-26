# NVH Spectro - Architecture & Méthodes de Calcul DSP / NVH

**Auteur** : Louis BARTHELEMY  
**Société** : VIBRATEAM [Vibratec (Everenn Group)]  
**Application** : NVH Spectro  
**Version** : v4.0.0 (Build 2026)  
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
   - `SpectrogramCanvas.kt` : Canvas 2D haute performance affichant le spectrogramme déroulant (Waterfall) en échelle Absolue (dBFS) ou Émergence Tonale (TTNR dB).
   - `TelemetryGraph.kt` : Graphique 2D déroulant synchronisé (Vitesse, Accélération, Altitude) et Mode Spectre 2D TTNR (Émergence vs Fréquence).
   - `SettingsDialog.kt` : Dialogue de configuration avec tableau récapitulatif des indicateurs DSP en temps réel.
   - `InfoDialog.kt` : Fiche auteur, société VIBRATEAM Vibratec et détails métier.
   - `ExportDialog.kt` : Générateur de rapport PNG complet.

2. **Layer Core & DSP** :
   - `AudioRepository.kt` : Continuous 16-bit PCM Audio Capture à 44.1 kHz avec buffer glissant et recouvrement fixe à 50% (Constant Overlap-Add Hanning).
   - `FFTProcessor.kt` : Calcul FFT via `JTransforms` et algorithme d'émergence tonale ECMA-74 / ISO 1996-2.
   - `TelemetryRepository.kt` : Détection GPS haute fréquence et calcul d'accélération différentielle ($g$).

---

## 2. Méthodes de Calcul DSP & Traitement du Signal

### 2.1. Acquisition & Fenêtrage Hanning Compensé
- **Fréquence d'échantillonnage** : $F_s = 44\,100\text{ Hz}$.
- **Taille de fenêtre FFT** : $N \in \{512, 1024, 2048, 4096\}$ points (par défaut $N = 2048$).
- **Recouvrement (Overlap)** : Fixe à **50%** ($N/2$ échantillons), garantissant la propriété **COLA** (*Constant Overlap-Add*) :
  $$w(n) + w(n - N/2) = 1.0$$
- **Fenêtre de Hanning** :
  $$w(n) = 0.5 \cdot \left(1 - \cos\left(\frac{2\pi n}{N-1}\right)\right)$$
- **Normalisation en Amplitude (dBFS)** :
  $$\text{Magnitude}(i) = 20 \cdot \log_{10}\left(\frac{|X(i)|}{N / 4}\right)$$

---

### 2.2. Calcul du TTNR (Tone-to-Noise Ratio - ECMA-74 / ISO 1996-2)

Le TTNR mesure l'émergence d'une raie tonale émergente (sifflement d'engrenage, moteur électrique, turbo) par rapport au niveau du bruit de masque ambiant dans sa bande critique.

#### Step 1 : Largeur de bande critique (Formule de Terhardt)
Pour chaque raie fréquentielle $f = i \cdot \Delta f$ (avec $\Delta f = F_s / N$) :
$$\Delta f_c(f) = 25.0 + 75.0 \cdot \left(1 + 1.4 \cdot \left(\frac{f}{1000}\right)^2\right)^{0.69} \quad [\text{Hz}]$$

#### Step 2 : Puissance du ton vs Puissance du bruit de masque
- **Puissance de la raie tonale ($P_{\text{tone}}$)** : Somme du pic $i$ et de ses raies adjacentes de leakage ($i-1, i+1$).
- **Puissance du bruit de masque ($P_{\text{noise}}$)** : Moyenne de la densité spectrale de puissance sur la bande critique $\Delta f_c$, en excluant le pic principal et ses raies voisines immédiates ($|j - i| > 2$).

#### Step 3 : Émergence brute
$$\text{TTNR}_{\text{raw}}(i) = 10 \cdot \log_{10}\left(\frac{P_{\text{tone}}}{P_{\text{noise\_total}}}\right) \quad [\text{dB}]$$

---

### 2.3. Triade de Filtrage Psychoacoustique Smart (Anti-Parasite)

Pour éliminer les fluctuations statistiques du bruit de roulement en basse fréquence ($0 - 1200\text{ Hz}$), une triade de filtrage est appliquée :

1. **Porte de Bruit d'Amplitude (-70 dBFS)** :
   Si $\text{Magnitude}(i) < -70.0\text{ dBFS}$, alors $\text{TTNR}(i) = 0.0\text{ dB}$.

2. **Lissage Spectral Gaußien (3 raies)** :
   $$\text{TTNR}_{\text{smooth}}(i) = 0.2 \cdot \text{TTNR}_{\text{raw}}(i-1) + 0.6 \cdot \text{TTNR}_{\text{raw}}(i) + 0.2 \cdot \text{TTNR}_{\text{raw}}(i+1)$$

3. **Persistence Temporelle EMA (Exponential Moving Average, $\alpha = 0.35$)** :
   $$\text{TTNR}_{\text{final}}(i, t) = 0.35 \cdot \text{TTNR}_{\text{smooth}}(i, t) + 0.65 \cdot \text{TTNR}_{\text{final}}(i, t-1)$$
   *Résultat* : Le bruit parasite fluctuant s'annule, tandis que les vraies harmoniques émergent sous forme de courbes lisses avec détection de pic ultra-stable ($\ge 3.0\text{ dB}$).

---

## 3. Matrice des Indicateurs DSP (Réglages)

| Taille $N$ | Recouvrement | Pas Temporel ($\Delta t$) | Bloc Temporel ($1/\Delta f$) | Cadence (FPS) | Résolution ($\Delta f$) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **512 pts** | 50 % | 5.8 ms | 11.6 ms | 172.3 trames/s | 86.1 Hz |
| **1024 pts** | 50 % | 11.6 ms | 23.2 ms | 86.1 trames/s | 43.1 Hz |
| **2048 pts** | 50 % | 23.2 ms | 46.4 ms | 43.1 trames/s | 21.5 Hz |
| **4096 pts** | 50 % | 46.4 ms | 92.9 ms | 21.5 trames/s | 10.8 Hz |

---

## 4. Fonctionnalités Complètes de l'Application

- 🎨 **Spectrogramme Bimodal** : Bascule 1-clic entre Niveau Absolu (dBFS) et Émergence Tonale TTNR (dB).
- 📈 **Graphiques 2D Synchronisés 1-to-1** : Vitesse ($km/h$), Accélération ($g$), Altitude ($m$), et Spectre 2D TTNR ($dB$ vs $Hz$).
- ℹ️ **Fiche Auteur & Entreprise** : Présentation complète VIBRATEAM Vibratec (Everenn Group), auteur Louis BARTHELEMY.
- 💾 **Exportation Rapport HD PNG** : Génération de rapport complet incluant cartouche métadonnées, logo Vibratec, spectrogramme et courbes épilées.
- 🧊 **Mode Figer / Dégeler** : Analyse à l'arrêt sur une trame temporelle précise.

---

## 5. Procédure de Maintenance de la Documentation

> [!IMPORTANT]
> **Règle d'Agent / Procédure de Commit :**
> Ce fichier `doc/ARCHITECTURE_AND_DSP_METHODS.md` **DOIT être relu et mis à jour systématiquement avant tout nouveau commit Git** apportant une modification d'architecture, d'algorithme DSP ou de fonctionnalité.
