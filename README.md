# easyBOLUS

Eine schlanke Android-App (Jetpack Compose) zur schnellen Bolus-Berechnung, Dokumentation von Kohlenhydraten und Insulin sowie zur Live-Anzeige von CGM-Glukosewerten aus **Juggluco**.

> **Wichtiger Hinweis:** easyBOLUS ist ein privates Hilfs- und Dokumentationstool und **kein zertifiziertes Medizinprodukt**. Überprüfe alle berechneten Bolusvorschläge eigenverantwortlich und triff Therapieentscheidungen nicht allein auf Basis dieser App.

---

## Funktionen

- **Live-Glukosekurve & Dual-Axis-Chart**
  - Anzeige von Glukosewerten (`mg/dl`) inkl. Zielbereich-Band sowie IOB (*Insulin on Board*) und COB (*Carbs on Board*).
  - **Automatische Y-Achsen-Skalierung**, **Zwei-Finger-Zoom (Pinch-to-Zoom)** und **Verschieben (Pan)** auf der Zeitachse.
  - **Interaktives Crosshair:** Tippen auf das Chart zeigt den genauen Wert und Zeitpunkt; über den Chip **„Jetzt (6h)“** springst du sofort zurück zur aktuellen Ansicht.
  - Warnhinweis, falls der letzte Messwert 15 Minuten oder älter ist.
- **Bolusrechner & Schnelleintrag**
  - Berechnung anhand von Zielwert, Korrekturfaktor (ISF) und **KH-Faktor** (optional **stündlich für 00–23 Uhr** konfigurierbar).
  - Berücksichtigung von aktivem Insulin (IOB) basierend auf der eingestellten Wirkdauer (**DIA**).
  - Rundung passend zur **Pen-Schrittweite** (`0,5 IE` oder `1,0 IE`) inkl. **− / + Stepper-Buttons**.
  - **Kombinierter Schnelleintrag** für KH + IE + Notiz mit **+10 g / +15 g / +30 g**-Schnellchips und wählbarer Uhrzeit (**Heute / Gestern** + Zeitauswahl).
- **Tagebuch & Export**
  - Chronologische Übersicht mit Tagesgruppierung (*Heute*, *Gestern*, Datum) und **Tages-Summen für IE und KH**.
  - Filter-Chips (**Alle**, **Bolus**, **KH**, **Notizen**) sowie **CSV-Export**.
- **Design**
  - Unterstützt **System**, **Hell** und **Dunkel** (Dark Mode).

---

## Juggluco einrichten

easyBOLUS empfängt Glukosewerte lokal auf dem Gerät über Android-Broadcasts. Es ist **keine Internetverbindung** und kein Nightscout-Server nötig.

1. Öffne **Juggluco** auf deinem Smartphone.
2. Gehe in Juggluco ins Menü **Einstellungen** (*Settings*).
3. Aktiviere je nach Juggluco-Version einen der folgenden Broadcasts:
   - **Glucodata broadcast** (`glucodata.Minute`) **oder**
   - **Send to xDrip** (`com.eveningoutpost.dexdrip.BgEstimate`)
4. Falls Juggluco nach Ziel-Apps fragt, kannst du entweder den allgemeinen Broadcast aktiv lassen oder explizit **`de.easybolus`** auswählen.
5. Nach dem nächsten Sensor-Wert (in der Regel innerhalb von 1–5 Minuten) erscheint der aktuelle Glukosewert automatisch auf der Startseite von easyBOLUS.

### Tipps bei Verbindungsproblemen mit Juggluco
- **Akku-Optimierung:** Stelle sicher, dass weder Juggluco noch easyBOLUS durch aggressive Energiespar-Einstellungen im Hintergrund beendet werden.
- **Erster Test ohne Sensor:** In easyBOLUS kannst du unter **Einstellungen → Juggluco & Testdaten** jederzeit **„Demo-Daten laden“**, um Chart, Bolusrechner und Tagebuch sofort auszuprobieren.
- **Status-Anzeige:** Sobald Daten aus Juggluco empfangen wurden, verschwindet der Einrichtungshinweis automatisch und in den Einstellungen erscheint der grüne Status *„Juggluco-Broadcast aktiv“*.

---

## Installation & Updates mit Obtainium

Die fertige APK wird automatisch bei jedem Push auf `main` über GitHub Actions gebaut und als GitHub Release bereitgestellt.

1. Installiere [Obtainium](https://github.com/ImranR98/Obtainium) auf deinem Android-Gerät.
2. Tippe auf **App hinzufügen** und füge die Repository-URL ein:
   ```text
   https://github.com/EnnoGrensemann/easyBolus-Android
   ```
3. Obtainium erkennt automatisch die Releases aus diesem Repository und installiert Updates nahtlos (alle Release-APKs werden mit demselben Release-Keystore signiert).

---

## Selbst bauen (Android Studio)

- **Voraussetzungen:** Android Studio (aktuell), JDK 17+, Android SDK 35
- **Mindestanforderung:** Android 8.0 (`minSdk = 26`), `targetSdk = 35`
- **Paketname:** `de.easybolus`

```bash
git clone https://github.com/EnnoGrensemann/easyBolus-Android.git
```

Anschließend das Projekt einfach in Android Studio öffnen, Gradle-Sync abwarten und über **Run** auf dem Gerät oder Emulator starten.
