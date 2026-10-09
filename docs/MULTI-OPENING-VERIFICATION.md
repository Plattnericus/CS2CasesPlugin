# Neun Cases, Trade-in und Animationen

Stand: 9. Oktober 2026. Änderungen im aktuellen Arbeitsverzeichnis, Version 1.2.0.

Nachträgliche Vorgabe: In `/inventory` wechseln Scrollrad und Zahlentasten die Hotbar,
auch beim Blick auf einen Skin. Ausschließlich die Seitenbuttons blättern die Galerie.
Die ursprünglichen Live-Protokolle zur Scrollsperre dokumentieren die vorherige Vorgabe;
die erneuten Eingabe- und HD-Pack-Prüfungen liegen unter `build/verification/hotbar-hd/`.
Einzelheiten stehen in [HOTBAR-HD-VERIFICATION.md](HOTBAR-HD-VERIFICATION.md).

## Ausgeführter Plan

1. Öffnungen unabhängig verwalten: Jede Case behält ihren eigenen Roll, ihre Quittung und
   ihre Belohnung. Erst nach erfolgreicher Speicherung startet ihre Darstellung.
2. Alle verfügbaren Welt-Roulettes gleichzeitig zeigen: maximal neun; Reihen, Abstände und
   Größe bei jedem Hinzufügen oder Entfernen neu berechnen. Unvollständige Reihen zentrieren.
3. Eingaben klar zuordnen: Das Startmenü nach Annahme schließen, weitere Rechtsklicks auf
   signierte Cases direkt einreihen, doppelte Klicks desselben Ticks ignorieren. Im geöffneten
   Hologramm Scrollrad und Zahlentasten für normale Hotbar-Wechsel freigeben; Seitenbuttons blättern.
4. Trade-in vereinfachen: Waffe direkt auswählen; Seltenheit und StatTrak filtern; fünf
   Sortierungen; passende Inputs seitenübergreifend auffüllen. Auswahl bei Menüwechseln erhalten.
5. Alle Modelle und Animationen prüfen: explizite Hand-/Display-Ausrichtung, saubere und
   deckende Randflächen, vier Varianten pro Typ; anschließend Pack-, Datenbank- und Live-Tests.

## Ergebnis

| Wunsch | Umsetzung |
| --- | --- |
| Eine Case öffnen, Menü schließen | Gültige Einzel- und Mengenanfragen schließen das Case-Startmenü sofort |
| Spam ergibt bis zu neun Cases | Weitere Rechtsklicks während einer Welt-Öffnung ergänzen einzelne Rollen; zusätzliche Klicks nach neun verbrauchen nichts |
| Automatisch zentrieren | Raster für 1–9 Rollen, mittige unvollständige Reihen, kleinere Darstellung vor nahen Wänden und erneute Anordnung nach dem Schließen |
| Inventar und Hotbar | Normale Hotbar-Wechsel im Hologramm; Seitenwechsel ausschließlich über die vorhandenen Buttons. Chest-Menüs schützen weiterhin ihre Items |
| Trade-in einfach bedienen | Direkter Waffenwähler mit Bestandszahlen, Seltenheits-/StatTrak-Filter, getrennte Buttons für Filter-Reset und Auswahl löschen |
| Gut sortieren | Neueste, Name, Seltenheit, Float aufsteigend und Float absteigend; Links-/Rechtsklick wechseln vorwärts/rückwärts |
| Passende Waffen auswählen | Inkompatible Inputs werden sofort ausgeschlossen; automatisches Auffüllen folgt Filter und Sortierung über alle Seiten und überspringt Favoriten |
| Herkunft anzeigen | Waffen-, Messer- und Handschuh-Ergebnisse zeigen `Source: TRADE IN`; Herkunfts-Case und Admin-Herkunft bleiben intern erhalten |
| Kanten und verdrehte Modelle | Alpha-gewichtete Verkleinerung, deckende 3D-Ränder, Rand-UVs und explizite Ausrichtung in beiden Händen und Display-Ansichten |
| Mehrere Animationen für alle | 63 Waffen-/Messer-/Handschuhtypen, jeweils vier Varianten, insgesamt 252; getrennte Gelenke für Mechanik, Butterfly-Griffe und gepaarte Modelle |
| Optimierung | Begrenztes Entity-Fenster, eine gemeinsame Tick-Tonspur, vorab indizierte Herkunfts-Cases und gruppiertes Auffüllen statt wiederholter vollständiger Kandidatensuche |

Mengen über neun bleiben explizite Warteschlangen. Die GUI-Fallback-Darstellung ohne Welt-Rollen
zeigt ihre Ergebnisse nacheinander. Ein vorhandenes niedrigeres Spielerlimit wird respektiert.
Für neun Slots ist `opening.max-active-per-player: 9` erforderlich.

## Automatisierte Prüfungen

`JAVA_HOME=<JDK-25> bash gradlew build devChecks inspectRigPreview`

- 815 Skins, 22 Cases, 5.500 Reward-/Reel-Prüfungen und vorhandene Sprach-/Inventarprüfungen.
- Neun gleichzeitige gespeicherte Welt-Präsentationen, Reihenfolge bei verspäteten SQL-Commits,
  serieller GUI-Fallback und kollisionsfreie zentrierte Raster für 1–9 bei 1,2/3/6 Blöcken Abstand.
- 20 gleichzeitig gespeicherte Öffnungen mit eindeutigen IDs und wiederholbarer Recovery;
  atomare normale und Gold-Trade-ins sowie vorhandene Kauf-/Zahlungs-Rollback-Prüfungen mit SQLite.
- Fünf stabile Trade-in-Sortierungen, Waffen-/Seltenheits-/StatTrak-Filter, 48 Inputs über mehrere
  Seiten, Favoriten-Schutz, 10/5-Input-Verträge und Herkunft für Waffen/Messer.
- Alle neun Hotbar-Slot-Anfragen gegen die tatsächlichen Chest-Menü-Listener mit
  kontrollierten Paper-Event-/View-Proxys; Galerie-Eingaben zusätzlich auf dem Live-Server geprüft.
- Alle 63 Rigs / 252 Timelines: 880.360 berechnete Gelenk-Frames, beide Hände, Eye-/Hand-Ansichten,
  70°-/4:3-Bildgrenzen, endliche Transformationen, Übergänge und Konfigurationsfehler.
- Tatsächlich exportiertes ZIP: 815 Icons, 1.377 Inspect-Layer, 101.988 deckende Randflächen;
  Rand-UVs, vollständige Texturreferenzen, Front-/Rückseiten und Hand-Ausrichtungen.
- Unveränderte alte Standardanimationen werden aktualisiert; angepasste Timelines bleiben erhalten.
- Drei Python-Prüfungen für den isolierten Testlauf, Artefaktpaarung und Prozessbereinigung.

Software-Filmstreifen unter `build/verification/animations/` sind eigene Renderings;
sie sind keine Minecraft-Screenshots.

## Live-Prüfungen

Nach ausdrücklicher EULA-Zustimmung wurde ein isolierter Paper 26.3 Build 159 mit Java 25 und
zwei echten Minecraft-26.3-Clients gestartet. Welt, Datenbank und Minecraft-Einstellungen liegen
unter `.dev/runtime/`. Die regulären Minecraft-Einstellungen wurden nicht verändert.

Bestandene Prüfungen:

- `items`: echte Paper-ItemStacks und Signaturen, Zahlungen, Kapazität, Quittungen und Journal.
- `inventory`: Wechsel Hologramm → Vanilla-Menü → Hologramm; Schutz vor Drag/Number-Key-Entnahme.
- `tradein-ui`: 48 direkt vergebene Admin-Waffen; alle fünf Sortierungen im tatsächlichen Menü,
  zweite Seite, direkte Waffenauswahl, Seltenheits-/StatTrak-Filter, zehn passende Inputs, Favoriten-Schutz,
  Bestätigung/Zurück/Filter-Reset ohne Auswahlverlust und gesperrtes Scrollen.
- `spam`: Einzelöffnung schließt das Menü; signierte Item-Klicks ergeben 1–9 Sessions;
  drei Events pro Tick ergänzen nur eine Case; weitere Klicks verbrauchen nichts;
  genau neun Belohnungen und neun Case-/Key-Paare.
- `nine`: echter Neuner-Button; neun gleichzeitig sichtbare Rollen; unzureichende Keys und
  doppelte Anfragen abgewiesen; neun eindeutige gespeicherte Rewards und History-Einträge;
  Journal und Welt-Entities vollständig entfernt.
- `gold`: zehn normale oder fünf Covert-Inputs über Auswahl und abschließende Bestätigung;
  Waffen-, Messer- und Handschuh-Ausgaben, korrekte Herkunft, SQL-Verbrauch und genau einmalige
  zulässige Gold-Ankündigung.
- Inventar-Eingaben: Scrollen/Zahlentasten in beiden Sneak-Zuständen erlaubt, ohne Seitenwechsel;
  Galerie-Bereinigung bei zehn Blöcken, abgebrochene Bewegung und korrektes Inspect-Slot-Verhalten.

Die Prüfungen dispatchen echte Paper-Menü-/Item-Events für verbundene Spieler. Native Aufnahmen
werden separat über Minecraft-eigene Framebuffer-APIs erzeugt. Die Produktions-JAR enthält
weder den Prüfharness noch den Client-Agenten.

Weitere visuelle Prüfung:

- 2.296 echte Framebuffer-Aufnahmen: alle 63 Typen und 252 Varianten bei Tick 8/22/40 aus
  Besitzer- und Beobachtersicht; bei Messern/Handschuhen zusätzlich drei Seitenwinkel,
  linke Hand und beide F5-Kameras. Die Inspect-Aufnahmen halten die Pose für die Aufnahme an.
- 504 Aufnahmen der normalen gehaltenen Modelle: alle 63 Typen in beiden Händen,
  First-Person-, Beobachter- und beiden F5-Ansichten. Für F5 steht der Beobachter außerhalb des Bildes.
- Neun native Rasteraufnahmen während derselben laufenden Öffnung: die Anzahl wächst von 1 auf 9;
  jede Anfrage wird vor der Aufnahme in SQL bestätigt. Alle unvollständigen Reihen bleiben mittig.
- Fünf natürliche Bewegungsabläufe mit echter Server-Interpolation: AK-47, Butterfly,
  Shadow Daggers, Kukri und Sport Gloves. Beide Clients wurden aufgenommen; nach Ablauf
  jeder Animation bleiben keine ItemDisplay-Entities zurück.

Live-Ergebnisse und Aufnahmen: `build/verification/live/`. `ui-results.json` enthält die
bestandenen UI-Prüfungen; `artifacts.json` bestätigt per SHA-256, dass Live-Plugin und Pack den
finalen Build-Artefakten entsprechen. Die visuellen Verzeichnisse enthalten eigene Manifeste,
Kontaktbögen und Motion-GIFs. Die Einzelbilder sind keine Prüfung aller möglichen Kombinationen
aus Skin, Pattern, Float und Client-Einstellungen; das exportierte Pack wurde für alle 815 Skins geprüft.

## Artefakte und Wiederholung

- Plugin: `build/libs/MCCases-1.2.0.jar`
- Passendes Pack: `build/distributions/MCCases-ResourcePack-1.2.0.zip` (auch in der Plugin-JAR enthalten)
- Reiner Prüfharness: `build/libs/MCCases-DevChecks-1.2.0.jar`

Das neue Pack muss im Client geladen sein, damit die Modell- und Kantenkorrekturen sichtbar sind.
Bei einem eigenen kombinierten Pack müssen dessen MCCases-Assets ebenfalls aktualisiert werden.

```sh
python3 scripts/verify_live_ui.py --root .dev/runtime --output build/verification/live
python3 scripts/capture_inspect_audit.py --root .dev/runtime --output build/verification/live/animations
python3 scripts/build_visual_evidence.py --captures build/verification/live/animations --output build/verification/live/sheets
python3 scripts/capture_held_models.py --root .dev/runtime --output build/verification/live/held
python3 scripts/capture_opening_layout.py --root .dev/runtime --output build/verification/live/layouts
python3 scripts/capture_inspect_motion.py --root .dev/runtime --output build/verification/live/motion --only ak47,butterfly,shadow_daggers,kukri,sport_gloves
```

Die Aufnahme-Skripte benötigen zwei verbundene Testclients, den lokalen RCON-Zugang und den
separat kompilierten `src/tools/client/ClientCaptureHarness.java` als
`.dev/runtime/capture-harness.jar`. `dev.py` lädt diesen Agenten nur, wenn die Datei existiert.
`python3 scripts/dev.py stop` entfernt die isolierte Laufzeit; Prüfergebnisse in `build/` bleiben erhalten.
