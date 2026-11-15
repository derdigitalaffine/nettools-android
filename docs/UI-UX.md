# NetTools Android · UI/UX-Standard

Stand: 15.11.2026 · Schwerpunkt der Version **0.10.0-alpha**.

## Leitbild

Professionelle Netzwerk-Workbench statt einer langen Sammlung unverbundener Formulare. Zentrale Qualitätsziele sind **schnelle Orientierung, ruhige Hierarchie, konsistente Bedienung, eindeutige Zustände, ehrliche Funktionskennzeichnung und zugängliche Messwerte**.

## Implementierte Gestaltung

### Navigation und responsive Ansichten

- Kompakt: Bottom Navigation für Start, Diagnose, Funk, Sensoren, Hardware.
- Mittel (ab ca. 740 dp Fensterbreite): Navigation Rail.
- Weit (ab ca. 1160 dp): permanente Seitenleiste mit ausgeschriebenen Zielen.
- Diagnosezentrum: Katalog mit **12 Tools**, Live-Suche nach Name/Beschreibung/Fähigkeit, Kategorie-Chips und Favoriten.
- Diagnosezentrum ab ca. 760 dp verfügbarer Inhaltsbreite: **Liste und Werkzeugdetail nebeneinander**. Darunter: eigenständige Detailseite mit Zurück-Pfeil und Android-System-Zurück.
- Pro Werkzeug wird nur **eine** interaktive Arbeitsfläche angezeigt. Keine zwölf parallel aufgebauten Werkzeuge auf einer Scrollseite.
- Favoriten und Designauswahl bleiben nach App-Neustart erhalten. Saveable Formulareingaben bleiben beim internen Navigieren erhalten; SNMP-Credentials werden **nicht** persistiert.

### Designsystem

Implementiert in `NetToolsTheme.kt`: Material 3, helle und dunkle Palette, semantische Container, 12/16/22-dp-Rundungen, harmonisierte Textgrößen, typografische Hierarchie. Keine externen Schriftdateien.

- Dark: Hintergrund `#09141E`, Surface `#101E29`, Primär `#68DED6`, Haupttext `#F0F6F9`.
- Light: Hintergrund `#F4F7F9`, Surface `#FFFFFF`, Primär `#006F72`, Haupttext `#172B36`.
- Kategorien und Status werden **nicht allein über Farbe** kommuniziert; Messmethode und Funktionsgrenzen bleiben als Text erkennbar.
- Reusable: Header, Panel, Metric, Notice, Empty State, Tool-Row, Diagnose-Split-Pane.

### Die wichtigsten Screens

- **Dashboard:** Verbindungs-Hero, Netzwerk- und DNS-Kennzahlen, Schnellaktionen, kopierbare IP/DNS-Daten und explizit einblendbare Routen.
- **Diagnose:** indexierte Werkzeuge, Suche, Favoriten, Fähigkeits-/Methodenhinweise und Back-Navigation.
- **BLE:** aktiver Scanstatus, klarer Start/Stopp, RSSI-Filter, Geräte-Karten und inkrementelles Nachladen.
- **GNSS/Sensoren:** große primäre Positionstaste, Karten-Shortcut, definierte Leerzustände und Sensor-Karten.
- **USB:** Geräteanzahl, manueller Refresh, Vendor-/Product-ID und Schnittstellen pro Gerät.
- **Karte:** schwebende Kartensteuerung, basemap.de-Stilauswahl, Ladezustand, Startausschnitt-Reset und sichtbare Quellenangabe.
- **Scanner:** begrenzte Ergebnisse pro sichtbarer Seite, Status/Messmethode, progressive Offenlegung der optionalen Dienstindizien.
- **Formulare:** passende Android-Tastaturen für Port- und Timeout-Zahlen, OID, Adressen und verdeckte Credentials.

## Sicherheits- und Messmethoden-UX

- Kein Root-/Raw-L2-Versprechen ohne nachgewiesene Device-Capability.
- TCP-Connect wird niemals als ICMP ausgewiesen.
- Serviceport-Namen sind **Hinweise**, keine bestätigten Dienstversionen.
- SNMPv2c und SNMPv3 werden als unverschlüsselte bzw. AuthPriv-Varianten gekennzeichnet.
- Nur explizit ausgelöste Scans und Standortmessungen; Abbruchfunktionen sichtbar. Keine automatischen SNMP-SETs.
- Aufruf von Kartenkacheln über basemap.de ist sichtbar deklariert.
- Zugangsdaten nicht im Einstellungsstore oder Scan-Export persistieren.

## Noch ausstehende Qualitätstore

1. Screenshot-/Instrumentierungstests auf 320/360/393/600/800/1200 dp und verschiedenen Formfaktoren; prüfen, ob Felder bei IME-Öffnung erreichbar bleiben.
2. TalkBack-Fokusreihenfolge, 48-dp-Interaktionsflächen, dynamische Schrift 100/150/200 % und Keyboard-Navigation testen.
3. Touch- und Navigationsregression bei laufenden Messungen und wiederholtem Wechsel zwischen Werkzeugen.
4. Test mit 100+ BLE-Geräten, 200+ Sensoren, sehr langen Hostnamen und Fehlermeldungen.
5. Theme-Umschaltung, Rotation und Prozesswiederherstellung prüfen; keine Speicherung sensibler SNMP-Credentials.
6. USB/MapLibre/BLE/GNSS auf physischer Hardware, offline und mit gesperrten Berechtigungen prüfen.

**Status:** Die Kotlin-/Compose-Implementierung kann per CI kompilieren. Ein erfolgreicher CI-Lauf ist **kein** Ersatz für Accessibility-, Screenshot- und reale Endgeräteprüfungen.

## Weiterführende UI-Epics

- Einheitliches, Modul-übergreifendes Sitzungsarchiv mit datensparsamer Export-/Löschfunktion.
- Per-Tool Live-Graphen mit Daten-/Zeitachsen, Zoom, Tabellenalternative und p50/p95.
- BLE-GATT-Detailansicht mit ausdrücklich bestätigter Verbindung.
- Sensordashboards, GNSS-Skyplot, USB-Serial-Konsole.
- Einstellungspanel für Kontrast, Schriftgrößen und Quellnetzauswahl.
