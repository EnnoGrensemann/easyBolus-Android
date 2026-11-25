# Google Drive Sync – Einmaliges Google-Cloud-OAuth-Setup

easyBolus speichert das Google-Drive-Backup optional als sichtbare Datei **`easybolus_backup.json`** im eigenen Google Drive (`drive.file`-Scope).  
Da es sich um eine Open-Source-/Self-Build-App handelt, muss für den Google-Login und die Drive-API einmalig eine OAuth-Konfiguration in der Google Cloud Console hinterlegt werden.

---

## Schritt-für-Schritt-Anleitung

### 1. Google-Cloud-Projekt anlegen
1. Öffne die [Google Cloud Console](https://console.cloud.google.com/).
2. Erstelle oben über die Projektauswahl ein neues Projekt (z. B. `easyBolus`).

### 2. Google Drive API aktivieren
1. Navigiere zu **APIs & Dienste → Bibliothek**.
2. Suche nach **Google Drive API** und klicke auf **Aktivieren**.

### 3. OAuth-Zustimmungsbildschirm konfigurieren
1. Öffne **APIs & Dienste → OAuth-Zustimmungsbildschirm** (bzw. **Google Auth Platform → Branding / Zielgruppe**).
2. Wähle **Extern** (für persönliche Google-Konten) und trage als App-Namen `easyBolus` ein.
3. Füge unter **Bereiche (Scopes)** folgenden Scope hinzu:
   - `https://www.googleapis.com/auth/drive.file`
4. Solange die App im Status **Testen** ist, füge unter **Testnutzer** deine eigene Gmail-Adresse hinzu.

### 4. Android-OAuth-Client-ID erstellen (für Drive-Berechtigung)
1. Gehe zu **APIs & Dienste → Anmeldedaten → + Anmeldedaten erstellen → OAuth-Client-ID**.
2. Anwendungstyp: **Android**.
3. **Paketname**: `de.easybolus`
4. **SHA-1-Zertifikatfingerabdruck** deines Debug- oder Release-Keystores ermitteln:
   ```bash
   # Debug-Keystore (Standard)
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android

   # Release-Keystore
   keytool -list -v -keystore /pfad/zu/deinem-release.keystore -alias dein-alias
   ```
5. Den SHA-1-Wert kopieren und speichern.

### 5. Web-OAuth-Client-ID erstellen (für Credential Manager Sign-In)
1. Erstelle eine zweite **OAuth-Client-ID** mit Anwendungstyp **Webanwendung** (z. B. Name `easyBolus Web Client`).
2. Es werden keine Redirect-URIs benötigt.
3. Kopiere die generierte **Client-ID** (endet auf `.apps.googleusercontent.com`).

### 6. Web-Client-ID in easyBolus hinterlegen
Trage die Web-Client-ID entweder lokal in `gradle.properties` (bzw. `~/.gradle/gradle.properties`) ein:
```properties
EASYBOLUS_GOOGLE_WEB_CLIENT_ID=1234567890-abcdef.apps.googleusercontent.com
```
oder als Umgebungsvariable / GitHub-Actions-Secret `EASYBOLUS_GOOGLE_WEB_CLIENT_ID` beim Build.

---

## Funktionsweise in der App
- **Lokal-zuerst**: Alle Messwerte, Tagebucheinträge und Einstellungen bleiben primär in der lokalen Room-Datenbank bzw. DataStore.
- **Gedrosselter Auto-Sync**: Bei Änderungen plant `WorkManager` frühestens nach 30 Sekunden (und höchstens alle 5 Minuten) einen Upload im Hintergrund, sobald Internet verfügbar ist.
- **Manuell („Jetzt sichern“ / „Wiederherstellen“)**: Über das Profilbild oben links auf dem Dashboard erreichbar. Beim Wiederherstellen werden bestehende lokale Daten über `BackupMerger` dedupliziert und nur fehlende Messwerte/Einträge ergänzt.
