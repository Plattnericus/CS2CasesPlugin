# Hotbar und Fusion-HD-Pack

Stand: 9. Oktober 2026, Version 1.2.0.

In `/inventory` wechseln Scrollrad und Zahlentasten die Hotbar, auch beim Blick auf einen
Skin. Die Galerie bleibt auf derselben Seite; ihre Seitenbuttons blättern weiterhin.
Der Galerie-Listener fängt Held-Slot-Pakete nicht mehr ab. Chest-Menüs schützen ihre Items
weiterhin vor Entnahme, Drag und Hotbar-Swaps.

Das vorhandene Fusion-HD-Pack wurde mit den aktuellen Modellen und deckenden Inspect-Rändern
aktualisiert. Die 128px-Artwork bleibt erhalten; die Inspect-Alpha-Masken passen zur aktuellen
Geometrie. Die zusätzlichen Minecraft-Schriftgrafiken sind bytegleich zum gelieferten Pack.
Die ursprüngliche ZIP bleibt erhalten.

## Ergebnisdateien

- Downloads: `/Users/nexor/Downloads/MCCases-ResourcePack-Fusion-HD-1.2.0.zip`
- Build: `build/distributions/MCCases-ResourcePack-Fusion-HD-1.2.0.zip`
- Plugin mit Scrollkorrektur: `build/libs/MCCases-1.2.0.jar`
- Nachweise: `build/verification/hotbar-hd/`

Für die Scrollkorrektur die neue Plugin-JAR installieren. Die ZIP im Client aktivieren.
Bei manueller Pack-Verteilung `resource-pack.enabled: true` verwenden; das Standardpack
nicht zusätzlich über das HD-Pack laden. Die Produktions-JAR enthält weiterhin das
Standardpack; das kombinierte HD-Pack wird als separate ZIP ausgeliefert.

## Bestandene Prüfungen

- Standardbuild und vollständige Feature-Prüfungen; erneute Prüfung der tatsächlichen HD-ZIP.
- 815 Skins, 1.377 Inspect-Layer und 101.988 Randflächen: Texturreferenzen, deckende UVs,
  Front-/Rückseiten, beide Hände, Vorwärtsausrichtung und transparente Canvas-Ränder.
- Paper 26.3 Build 159 und zwei echte Minecraft-26.3-Clients mit dem finalen HD-Pack.
- 96 tatsächliche Client-Scroll-Callbacks: alle neun Slots, beide Richtungen und Slot 9/1,
  jeweils stehend/schleichend sowie über einer Karte/außerhalb der Galerie. Client und Server
  halten denselben Slot; Seitenzahl und Galerie-Entities bleiben unverändert.
- Echter Client-Rechtsklick auf den nächsten Seitenbutton: Seite 1 → 2, Hotbar unverändert.
- Paper-Prüfungen für Zahlen-/Scrollpakete, Inspect-Wechsel, Galerie-Bereinigung,
  Hologramm/Vanilla-Wechsel und Trade-in-Waffenauswahl, alle fünf Sortierungen und Filter.
- 210 native Inspect-Aufnahmen: alle 63 Typen bei Tick 22 der ersten Variante, Besitzer und
  Beobachter; bei allen Messern und Handschuhen drei zusätzliche Beobachterwinkel.
- 504 native gehaltene Modelle: alle 63 Typen, beide Hände, First Person, Beobachter und
  beide F5-Kameras. Die Kamera wird mit den Minecraft-eigenen APIs eingestellt.
- Neun native Rasteraufnahmen derselben Öffnung: 1–9 Cases, zentrierte unvollständige Reihen.
- Insgesamt 736 finale Client-Aufnahmen. Keine verbleibenden ItemDisplay-, TextDisplay-
  oder Interaction-Entities nach Ablauf der Öffnungen.
- Drei Python-Prüfungen für die isolierte Entwicklungsumgebung.
- CRC-Prüfung der gelieferten ZIP; SHA-256 stimmt zwischen Build, beiden Client-Packs und
  Downloads-Kopie überein. Live-Plugin und finale Plugin-JAR stimmen ebenfalls überein.

Die Screenshots prüfen repräsentative Skins je Typ und ausgewählte Inspect-Posen.
Alle 815 Assets werden automatisch geprüft; nicht jede Skin-/Float-/Pattern-Kombination
wurde im Client aufgenommen. Die vollständigen Animationstests des vorherigen Durchlaufs
stehen in `MULTI-OPENING-VERIFICATION.md`; die Timelines wurden für die Scrollkorrektur
nicht geändert. Die isolierten Clients, der Server und ihre temporären Daten werden nach
dem Test entfernt; Aufnahmen und Protokolle bleiben im Build-Verzeichnis.

## Wiederholung

```sh
python3 scripts/refresh_fusion_pack.py \
  --base /Users/nexor/Downloads/MCCases-ResourcePack-Fusion-HD.zip \
  --current build/distributions/MCCases-ResourcePack-1.2.0.zip \
  --output build/distributions/MCCases-ResourcePack-Fusion-HD-1.2.0.zip
JAVA_HOME=<JDK-25> bash gradlew verifyPack \
  -PpackToVerify=/Users/nexor/Github/CS2CasesPlugin/build/distributions/MCCases-ResourcePack-Fusion-HD-1.2.0.zip
python3 scripts/verify_gallery_scroll.py --root .dev/runtime --output build/verification/hotbar-hd/scroll
python3 scripts/capture_pack_audit.py --root .dev/runtime --output build/verification/hotbar-hd/inspect
python3 scripts/capture_held_models.py --root .dev/runtime --output build/verification/hotbar-hd/held
python3 scripts/capture_opening_layout.py --root .dev/runtime --output build/verification/hotbar-hd/layouts
```

Vor den Client-Prüfungen muss der isolierte Testserver laufen und exakt diese HD-ZIP
in beiden Testclients geladen sein. `artifacts.json` dokumentiert die geprüften Dateihashes.
