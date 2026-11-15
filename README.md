# NetTools Android

Native Netzwerktoolbox mit Kotlin, Jetpack Compose, Android Bluetooth/BLE, GPS/Sensoren und MapLibre mit amtlicher basemap.de-Karte.

## Build
```sh
gradle :app:assembleDebug
```
Benötigt JDK 17, Android SDK 35 und Gradle 8.13. GitHub Actions baut eine installierbare Debug-APK als Workflow-Artefakt. Kein Release signiert; Debug-APK ist kein Qualitätsnachweis für alle Hardwarefunktionen.

## Real implementierter Alpha-Umfang
Netzwerkübersicht, TCP-Connect-Test, Hostnamenauflösung via Systemresolver, BLE-Discovery mit Permission und Stop, laufende Android-Sensorwerte, GPS-Position, MapLibre-Karte mit basemap.de und USB-Geräteliste. Weitere Roadmap-Funktionen sind **noch nicht implementiert**.

[Master-Roadmap](docs/roadmap/MASTER-ROADMAP.md)

## Sicherheit und Datenschutz
Standort und Bluetooth nur nach Android-Systemfreigabe; kein dauerhafter Trackingservice. Keine Telemetrie. Bei der Kartenanzeige werden Kartenkacheln von basemap.de abgerufen; Kartennutzung überträgt technische Anfragedaten an diesen Dienst. BLE-Scan ist kein Rohpaketmitschnitt.
