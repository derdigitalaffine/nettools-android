# NetTools Android – UI/UX-Qualitätsstandard

Stand 15.11.2026. Verbindlich für alle weiteren Module und PRs.

## Leitbild
Professionelle Messplattform statt Sammlung isolierter Mini-Apps. Eine hochwertige Oberfläche muss die Ergebnisse **schnell erfassbar**, Messmethoden **ehrlich kenntlich**, Fehler **verständlich** und lange Messungen **kontrollierbar** machen. Ein Build allein beweist keine gute Usability.

## Struktur und Navigation
- Smartphones: kompakte Bottom Navigation für Übersicht, Diagnose, Funk, Sensoren und Hardware; alle Ziele schnell erreichbar.
- Tablets: Navigation Rail, größere Karten, größere Lesebereiche; keine erzwungenen Smartphone-Screens.
- Wiederverwendbare Komponenten: Workspace Header, Panel, Metric, Notice, Graph, Section; keine modulspezifischen konkurrierenden Farbwelten.
- Werkzeugansicht immer in der Reihenfolge: **Ziel/Quelle → Einstellungen → Start/Stop → Live-Ausgabe → Analyse/History → Export**.
- Keine Aktionsbuttons, die eine nicht implementierte Funktion vortäuschen; Roadmap-Funktionen eindeutig separat.

## Design-Tokens
- Hintergrund #0B1822, Navigation #101F29, Karten #152530, Primärakzent #17A2A4, Haupttext #E9F1F4, Sekundärtext #9BADB9.
- Runde Karten (ca. 22dp), harmonisierte Randabstände 12/14/18/20dp, Material3-Eingabefelder und Buttons.
- Statusfarben sind semantisch, nicht alleinige Information: Test läuft, erfolgreich, fehlgeschlagen, nicht verfügbar, Berechtigung fehlt, abgebrochen.
- Mindest-Touchflächen nach Android-Barrierefreiheitsstandard; Schriftvergrößerung und TalkBack beachten.

## Daten- und Sicherheits-UX
- Bei jeder Messung Ziel, Quellnetz/Interface, Methode und Datum erkennbar. Kein TCP-Connect als ICMP-Ping etikettieren.
- Laufende Scans/Standortaufnahme sichtbar und per Stop-Schaltfläche beendbar.
- Berechtigungen unmittelbar beim konkreten Benutzerwunsch anfordern; nach erfolgreicher Zustimmung diese Aktion fortsetzen, ohne automatischen erneuten Anlauf.
- Fehlende Sensor-/BLE-/USB-Fähigkeiten transparent statt leerer oder erfundener Daten.
- Grafiken immer zusätzlich als zugängliche numerische Daten und mit Einheiten; keine geschätzten Distanzen aus Bluetooth-RSSI als exakte Messwerte.
- Bei basemap.de sichtbare Attribution und technische Information zur Kartenübertragung; Track/Geolokalisierung opt-in.

## Testmatrix vor 1.0
- Breiten 320/360/393/600/800/1200dp, Hoch-/Querformat, gefaltete/entfaltete Geräte.
- 100%, 150% und 200% Schrift, TalkBack, Dark Mode, Tastatur und Hardware-Back.
- Offline, DNS-Timeout, IPv6, fehlendes GPS, kein Bluetooth, gesperrte USB-Berechtigung, rotierende Android-Systemleisten.
- Scan nach Grant/Deny/Don't ask again; Wechsel in Hintergrund; Abziehen von USB-Geräten.
- Messwerte lang, Gerätebezeichnungen mit Unicode, 100+ BLE-Geräte, 200+ Sensoren, leere Listen.
- Screenshot-Verifikation und Instrumentierung auf realem Smartphone/Tablet sind Pflicht vor UX-Freigabe. Aktuell noch ausstehend.

## Nächste UI-Epics
1. Vollständige Designsystem-Extraktion in `core:designsystem`; Compose Preview/Screenshot-Tests für alle Zustände.
2. Tool-Katalog und globale Suche/Favoriten/Verlauf mit Zielsystemprofilen.
3. Grafisches ICMP/Traceroute/SmokePing mit Zoom, Zeitbereich, Hop- und Loss-Ansichten.
4. BLE-GATT-Gerätedetailseite, RSSI-Karten und Live-Subscription-Protokoll.
5. GNSS-Skyplot, Lagegenauigkeitsanzeige, Sensorgraphs und Trackkarte mit MapLibre Layern.
6. USB-Serial-Terminal mit echten Hardware-Permissions, Baud/Parity-Konfiguration, Connect/Disconnect.
7. Persistente Diagnosesessions (Room), Export und Diagnosereport-Workbench.
8. Accessibility/Screenshot/E2E/Hardware-Regression, Tablet-Split-Pane und echtes User-Testing.
