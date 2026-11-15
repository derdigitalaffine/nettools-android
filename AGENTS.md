# NetTools Agentenorganisation
Siehe [Roadmap](docs/roadmap/MASTER-ROADMAP.md). Änderungen erfolgen in funktionsbezogenen PRs. Jeder Agent liefert dokumentierte Tests, Android-Berechtigungsprüfung, IPv6- und Fehlerzustände. Ungeprüfte Geräteeigenschaften nicht als unterstützt kennzeichnen.

## Zuständigkeiten
- Orchestrator: Abhängigkeiten, Roadmap, Integration, CI.
- Android: Compose, Lifecycle, Permissions und Gradle.
- Netzwerk: DNS, Ping, Traceroute, Socket und Protokolle.
- Funk: Bluetooth Classic, BLE und WLAN.
- Geo: Android Location/GNSS, SensorManager, MapLibre/basemap.de.
- USB: Host/Serial/Ethernet, Capability-Matrix.
- Security/QA: Sicherheit, Tests, Release-Gates.

## Merge-Gates
Keine Merge-Freigabe bei fehlgeschlagenem Gradle-Compile, gefährlicher Rechteausweitung, heimlichem Scanning oder falschen Layer-2-Claims. Geschützte Daten nie in Logs; Capture nur per sichtbarer Benutzeraktion. Tests auf echten Geräten vor Hardware-Freigabe.
