# HX IPTV – Fire TV App

Native Android-TV-App (Kotlin, Jetpack Compose, Media3/ExoPlayer, Room) für Fire TV.

## Was sie kann

- M3U-Playlist laden, Sender nach Kategorien sortiert, dazu Favoriten
- Senderlogos (`tvg-logo` aus der Playlist) werden einmal geladen und dauerhaft auf dem Gerät gecacht
- TV-Programm (XMLTV, auch `.gz`) wird heruntergeladen und lokal gespeichert: Jetzt/Danach pro Sender, Fortschrittsbalken, Programmliste im Detail-Panel
- Zuordnung des Programms per `tvg-id`, ersatzweise per Sendername
- Automatische Aktualisierung alle 12 Stunden im Hintergrund, Daten bleiben offline verfügbar
- Stabile Wiedergabe: großer Puffer, viele Ladeversuche, automatischer Reconnect mit Wartezeit, Watchdog gegen hängendes Puffern

## APK bauen (GitHub Actions)

1. Auf GitHub ein neues Repository anlegen (am besten privat).
2. Den kompletten Inhalt dieses Ordners hochladen, **inklusive** des versteckten Ordners `.github`.
3. Im Repo auf **Actions → Build APK → Run workflow** klicken (läuft auch automatisch bei jedem Push).
4. Nach ca. 5–10 Minuten unter dem fertigen Lauf das Artefakt **hx-iptv-apk** herunterladen und das ZIP entpacken.

## Auf den Fire TV installieren

1. Fire TV: **Einstellungen → Mein Fire TV → Entwickleroptionen → Apps unbekannter Herkunft** erlauben.
2. APK installieren, entweder
   - mit der App **Downloader** (APK irgendwo hosten und URL eintippen), oder
   - per ADB: `adb connect <Fire-TV-IP>` und `adb install hx-iptv.apk`.

## Bedienung

| Taste | Aktion |
| --- | --- |
| Hoch/Runter in der Liste | Sender und Kategorien durchblättern |
| Mitte (OK) | Sender abspielen |
| Menü-Taste (drei Striche) | Favorit setzen/entfernen |
| Im Player Hoch/Runter | Sender wechseln |
| Im Player OK | Infoleiste mit Jetzt/Danach |
| Zurück | Player schließen, danach App beenden |

## Hinweise

- Die APK wird mit dem Debug-Schlüssel signiert. Für den privaten Gebrauch per Sideload genügt das.
- Logos kommen aus der Playlist (`tvg-logo`). Fehlt eines, erscheinen die Initialen des Senders.
