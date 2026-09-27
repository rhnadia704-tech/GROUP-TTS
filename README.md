# Group TTS — Moteur de Synthèse Vocale Multi-Voix pour Android

**Group TTS** est une application et un moteur de synthèse vocale (*Text-to-Speech Engine*) modulaire pour Android. Elle s'appuie sur le moteur TTS système tout en permettant de configurer jusqu'à **5 voix indépendantes simultanément** pour générer et écouter des dialogues audio interactifs, des pièces de théâtre, des podcasts et des récits narratifs.

---

## 🌟 Fonctionnalités Clés

### 1. Moteur TTS Sélectionnable dans les Paramètres Android
- Implémente l'interface officielle `android.speech.tts.TextToSpeechService`.
- **Directement sélectionnable comme moteur TTS par défaut** dans les Paramètres Android (*Paramètres > Système > Accessibilité / Synthèse vocale*).
- Les applications tierces et outils d'accessibilité peuvent ainsi utiliser Group TTS et bénéficier du multi-voix.

### 2. Configuration Studio de 5 Voix Personnalisables
- **Renommage libre** : Définissez le nom de vos personnages (ex: *Alice*, *Bob*, *Narrateur*, *Clara*, *David*).
- **Sélection des voix système** : Associez chaque slot à n'importe quelle voix TTS installée sur votre appareil Android (voix masculines, féminines, accents régionaux).
- **Ajustement fin** : Contrôlez la **tonalité (Pitch)** de 0.5x à 2.0x et la **vitesse d'élocution** de 0.5x à 2.0x.
- **Bouton Test instantané** : Écoutez immédiatement le rendu de chaque voix configurée.
- **Codes couleurs & avatars distinctifs** pour repérer visuellement les répliques dans le script.

### 3. Éditeur de Script & Import de PDF
- **Éditeur de dialogue intelligent** :
  - Détection automatique des balises locuteurs : `Alice: Bonjour`, `[Bob] Salut`, `Narrateur - Il était une fois...`.
  - Puces d'insertion rapide en un tap pour ajouter les tags de vos voix.
  - Exemples de scripts pré-intégrés (Débat Tech, Conte d'aventure, Interview).
- **Importation de PDF & Texte** :
  - Importez des fichiers `.pdf` ou `.txt` via le sélecteur de documents Android (SAF).
  - Extraction de texte intégrée pour convertir un document en script prêt à être lu.

### 4. Lecteur Interactif Intégré
- Défilement automatique et surbrillance en temps réel de la réplique en cours de lecture.
- Mode **Aperçu Dialogue** (bulles de chat interactives par personnage).
- Contrôles complets : Lecture, Pause, Reprise, Arrêt, Réplique suivante / précédente, et lecture d'une réplique individuelle.

### 5. Optimisation des Dialogues par Intelligence Artificielle (Gemini)
- Option d'optimisation IA intégrée (modèle **Gemini 3.5 Flash**) accessible dans le Studio et les Paramètres.
- Analyse le texte brut et le réécrit sous forme de dialogue vivant et équilibré entre vos 5 personnages.
- Ajoute les respirations et la ponctuation expressive (virgules de pause, points de suspension) adaptées aux moteurs TTS.
- Mode de repli heuristique local automatique si aucune clé API n'est configurée.

### 6. Export Audio Haute Fidélité (.WAV) & Bibliothèque
- Synthèse et assemblage des répliques dans un fichier **WAV 16-bit PCM sans compression**.
- **Gestionnaire d'intervalle de silence** : Réglez la pause entre les prises de parole (100 ms à 1200 ms).
- **Bibliothèque intégrée** : Écoutez directement vos enregistrements dans l'application avec le lecteur audio intégré.
- **Partage Android FileProvider** : Partagez instantanément vos fichiers audio vers WhatsApp, Telegram, Google Drive, Mail, etc.

---

## 🏗️ Architecture du Projet

Le projet suit les principes de **Clean Architecture** et **MVVM (Model-View-ViewModel)** avec **Jetpack Compose** :

```
app/src/main/java/com/example/
├── MainActivity.kt                  # Point d'entrée avec Navigation Bar M3 et Scaffold
├── data/
│   ├── ai/
│   │   └── GeminiDialogueOptimizer.kt # Client REST Gemini 3.5 Flash (Retrofit + Moshi)
│   ├── model/
│   │   ├── VoiceSlot.kt             # Modèle pour les 5 slots vocaux
│   │   ├── DialogueLine.kt          # Modèle d'une réplique de script
│   │   └── ExportedAudio.kt         # Modèle des fichiers audio générés
│   └── repository/
│       ├── VoiceRepository.kt       # Persistance SharedPreferences / JSON
│       ├── ScriptParser.kt          # Analyseur syntaxique multi-formats de dialogues
│       ├── PdfExtractor.kt          # Extraction de texte depuis des fichiers PDF/TXT
│       └── AudioExportManager.kt    # Synthèse et assemblage des flux audio PCM WAV
├── tts/
│   ├── GroupTextToSpeechService.kt  # Service Android TextToSpeechService officiel
│   └── SpeechPlayerManager.kt       # Gestionnaire de lecture TTS et suivi des utterances
└── ui/
    ├── MainViewModel.kt             # Gestionnaire d'état de l'application (Flow / StateFlow)
    ├── components/
    │   ├── AudioPlayerBar.kt        # Barre de lecture audio ancrée avec progression
    │   └── VoiceComponents.kt       # Puces de locuteurs et bulles de dialogues
    ├── screens/
    │   ├── StudioScreen.kt          # Éditeur de script, import PDF, lecteur et export
    │   ├── VoicesScreen.kt          # Réglages des 5 voix (nom, pitch, vitesse, moteur)
    │   ├── LibraryScreen.kt         # Bibliothèque des fichiers audio enregistrés
    │   └── SettingsScreen.kt        # Raccourci paramètres TTS Android et réglages IA
    └── theme/
        ├── Color.kt                 # Palette Material 3 et couleurs des 5 voix
        ├── Theme.kt                 # Support mode clair, sombre et Dynamic Color
        └── Type.kt                  # Typographie M3
```

---

## ⚙️ Configuration & Compilation

### Prérequis
- **JDK 17** ou supérieur.
- **Android SDK** avec API de compilation 36 (minSdk 24).
- Gradle (géré automatiquement via le wrapper).

### Compilation Locale
1. Cloner le dépôt :
   ```bash
   git clone <URL_DU_DEPOT>
   cd <NOM_DU_DOSSIER>
   ```

2. Configurer le fichier d'environnement :
   ```bash
   cp .env.example .env
   ```
   *(Optionnel)* Ajoutez votre clé Gemini dans `.env` :
   ```properties
   GEMINI_API_KEY=votre_cle_api_ici
   ```

3. Lancer les tests unitaires et Robolectric :
   ```bash
   gradle testDebugUnitTest
   ```

4. Compiler l'APK de débogage :
   ```bash
   gradle assembleDebug
   ```
   L'APK généré sera disponible dans `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🚀 Intégration Continue (GitHub Actions)

Un workflow d'automatisation complet est inclus dans `.github/workflows/android.yml`.

À chaque `push` ou `pull request` sur les branches `main` ou `master` :
1. Le workflow configure l'environnement Ubuntu avec **Java 17 Temurin**.
2. Il prépare automatiquement les configurations de secrets `.env`.
3. Il exécute les tests unitaires et de compatibilité.
4. Il compile l'APK Android (`app-debug.apk`).
5. Il publie l'APK sous forme d'artefact téléchargeable (**GroupTTS-Debug-APK**).

---

## 📱 Utilisation comme Moteur TTS Système

Pour utiliser Group TTS dans tout le système Android :
1. Installez l'APK sur votre appareil.
2. Ouvrez l'application et rendez-vous dans l'onglet **Paramètres**.
3. Appuyez sur **« Ouvrir les Paramètres TTS du Système »** (ou allez dans *Paramètres Android > Accessibilité > Sortie de synthèse vocale*).
4. Cochez **Group TTS (Moteur Multi-Voix)** comme moteur préféré.

---

## 📄 Licence
Ce projet est distribué sous licence open-source. Libre d'utilisation et de modification.
