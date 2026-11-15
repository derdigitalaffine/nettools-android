# NetTools Android

Native Netzwerktoolbox mit Kotlin, Jetpack Compose, Android Bluetooth/BLE, GPS/Sensoren und MapLibre mit amtlicher basemap.de-Karte.

## Build
```sh
gradle :app:assembleDebug
```
Benötigt JDK 17, Android SDK 35 und Gradle 8.13. GitHub Actions baut eine installierbare Debug-APK als Workflow-Artefakt. Kein Release signiert; Debug-APK ist kein Qualitätsnachweis für alle Hardwarefunktionen.

## Implementierter Funktionsstand (0.2.0-alpha)
- Adaptiver Compose-Workspace mit dunklem Design, Dashboard und Bottom Navigation (Telefon) / Navigation Rail (Tablet).
- Netzwerkstatus mit DNS-Servern, aktiver Schnittstelle, IP-Adressen und Routen.
- DNS-Hostnameauflösung mit dem Android-Systemresolver und TCP-Connect-Zeit mit Messverlauf. **Kein ICMP-Ping oder vollständiger DNS-RR-Browser.**
- IPv4-CIDR-Rechner mit Netzmaske, Broadcast und Hostbereich sowie Unit-Tests.
- Wiederholtes TCP-Connect-Monitoring mit Latenz-Zeitreihe, Ausfallquote, Mittelwert, Schwankung und CSV-Export (nur bei geöffneter Ansicht).
- TLS-Zertifikatsprüfung mit echter Hostnamenvalidierung, Protokoll/Cipher, Aussteller, Gültigkeit und SHA-256-Fingerprint.
- BLE-Discovery mit Android-Berechtigung, RSSI und Service-UUID-Anzeige; noch kein GATT-Explorer.
- Android-Sensor-Livewerte, GPS-Standortabfrage, USB-Geräteliste.
- MapLibre-Kartenarbeitsplatz mit basemap.de-Farb-/Graustil und Zoomkontrollen.

[UI-/UX-Standard](docs/UI-UX.md) · [Master-Roadmap](docs/roadmap/MASTER-ROADMAP.md)

## APK
Der GitHub-Actions-Workflow unter **Actions → Android APK** erzeugt bei jedem Push die Debug-APK als `nettools-debug-apk`-Artifact. Die CI prüft Compilation und Unit-Tests. Ein Debug-Build ersetzt **keine** Tests auf realen Android-Geräten, keinen Sicherheitsreview und keine Produktionssignatur. USB-Serial, LLDP, PCAP, grafisches Traceroute, ICMP, SmokePing und ASN/BGP stehen weiter auf der Roadmap.

[Master-Roadmap](docs/roadmap/MASTER-ROADMAP.md)

## Sicherheit und Datenschutz
Standort und Bluetooth nur nach Android-Systemfreigabe; kein dauerhafter Trackingservice. Keine Telemetrie. Bei der Kartenanzeige werden Kartenkacheln von basemap.de abgerufen; Kartennutzung überträgt technische Anfragedaten an diesen Dienst. BLE-Scan ist kein Rohpaketmitschnitt.
