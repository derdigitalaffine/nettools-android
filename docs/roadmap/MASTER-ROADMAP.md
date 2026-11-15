# NetTools Android – Master-Roadmap (15.11.2026)

> Status 15.11.2026: Android-Alpha **0.9.0-alpha** mit Debug-APK über GitHub Actions. Erste Diagnosewerkzeuge (TCP, ICMP, DNS, TLS, SNMPv2c/v3, Portscanner, Nmap-XML-Import) sind implementiert. Die folgende Roadmap beschreibt weiterhin **nicht erreichte** Produktziele und keine vollständige Fertigstellung. Ausgangsrepository bei Bestandsaufnahme leer. Ziel: professionelle, native und modular erweiterbare Netzwerktoolbox für Smartphones und Tablets mit Offline-Basisfunktionen, USB-Hardware und optionalem Remote-Capture-Agenten.

## 0. Produktprinzipien

- Kotlin, Jetpack Compose, Material 3, Single-Activity, adaptive Smartphone-/Tablet-Layouts, Dark/Light, dynamische Schrift, Barrierefreiheit.
- Prinzip „offline first“: Netzwerkdiagnosen, DNS-Abfragen, SSH, Traces und USB-Schnittstellen funktionieren ohne Herstellerkonto und ohne verpflichtenden Cloud-Dienst.
- Toolbezogenes Berechtigungsmodell und sichtbare Capability-Anzeige: **lokal möglich**, **USB-Zusatzhardware**, **Remote-Agent nötig**, **Root/privilegiert**, **nicht unterstützt**. Niemals falsche Paketmitschnitt- oder LLDP-Fähigkeiten behaupten.
- Sorgfältige Trennung von „Sicht auf IP-Verkehr des Android-Geräts“ und „Layer-2-Mitschnitt des physischen LANs“.
- Keine geheimen Daten in Logfiles; Verschlüsselung ruhender SSH-Zugangsdaten; konfigurierbare Datenhaltung/Löschung; keine versteckte Telemetrie.
- Alle aktiven Scan-, SNMP- und Sicherheitsfunktionen nur für ausdrücklich autorisierte Ziele; definierte Raten, Zeitlimits und Abbruch.
- Kein ungeprüfter Root-Zwang, keine fragilen Shell-Hacks, keine Play-Store-unvereinbaren Versprechen.

## 1. Technische Architektur

```
app (Compose/Navigation, Responsive UI, Tool Launcher)
 ├─ core:designsystem, core:model, core:network, core:storage
 ├─ core:jobs (coroutines/Flow; foreground services; WorkManager)
 ├─ core:permissions (runtime, USB, VPN, WLAN, notifications)
 ├─ core:diagnostics (structured diagnostics, event/audit trail)
 ├─ feature:overview / feature:profiles / feature:reports
 ├─ feature:ping / feature:traceroute / feature:smokeping
 ├─ feature:dns / feature:whois-rdap / feature:asn-bgp
 ├─ feature:ports / feature:discovery / feature:wifi
 ├─ feature:ssh / feature:terminal / feature:snmp
 ├─ feature:usb / feature:lldp / feature:packets
 └─ feature:monitor / feature:export / feature:settings
```

- Daten: Room (Targets, Messreihen, Alarme, Reports), DataStore (Einstellungen), Keystore/verschlüsselte Schlüsselmaterialien; optionale SQLCipher-Evaluierung.
- Netzwerkzugang über Android `ConnectivityManager`, `Network.getSocketFactory`, `Network.bindSocket`; Quellnetz explizit wählen, nicht auf globales `bindProcessToNetwork` als einziges Mittel setzen.
- Einheitlicher Probe-Vertrag: `ProbeRequest`, `ProbeEvent`, `ProbeResult`, `ProbeCapability`, `ProbeError`, `ProbeCancellation`; Metadaten mit Zeitstempel, Netzwerk, Messmethode, Berechtigungen und Fehlerursache.
- IPv4 und IPv6 von Anfang an, RFC-konforme DNS- und IP-Parser, TLS-Validierung.
- Für lang laufende Benutzer-Messungen nutzerinitiierte, sichtbare Foreground Services mit zutreffenden Android-Service-Typen und sauberem Abbruch; WorkManager nur für geeignete deferrable Aufgaben.

## 2. USB und Paketmitschnitt: verbindliche Machbarkeitsgrenzen

| Option | Möglichkeiten | Einschränkung |
|---|---|---|
| Standard-Android-IP/WLAN/Ethernet | IP-Verbindungen, DNS, TCP-Timing, verfügbare Interface-/Linkdaten | Kein allgemeiner `AF_PACKET`-Rohmitschnitt ohne Privilegien |
| Android VpnService | Auf Android geroutete IP-Pakete eigener/ausgewählter Apps erfassen, PCAPNG-Export nach Implementierung eines korrekten Forwarders | Keine Ethernet-Frames, kein physisches LLDP/CDP; nur eine aktive VPN-App; ausdrückliche Systemzustimmung |
| Android USB Host | Geräte auflisten, USB-Interfaces/Endpoints ansprechen, USB-Serial-Konsole | USB-Freigabe/OTG erforderlich; Standard-USB-Ethernetkernelanbindung liefert keinen allgemeinen L2-Rohzugriff |
| Spezifischer USB-Ethernet-Treiber im Userspace | Nach konkreter Hardware-Spike potenziell Ethernet-Frames und LLDP/CDP direkt lesen | Nur für validierte Chipsätze/USB-Interfaces, hoher Implementierungs- und Hardwareaufwand |
| Externer Capture-Agent (Linux/OpenWrt/Raspberry Pi/Netzwerkprobe) | LLDP/CDP, VLAN, ARP, DHCP, Ethernet-PCAP, Link-Monitoring, Switchport-Analyse | Zusätzlicher Host, Autorisierung, sichere Verbindung und Rechte erforderlich |
| Root/privilegierte Ausgabe | Optional erweiterte Socket-/Capture-Funktionen | Separate Flavor, niemals Voraussetzung für Basis-APK |

### USB-Meilensteine
1. USB Host Device Discovery, Vendor/Product ID, Interfaces, Permissions und Status.
2. USB-Serial: CDC ACM, FTDI, CP210x, CH34x, PL2303 (hardwarevalidierte Auswahl), Baud-/Parity-/Flow-Control, Cisco/Juniper/MikroTik-Terminalprofile, Logs.
3. USB-C-/OTG-Ethernet: Android-systemseitig erkannte Ethernet-Interfaces, Adressen, Routes und Gateway anzeigen; Quellnetz wählen und Diagnosen darauf ausführen.
4. USB-Ethernet-L2-Spike: klarer Hardwarekatalog; eine ausgewählte Chipfamilie prototypisch über USB Bulk Endpoints; Benchmark, Regression und Stabilitätsprüfung; nur danach Produktzusage.
5. Optionales externes USB- oder LAN-Capture-Interface, bevorzugt über bewährte Linux-Capture-Agent-Integration.

## 3. Funktionskatalog nach Priorität

### P0: Foundation / Alpha
- Adaptive Startseite mit Live-Netzprofil: WLAN/Ethernet/Mobilfunk/VPN, IPv4/IPv6, DNS, Gateway, DNS-Suffixe, Signal- und Linkinformationen soweit Android zugänglich.
- Ping: ICMP sofern im Geräte-/API-Kontext verfügbar, dokumentierter TCP-/HTTP-Fallback (niemals als ICMP etikettieren); einstellbare Rate, Timeout, Size, IPv4/IPv6, Statistik Loss/min/avg/max/jitter.
- Grafisches Traceroute: TTL-/Hop-Laufzeit-Ermittlung über verfügbare Mechanismen, IPv4/IPv6, parallele DNS-Rückauflösung, Timeout-Signale, Chart und Tabelle; begrenzte Fallbacks mit gekennzeichneter Messmethode.
- DNS: A, AAAA, CNAME, MX, TXT, NS, SOA, SRV, PTR, CAA, HTTPS/SVCB, DNSKEY, DS, TLSA; UDP, TCP, DoT, DoH; Resolververgleich, TTL, DNSSEC-Status sauber trennen von kryptografisch validierter DNSSEC-Chain.
- IP/Subnet: CIDR Rechner, IPv4/IPv6, Netz-/Broadcast (nur IPv4), RFC1918/ULA, Link-local, IP-/MAC-/OUI-Lookups offline und online.
- RDAP/WHOIS mit Bootstrap, ASN/Prefix/Origin/Registry, RIPEstat (BGP-Historie/Peers/Route Visibility), API-Quellenstatus und Rate-Limits.
- TCP-Portconnect, TLS-Zertifikatsinspektion mit Chain/SAN/Expiry/ALPN, HTTP-Headers/Redirects, einfache Bandbreitenmessung mit Zustimmung.
- Lokale Verlaufsspeicherung und CSV/JSON-Export; grundlegende Reports.

### P1: Professional Diagnostics
- SmokePing-ähnliches Monitoring: Messreihen von mehreren Probes, p50/p95/p99, Min/Max/Loss/Jitter, Heatmap/Verteilungsband, Tages-/Wochen-/Monatsansichten, Unterbrechungsdetektion, SQLite-Retention und Pause/Resume.
- MTR-/PingPlotter-ähnliche Hop-Ansicht, Hop-Statistiken und Pfadwechsel; Warnung vor Fehlinterpretation durch ICMP-Rate-Limits.
- SSH Terminal: Host-Key-Pinning/TOFU, ED25519-/RSA-Key-Import, Passphrase, bekannte Hosts, Jump Hosts als spätere Erweiterung, Session Management, SFTP.
- Netzwerk-Discovery im autorisierten Subnetz, mDNS/DNS-SD, SSDP/UPnP, ARP/Neighbor Cache soweit verfügbar; SNMP v2c/v3 (v3 mit AuthPriv bevorzugen) und MIB-Browser.
- Wi-Fi-Analyzer: SSID/BSSID/Band/Frequenz/Kanal/RSSI/Security, Kanalübersicht, Verlauf. Android Scan-Throttling, Standortberechtigung und Geräteeinschränkungen berücksichtigen.
- Netzdokumentation: Hosts, Tags, Siteprofile, Notizen, QR-Import/Export; Geräte- und Rollen-Profile.

### P2: Layer 2 / Hardware / Capture
- USB-Serial-Console mit Profilen, Session Logs, Makros, Terminal-Escape-Sequenzen und serieller Geräteerkennung.
- LLDP/CDP Browser: Ethernet-/Capture-Agent-Frameparser, Chassis ID, Port ID, System Name/Description, Capabilities, VLAN-Metadaten und TTL. Herkunft, Zeitstempel und Capture-Quelle anzeigen.
- Protokollanalyse: IP/TCP/UDP/ICMP/ARP/DHCP/DNS/TLS-Metadaten, Ethernet/VLAN/LLDP soweit L2-Capture verfügbar. Hex-/Tree-Ansicht, Filter und PCAP/PCAPNG.
- Android VpnService Capture mit Weiterleitungsstrategie, DNS/IPv6-Reachability, Loopvermeidung, Lifecycle-Tests, VPN-Kollisionserkennung und gut sichtbarem Capture-Status. Keine gefährliche Schein-VPN-Implementierung ohne funktionierende Paketweiterleitung.
- Remote-Capture-Agent mit gegenseitiger Authentisierung, TLS, Capabilities-Protokoll, begrenzten Filtern und Datensparsamkeit.

### P3: Expert/Enterprise
- SNMP-Graphing, Interface Counters, LLDP-Topology-Map, Switch-/Routerdiagnose, syslog-Viewer über explizit eingebundene Quellen.
- BGP-Visualisierung, ASN-Beziehungen, Prefix-Ankündigung, RPKI/ROA-Validierungsstatus und Looking-Glass-Integration.
- REST-/HTTP-Client, WebSocket-Test, MQTT-Client, NTP-Analyse, DHCP-Client-Diagnose soweit API erlaubt, Wake-on-LAN, iperf3 in autorisierten Testnetzwerken.
- Optionales Team-Sync/Backend, Android Enterprise/MDM Distribution, Integration ins Monitoring (Prometheus/Grafana) erst nach Datenschutz-/Berechtigungsreview.

## 4. Design- und UX-Konzept

Navigation: **Übersicht · Diagnose · Entdecken · Monitoring · Hardware · Bibliothek**. Werkzeuge über Command Palette und Suche, zuletzt genutzte Ziele, Favoriten, Profile und Schnellaktionen direkt aus dem Dashboard. Jeder Diagnosebildschirm besitzt Ziel/Quellnetz, Start/Stop, Live-Ergebnis, Zeitdiagramm, Details, Export.

- Einfache Ansicht: Ziel eingeben, messen, verständliche Ergebniszusammenfassung.
- Expertenansicht: Probe-Methode, Resolver, IP-Familie, TTL, Intervall, Paketgröße, Filter, Bindings.
- Tablets/Foldables: zweispaltige Workbench, synchronisierte Graph-/Detailsicht; Smartphones: Bottom Navigation, einklappbare Ergebnisse.
- Graphen: Zoom/Pan, Marker, Loss-Punkte, p95-Band, Min/Max, Zeitbereichsauswahl; zugängliche Texttabellen als Alternative.
- Klare States: disconnected, no-permission, device-unsupported, idle, queued, running, cancelled, partial, failed, completed.
- Keine automatisch laufenden Netzwerk-Scans und kein VPN-Autostart ohne erkennbare Entscheidung.

## 5. Agentenorganisation nach TAKT-Prinzipien

`AGENTS.md` im Repo als verbindliches Handbuch, `docs/architecture/`, `docs/adr/`, `docs/roadmap/`, `docs/testing/`; keine Behauptung einer aus TAKT bereits vorhandenen vollständigen Coding-Agent-Orchestrierung.

| Rolle | Verantwortlichkeit | Gates |
|---|---|---|
| Orchestrator | Epic-Zerlegung, Abhängigkeiten, Integrationsplan, Issue/PR-Steuerung | Definition of Ready, CI, Merge-Policy |
| Android Architecture | Modules, APIs, Kotlin, lifecycle, performance | ADR, Architekturchecks |
| Network Protocols | DNS, traceroute, ICMP, TLS, RDAP, ASN/BGP, SNMP | Golden packets, RFC fixtures, IPv6 |
| USB/Hardware | OTG, Serial, Ethernet, LLDP, Hardware-Matrix | Gerätemessungen, Disconnect/Reconnect |
| Packet/Security | VPN Forwarding, Capture, PCAPNG, secrets/privacy | Threat model, Security Review |
| UI/UX | Compose Design System, Charts, a11y, responsive | Screenshot/E2E Tests |
| QA/Release | Emulator/device tests, CI, APK signing, release notes | Release-Gates |

**Ablauf:** Epic → ADR/Capability-Spec → kleine Issues (ein Interface/Feature je PR) → Agent-Implementierung im eigenen Branch → strukturierte Tests → Review durch anderen Fachagenten → Integrations-Agent → Merge nach grünem CI. Kein Agent veröffentlicht APKs oder führt produktive Messungen autonom aus. Versionsnummern, Entscheidungsprotokoll, Abnahmekriterien und Risiken sind maschinenlesbar. Modelle/Provider sind optional und nicht Laufzeit-Voraussetzung der Netzwerktoolbox.

## 6. Meilensteine / Releaseplan

| Phase | Inhalt | Deliverable | Exit Gate |
|---|---|---|---|
| M0 | Repository initialisieren; Kotlin/Compose, Gradle, Detekt/Ktlint, Tests, CodeQL/Dependency Review, AGENTS.md, Architektur-ADRs | Kompilierbare Debug-APK 0.0.x | CI grün, App startet ohne Netz |
| M1 | Netzinformationen, Source-network Auswahl, Ping/TCP, traceroute, DNS | Alpha 0.1 | IPv4/IPv6, Permission/Error UX und Tests |
| M2 | Graphen, SmokePing, History, Charts/Export, RDAP/ASN/BGP | Alpha 0.2 | Multi-hour battery/background soak, API failures |
| M3 | SSH/Serial-Console, USB-Device discovery, USB Ethernet diagnostics | Beta 0.3 | Matrix realer USB-Geräte, Lifecycle |
| M4 | Discovery, WiFi, SNMP, TLS/HTTP, Topologiegrundlagen | Beta 0.4 | Autorisierungs- und Scan-Limits, Stress |
| M5 | VPN-IP-Capture, PCAPNG, Packet decoder | Beta 0.5 | Korrektes Forwarding, kein Leak, VPN collision |
| M6 | LLDP/CDP via remote agent; optional validierter USB L2 driver spike | RC 0.9 | Reproduzierbarer echter L2-Test |
| M7 | UX/A11y, Sicherheitsreview, Performance, Release-APK | 1.0 | Signierte APK, Restore, Regression, SBOM |

Zeitannahme nur als Planungsrahmen: M0 1–2 Wochen, M1 3–5, M2 3–5, M3 4–6, M4 4–6, M5 5–8, M6 4–8, M7 2–4. Stark abhängig von Hardware, Anzahl Entwickler und CI. Parallelisierung nur nach stabilen Interfaces.

## 7. Issue-Backlog (Definition of Done je Epic)

- NT-001 Repo scaffold, README/AGENTS.md, Gradle wrapper, Compose shell.
- NT-002 Architektur-ADRs und Capability Matrix ohne/mit USB/Remote/Root.
- NT-003 CI debug APK artifact, reproducible pinned dependencies, lint/unit tests.
- NT-004 Secure signing release pipeline, environment protection, no committed signing keys.
- NT-010 Android Connectivity source-network abstraction + IPv6.
- NT-011 Ping engines with truthful ICMP/TCP/HTTP method labels.
- NT-012 Traceroute engine + graphical hop timeline and tests.
- NT-013 DNS resolver engine UDP/TCP/DoT/DoH; DNSSEC validator separation.
- NT-014 RDAP/WHOIS and ASN/BGP RIPEstat multi-provider service with caching.
- NT-015 TCP/TLS/HTTP Diagnostics.
- NT-020 SmokePing metrics, sampling and storage.
- NT-021 Graph engine, reports and export.
- NT-022 Monitoring background policy, notifications and battery tests.
- NT-030 SSH + host key security.
- NT-031 USB permissions/discovery, serial driver matrix.
- NT-032 USB serial console (CDC/FTDI/CP210/CH34).
- NT-033 USB Ethernet OS network support; source binding.
- NT-034 USB L2 hardware feasibility spike (explicit no-go allowed).
- NT-040 WiFi scanner and permission policy.
- NT-041 Authorized device discovery and SNMP v3.
- NT-050 VPN consent and safe forwarding engine.
- NT-051 packet parsers, display filters, pcapng test fixtures.
- NT-052 remote capture agent authentication and transport.
- NT-053 LLDP/CDP parser, real agent/capture source, topology view.
- NT-060 security/telemetry/privacy review, performance, accessibility, signed release.

DoD je Issue: Tests + Dokumentation + Screenshots bei UI + API-Breakage Review + IPv6/Fallback consideration + kein ungeprüfter Sicherheits- oder Hardwareclaim.

## 8. Testmatrix

Android-Versionen: minSdk 26 als Vorschlag, aktuelle targetSdk anhand Play-Policy beim Build festlegen. Geräte: Samsung Tablet, Pixel-Referenztelefon, Android 10/12/14/15/16 soweit Testhardware verfügbar. Netze: WLAN, Mobilfunk, USB-Ethernet, VPN, IPv4 only, IPv6 only, Dual Stack, Captive Portal, DNS timeout, DNSSEC bogus, packet loss, lokale LAN-Netze. USB: ohne Host-Mode, entzogenes Permission, Abziehen bei Transfer, Hub/Power Delivery, CDC-ACM/FTDI/CP210/CH34, systemseitig unterstützte Ethernet-Adapter. SSH: Host-Key-Wechsel, Key-Import, Timeouts, Paging. Capture: kein VPN erlaubt, VPN schon aktiv, App-Kill, IPv6, DNS-Leak, Metered Networks, PCAP File Roundtrip.

Messbare Releases: 0 Kotlin-Compilefehler, 0 instrumentierte Critical Failures, keine uncaught permission SecurityException, unterbrochene Jobs wiederherstellbar oder ausdrücklich als abgebrochen markiert, reproduzierbare APK-Artefakte mit SHA-256 und Changelog.

## 9. Risiken und Forschungsschranken

- Android-ICMP/TTL: Verhalten ist OEM-/API-abhängig; keine universelle Rohsocket-Garantie. Vor verbindlicher Feature-Freigabe Geräte-Spikes.
- LLDP und Ethernet-Capture: VPN-TUN ist Layer 3. Externe Sonde als zuverlässiger Standard; Userspace USB L2 nur nach Hardware-Validierung.
- Android WiFi scan: Standortrechte, Standortaktivierung und Throttling; kein universeller Monitor Mode.
- Foreground/background: Service-Type und Startrestriktionen je targetSdk beachten; keine Garantie für lückenloses Monitoring bei Schlaf-/Batteriepolitik.
- API-Quellen: Rate-Limits, Verfügbarkeit, Lizenzbedingungen und transparente Datenqualität; Offline-Cache.
- Privacy: PCAP, SSH und SNMP können sensible Netz-/Anmeldedaten enthalten. Explizites Capture, klare Retention, gefilterter Export, Geheimnisse nie unmaskiert anzeigen.

## 10. Referenzen

- Android USB Host https://developer.android.com/develop/connectivity/usb/host
- Android VpnService https://developer.android.com/reference/android/net/VpnService
- Android ConnectivityManager https://developer.android.com/reference/android/net/ConnectivityManager
- Android Foreground Services https://developer.android.com/develop/background-work/services/fgs/service-types
- Android Wi-Fi scanning https://developer.android.com/develop/connectivity/wifi/wifi-scan
- RIPEstat API https://stat.ripe.net/docs/data-api/ripestat-data-api
- USB serial https://github.com/mik3y/usb-serial-for-android
- TAKT agent workflow reference https://github.com/derdigitalaffine/takt-platform/blob/main/portal/docs/agent-workflows.md
- TAKT task model routing https://github.com/derdigitalaffine/takt-platform/blob/main/portal/app/ai_task_routing.py
- TAKT specialized agents https://github.com/derdigitalaffine/takt-platform/blob/main/portal/docs/law-ai-agents.md


## 11. Erweiterung: Bluetooth Classic und Bluetooth Low Energy (BLE)

Die App erhält einen eigenen Bereich **Funk & Sensorik** und eine Gerätebibliothek. Bluetooth-Suche ist keine passive Funk-Sniffer-Funktion und Android stellt ohne spezielle Hardware keine allgemeinen Bluetooth-Link-Layer-Rohpakete bereit.

- **BT-001 Geräteübersicht:** Adapterstatus, Classic-Paired-Devices, unterstützte Profile (soweit OS-API), Name/Adresse nur mit Berechtigung, Filter/Favoriten/Herstellerkennung nach lokalem Katalog.
- **BT-002 BLE Discovery:** zeitlich begrenzter kontrollierter LE-Scan mit RSSI, Scan Record, Service-UUIDs, Manufacturer-/Service-Data (hex/dekodiert), Anzeige rotierender/randomisierter Adressen ohne dauerhafte Identitätsbehauptung.
- **BT-003 GATT Explorer:** Services/Characteristics/Descriptors, Properties, MTU, Read/Write mit Bestätigung, Notifications/Indications, Bonding- und Fehlerbehandlung; kein Zugriff ohne Verbindung/Berechtigung.
- **BT-004 Funkdiagnose:** RSSI-Zeitreihe/Heatmap, Advertisements, Scannerate, Vergleichsmessungen, CSV/JSON-Export, klarer Hinweis auf Android-Scan-Throttling und nicht kalibrierte Entfernung aus RSSI.
- **BT-005 Bluetooth Classic:** gekoppelte Geräte, RFCOMM/SPP-Terminal für kompatible Geräte, sichere Sessionverwaltung, explizites Connect/Disconnect; nicht jedes Bluetooth-Gerät stellt SPP bereit.
- **BT-006 Protokoll-Decoder:** BLE GAP/GATT-Standardservices wie Battery/Device Info/Environmental Sensing, optional herstellerbezogene Decoder ohne erratene Werte; optional Bluetooth HCI-Snoop-Import als Offline-Analyse statt heimlichen Host-Stack-Mitschnitts.
- **BT-007 Sicherheit:** Android 12+ `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`; auf Android 11 und älter Legacy-Bluetooth + Standortberechtigung nach OS-Anforderung. Da Funkmessungen mit Kartenposition korreliert werden können, Standortbezug offenlegen; bei aktivierter Geolokalisierung nicht `neverForLocation` behaupten. Scans nie still im Hintergrund.

## 12. Erweiterung: GPS, GNSS, Sensorik und Karten

**Geodaten-Stack:** MapLibre Native Android und **basemap.de** (nicht eine ungeprüfte `nasemap.de`-API). Amtliche basemap.de Style-JSON-URLs:
- Farbe: `https://sgx.geodatenzentrum.de/gdz_basemapde_vektor/styles/bm_web_col.json`
- Grau: `https://sgx.geodatenzentrum.de/gdz_basemapde_vektor/styles/bm_web_gry.json`
- Relief: `https://sgx.geodatenzentrum.de/gdz_basemapde_vektor/styles/bm_web_top.json`
- Dienstnutzung, Attribution, Cachebedingungen und Offline-Download unter den jeweils gültigen basemap.de-Bedingungen prüfen; Offlinekarten nicht ungeprüft herunterladen.

### Geoposition und GPS/GNSS
- **GEO-001:** präzise/grobe Standortberechtigungen, Position (WGS84), Höhe, Accuracy, Speed, Bearing, Zeit, Quelle, Providerstatus, Mock-location-Kennzeichnung wo API vorhanden; keine Positionsbehauptung bei fehlendem Fix.
- **GEO-002:** GNSS Status, Satelliten je Konstellation, C/N0, genutzt im Fix, Azimut/Elevation, TTFF wo verfügbar; Rohmessungen/NMEA nur wenn Chipset/API/OS und Berechtigung es zulassen.
- **GEO-003:** Standort-Track (bewusst Start/Stop), GPX/GeoJSON/KML-Export, Distanz, Richtung, Verlauf, sparsame Samplekonfiguration; lang laufendes Tracking nur als korrekt deklarierter Location Foreground Service.
- **GEO-004:** Karten-Workbench: MapLibre, basemap.de Farbe/Grau/Relief, aktuelle Position, Messpunkte, Tracks, Network-Assets, geolokalisierte WLAN-/BLE-Messungen mit Einwilligung, Heatmap/Marker, Karten-Layer und Suchfunktion; Standortmeldungen niemals unaufgefordert an externe API.
- **GEO-005:** Koordinaten-Toolbox WGS84 / Web Mercator, DMS/Dezimal, Distanz/Azimut, GeoJSON-Koordinatenvalidierung.

### Smartphone-Sensoren
- **SEN-001:** Android SensorManager-Gerätekatalog mit Sensorname/Typ, Vendor, Genauigkeit, Einheit, Stromverbrauch, ReportingMode; fehlende Sensoren klar als nicht vorhanden.
- **SEN-002:** Liveplots für Accelerometer, Gyroskop, Magnetometer, Rotationsvektor, Barometer, Umgebungslicht, Nähe, relative Feuchte, Temperatur (falls vorhanden), Schrittzähler und weitere Gerätetypen – ausschließlich auf unterstützten Geräten.
- **SEN-003:** Kalibrierungs- und Qualitätsstatus, Samplingrate/Batching, numerische CSV/JSON-Exporte, getrennte physikalische Einheiten.
- **SEN-004:** Synchronisierte Netzwerk-/Funk-/GNSS-/Sensor-Messsession mit Zeitachsen, Standort-Datenschutzschalter und exportierbarer Messmetadaten-Provenienz.

### Neue Meilensteine
- **M1.5:** Bluetooth-/BLE-Permissions, BLE-Scan und Gerätekatalog, GPS/Location, Sensorliste, Karten-Workbench mit basemap.de, erstes Geräte-E2E.
- **M3.5:** GATT Read/Write/Notify, RFCOMM-Konsole und Live-Sensorgraphen.
- **M6.5:** Karten-Layer, georeferenzierte Messungen, GNSS-Detaildiagnose, Track- und GeoJSON-/GPX-Export.
- **Release-Blocker:** keine Standort- oder Funkdaten an Dritte ohne transparente Zustimmung; kein angeblicher BLE-Paketsniffer über Standard-Android-Scanning.

### Quellen
- https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- https://developer.android.com/develop/connectivity/bluetooth/ble/find-ble-devices
- https://developer.android.com/reference/android/location/LocationManager
- https://developer.android.com/guide/topics/sensors/sensors_overview
- https://github.com/maplibre/maplibre-native/blob/main/platform/android/docs/artifacts.md
- https://sgx.geodatenzentrum.de/web_public/gdz/dokumentation/deu/basemap.de_web_vektor.pdf
