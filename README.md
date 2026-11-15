# NetTools Android

Native Netzwerk-Workbench für Android mit **Kotlin, Jetpack Compose Material 3, Bluetooth LE, GNSS/Sensoren, USB und MapLibre/basemap.de**. Offline-first, ohne Benutzerkonto und ohne versteckte Telemetrie.

## Oberfläche · 0.10.0-alpha

Die App besitzt ein gemeinsames Designsystem mit hohem Kontrast und hellem/dunklem Modus:

- **Smartphone:** kompakte Hauptnavigation unten, Werkzeugkatalog und fokussierte Einzelansicht. Android-Zurück führt aus einem Werkzeug zurück in den Katalog.
- **Tablet:** Navigation Rail und adaptives Liste-Detail-Layout für Diagnosewerkzeuge.
- **Große Displays:** permanente Seitennavigation mit ausgeschriebenen Zielen.
- **12 Diagnosewerkzeuge:** Suche, Themenfilter, Favoriten und Kennzeichnung von Messverfahren bzw. Einschränkungen.
- **Dashboard:** aktiver Netzwerktransport, IP-/DNS-/Routenkennzahlen, Schnellaktionen und aufklappbare Verbindungsdetails.
- **Funk, Sensoren, USB und Karte:** harmonisierte Karten, Leerzustände, klare Aktionen, Signalfilter bei BLE, bessere kleine Displays.
- Favoriten und Designwahl werden lokal gespeichert; Eingaben, soweit unkritisch, beim Werkzeugwechsel erhalten. **SNMP-Passwörter und Communities werden nicht dauerhaft gespeichert.**
- Mobile Zahleneingabe für Port-, Timeout-, OID- und Zählerfelder.

### Funktionsstand

| Bereich | Implementiert |
| --- | --- |
| Netzwerk | Standardverbindung, Interface, IP-Adressen, DNS-Resolver und Routen |
| DNS | Systemresolver; A/AAAA/MX/TXT/NS/CNAME/SOA/SRV/PTR/CAA über UDP/53, TCP-Fallback |
| Messungen | TCP-Connect, Live-Zeitreihe und CSV; ICMP-Echo und TTL-Hop-Analyse via Android-`ping` |
| Analyse | IPv4-Subnetzrechner, TLS-Zertifikatsprüfung mit Hostnamenvalidierung |
| Portscanner | Non-Root-TCP-Connect-Prüfung: max. 1024 ausgewählte Ports und 32 Verbindungen; Scanprofile, CSV, opt-in-Banner/HTTP/TLS-Indizien |
| Nmap | Sicherheitsbegrenzter Import externer Nmap-XML-Berichte (`-oX`) über Android-Dateiauswahl |
| SNMP | SNMPv2c GET und begrenzter GETNEXT-Walk mit CSV; SNMPv3 AuthPriv GET über SNMP4J/SHA-256/AES-128 |
| Bluetooth | BLE-Advertisement-Scan mit RSSI, Service-UUIDs und Filter für stärkere Signale |
| GNSS/Sensoren | Standortabfrage auf Nutzeraktion, Android-Sensormesswerte |
| Karte | MapLibre mit basemap.de-Farb-/Graustil, Zoom und Zurücksetzen |
| Hardware | USB-Host-Geräte mit Vendor-/Product-ID und Interfacezahl |

### Was noch **nicht** enthalten ist

Eine native, in der APK ausgeführte Nmap-Engine (`-sV`, NSE, `-sS`), SNMPv3-Walk und vollständige MIB-Textauflösung, BLE-GATT-Explorer, SSH-Terminal, USB-Seriell-Konsole, Ethernet-Rohmitschnitt, LLDP-Frames, PCAP und ein vollständiger SmokePing-Datenspeicher. Die Portbezeichnungen im Connect-Scanner sind **Hinweise**, keine verifizierte Diensterkennung. Der Android-Ping-Aufruf kann geräteabhängig fehlen bzw. eingeschränkt sein.

[Native-Nmap-Integrationsgates](docs/architecture/NMAP-NATIVE-ANDROID.md) · [Master-Roadmap](docs/roadmap/MASTER-ROADMAP.md) · [UI-/UX-Standard](docs/UI-UX.md)

## Android-Build

Voraussetzungen: JDK 17, Android SDK 35, Gradle 8.13.

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

Der Workflow **GitHub Actions → Android APK** kompiliert und testet bei jedem Push. Das Artefakt `nettools-debug-apk` ist eine installierbare Debug-APK. Es existiert keine produktionssignierte Veröffentlichung. Ein grüner CI-Lauf belegt weder die Kompatibilität mit jeder Android-Firmware noch Hardware-, Netzwerk- und Accessibility-Tests.

## Datenschutz & Netzwerk-Sicherheit

- Scans und Netzwerkmanagement nur für autorisierte Zielsysteme.
- Kein Account, keine versteckte Telemetrie, keine dauerhaften Standortmessungen im Hintergrund.
- Bluetooth und Standort werden über die zuständigen Android-Systemfreigaben gestartet.
- Kartenkacheln über basemap.de; der Anbieter erhält technisch bedingte Anfragedaten beim Kartenaufruf.
- SNMPv2c-Community wird **unverschlüsselt übertragen**; wenn möglich SNMPv3 AuthPriv nutzen. AuthPriv-Zugangsdaten werden nicht gespeichert.
- Ein BLE-Scan ist kein Bluetooth-Sniffer. Normales Android ermöglicht ohne geeignete Zusatzhardware/Privilegien keinen universellen physischen Ethernet-Layer-2-Mitschnitt.

## Qualität und offene Gates

Unit-Tests decken unter anderem DNS-, SNMP-, Subnetz-, Scanner-, Nmap-Import- und Werkzeugfilterlogik ab. Für eine belastbare Produktfreigabe fehlen weiterhin **manuelle Tests auf mehreren realen Smartphones und Tablets, Layout-/Screenshot-Tests, TalkBack sowie 150–200 % Schriftgröße**. Diese Punkte bleiben ausdrücklich offen.
