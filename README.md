# NetTools Android

Native Netzwerktoolbox mit Kotlin, Jetpack Compose, Android Bluetooth/BLE, GPS/Sensoren und MapLibre mit amtlicher basemap.de-Karte.

## Build
```sh
gradle :app:assembleDebug
```
Benötigt JDK 17, Android SDK 35 und Gradle 8.13. GitHub Actions baut eine installierbare Debug-APK als Workflow-Artefakt. Kein Release signiert; Debug-APK ist kein Qualitätsnachweis für alle Hardwarefunktionen.

## Implementierter Funktionsstand (0.8.0-alpha)
- Adaptiver Compose-Workspace mit dunklem Design, Dashboard und Bottom Navigation (Telefon) / Navigation Rail (Tablet).
- Netzwerkstatus mit DNS-Servern, aktiver Schnittstelle, IP-Adressen und Routen.
- DNS-Hostnameauflösung mit dem Android-Systemresolver und TCP-Connect-Zeit mit Messverlauf. **Kein ICMP-Ping oder vollständiger DNS-RR-Browser.**
- IPv4-CIDR-Rechner mit Netzmaske, Broadcast und Hostbereich sowie Unit-Tests.
- Wiederholtes TCP-Connect-Monitoring mit Latenz-Zeitreihe, Ausfallquote, Mittelwert, Schwankung und CSV-Export (nur bei geöffneter Ansicht).
- TLS-Zertifikatsprüfung mit echter Hostnamenvalidierung, Protokoll/Cipher, Aussteller, Gültigkeit und SHA-256-Fingerprint.
- DNS-Record-Explorer mit direktem UDP-DNS, TCP-Fallback bei TC-Flag, auswählbarem Resolver und A/AAAA/MX/TXT/NS/CNAME/SOA/SRV/PTR/CAA-Records. Kein DoH, kein DNSSEC-Validierungsclient.
- Gezielte TCP-Portprüfung mit Portlisten und Bereichen (max. 128 Ports pro Lauf), Timeout, Live-Status und Filter für offene Ports.
- ICMP-Echo/Ping mit RTT und Verlustquote sowie TTL-basierte Traceroute-Hop-Analyse. Beide nutzen das Android-Systemprogramm `ping`; abhängig von Firmware, Router-Antwortverhalten und Berechtigungen. Keine Root-Pflicht zugesichert.
- BLE-Discovery mit Android-Berechtigung, RSSI und Service-UUID-Anzeige; noch kein GATT-Explorer.
- Android-Sensor-Livewerte, GPS-Standortabfrage, USB-Geräteliste.
- MapLibre-Kartenarbeitsplatz mit basemap.de-Farb-/Graustil und Zoomkontrollen.

[UI-/UX-Standard](docs/UI-UX.md) · [Master-Roadmap](docs/roadmap/MASTER-ROADMAP.md)

## APK
Der GitHub-Actions-Workflow unter **Actions → Android APK** erzeugt bei jedem Push die Debug-APK als `nettools-debug-apk`-Artifact. Die CI prüft Compilation und Unit-Tests. Ein Debug-Build ersetzt **keine** Tests auf realen Android-Geräten, keinen Sicherheitsreview und keine Produktionssignatur. USB-Serial, LLDP, PCAP, grafisches Traceroute, ICMP, SmokePing und ASN/BGP stehen weiter auf der Roadmap.

[Master-Roadmap](docs/roadmap/MASTER-ROADMAP.md)

## Sicherheit und Datenschutz
Standort und Bluetooth nur nach Android-Systemfreigabe; kein dauerhafter Trackingservice. Keine Telemetrie. Bei der Kartenanzeige werden Kartenkacheln von basemap.de abgerufen; Kartennutzung überträgt technische Anfragedaten an diesen Dienst. BLE-Scan ist kein Rohpaketmitschnitt.


### Erweiterte Netzwerkdiagnose (0.8.0-alpha)
- Nmap-orientierter TCP-Connect-Scanner für Android ohne Root: bis 1024 gezielt ausgewählte Ports, 1–32 parallele Verbindungen, Fortschrittsanzeige und Filter. **Dies ist keine Portierung des nativen Nmap-Binaries**; NSE, SYN-/UDP-Scans und Nmap-Service-Fingerprinting sind nicht enthalten.
- SNMPv2c Read-only GET mit ASN.1/BER-Codierung und OID-Auswertung. Community-basierte SNMPv2c-Anfragen sind unverschlüsselt; für vertrauliche Netze ist SNMPv3 mit Authentifizierung/Verschlüsselung vorzuziehen. Kein SNMPv3 oder Walk in dieser Version.

### Diagnoseerweiterungen 0.8.0-alpha

- SNMPv2c-GETNEXT-Walk mit Begrenzung auf 200 Einträge, lexikografischer OID-/Teilbaumprüfung, System-/Interfaces-/IP-/Host-/LLDP-Profilen, Abbruch und CSV-Export. **Keine** automatische MIB-Textauflösung oder SNMPv3-Unterstützung.
- Nmap-kompatibler Offline-Berichtsimport für XML-Dateien (-oX) über Android SAF; gesicherter XML-Parser gegen DTD/XXE, 2-MB- und Host-Limits. **Native Nmap läuft derzeit nicht innerhalb der APK**.
- Non-Root-Portscanner mit Web-/Infrastruktur-/1–1024-Profilen, bekannten Port-Bezeichnern (**nur Hinweise, kein Fingerprinting**) und CSV-Export.
- Bedingungen für eine mögliche native Android-Portierung: [Nmap-Architektur und Release-Gates](docs/architecture/NMAP-NATIVE-ANDROID.md).

### SNMPv3 (0.8.0-alpha)

- Neues SNMPv3-GET-Werkzeug mit USM **AuthPriv**, HMAC-SHA-256 und AES-128.
- Implementiert über **SNMP4J 3.13.2 (Apache-2.0)**. Die App fragt die authoritative Engine-ID ab, authentifiziert den Benutzer und verschlüsselt die Anfrage. Beide Passwörter werden nicht auf dem Gerät gespeichert.
- Keine SNMP-SET-Operationen, keine Passwörter im CSV-Export. Aktuell SNMPv3 **GET**, nicht GETBULK/WALK. Gegen echte SNMPv3-Geräte ist ein Praxis-Interoperabilitätstest erforderlich.
- Die bereits vorhandenen SNMPv2c-GET-/GETNEXT-Werkzeuge bleiben für ältere Geräte separat nutzbar. SNMPv2c ist nicht verschlüsselt.
