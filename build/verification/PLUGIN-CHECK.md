# Plugin-Prüfung · 03.10.2026

Geprüft auf Paper 26.2 Build 129, Java 25, SQLite und einem lokalen Vanilla-Client 26.2. Die durchgeführten Prüfungen sind bestanden; es ist keine Garantie für jede Serverkonfiguration.

## Automatische Prüfungen

- `bash gradlew build devChecks inspectFilmstrip`: erfolgreich. 713 Skins, 21 Cases, 5.250 Reward-/Reel-Prüfungen; Gewichtung der Seltenheiten, Float-Grenzen, Seed-Grenzen und StatTrak-Berechtigung.
- Inventar-Kategorien, kombinierte Filter, alle Sortierungen, Recent-Limit, ausgeblendete PENDING-Skins und Aufräumen von Ausrüst-Verweisen.
- 45 vollständige Sprachschemas; regionale Fallbacks, Client-/Server-Auswahl, eigene Nachrichten-Dateien, MiniMessage-Verarbeitung. Deutsch/Französisch zusätzlich mit zwei echten Vanilla-Clients geprüft.
- 46 Animations-Timelines: endliche Matrizen, Rückkehr zur Ausgangspose, Interpolationsabstände, Zufallsvarianten ohne direkte Wiederholung und monotone Case-Abbremsung.
- Export aller 713 Skin-Modelle/Textures, 21 Cases und eines Schlüssels. Vollständige Tick-Prüfung auf Abschneiden; Kontaktbögen und neue GIFs erstellt.
- Aufräum-Test des Dev-Supervisors bestanden; fremde Prozesse und Dateien bleiben geschützt. `git diff --check` sauber.

## Integration auf dem laufenden Server

`mccasesdevcheck DevTester full` hat alle Gruppen bestanden, zuletzt um 11:56:50 Uhr; der Zweiclient-Test um 11:57:39 Uhr. Das ursprüngliche physische Inventar wird nach dem Test wiederhergestellt. Die vollständige Test-Kistenöffnung gibt zusätzlich einen als TEST markierten Skin. Die Belege stehen in `paper-audit.log`.

- Genuine signierte Cases/Keys werden akzeptiert; manipulierte IDs/Test-Flags und GUI-Icons werden zurückgewiesen. Bei fehlendem Schlüssel wird nichts verbraucht; eine Öffnung verbraucht genau ein Paar.
- Shop: Einzel-/Fünferkauf, Stapeln, exakte Zahlung, kein Abzug bei vollem Inventar, während der Zahlung frei werdender Platz, modifizierte Währung, große Preise und veraltete Angebote.
- Nebenhand-Einsetzen in Arbeitsstationen blockiert; permanentes Inventar-Item gegen Duplizieren, Droppen und Handtausch geschützt. Händler öffnet den Shop und ist gegen Schaden geschützt.
- Schwert-, Bogen- und Armbrust-Material, Haltbarkeit, Verzauberungen und ursprüngliche Metadaten bleiben beim Auftragen/Entfernen kosmetischer Skins erhalten. Eigentums-/Slot-Prüfungen verhindern falsches Ausrüsten.
- Echter Armbrust-zu-Bogen-Hotbar-Event, stehend und schleichend; Galerie schließt und lässt den Wechsel zu. Inspect-Abbruch stellt die richtige Waffe wieder her.
- F startet Inspect, Schleichen + F erlaubt normalen Handtausch. Der F5-Handmodus hält die Position unabhängig von der Kopfneigung; gewählter Modus gilt für den Hotkey.
- Zweiter Vanilla-Client `DevObserver` auf Französisch: tatsächliche Client-Locales `de_DE` → Deutsch und `fr_FR` → Französisch; französischer Shop-Titel und Inventar-Token. Ein deutscher Schlüssel wird durch den Produktions-Listener auf Französisch aktualisiert, bei identischer Signatur/Test-Markierung und Menge.
- Zwei sichtbare Inspect-Szenen geprüft: Besitzer sieht ausschließlich die Ego-Szene, Beobachter ausschließlich die Körperhand-Szene. Letztere folgt Körper-Yaw, bleibt unabhängig von Kopf-Pitch bei Augenhöhe minus 0,8 Blöcken. Beide werden beim Abbruch vollständig entfernt.
- Doppelte Skalierung geprüft: Ego `1.24` statt `0.62`, Körperhand `1.3` statt `0.65`; Pack-Faktor weiterhin `0.75`. Bewegungsweite des Griffs wurde begrenzt, damit nicht zusätzlich der Weg verdoppelt wird.
- Case-, Shop-, Skin- und Listen-Menüs öffnen; Icons bleiben geschützt. Pack- und Karten-Vorschauen starten/enden ohne Veränderung des realen Inventars; ein abgebrochener Renderauftrag öffnet keine alte Vorschau mehr.
- SQLite: atomare Öffnung + Audit, idempotente Wiederholung, PENDING-Wiederherstellung, Favoriten, konkurrierende StatTrak-Inkremente, Ausrüst-Persistenz und Offline-Profil. Entfernte Test-Fixtures bleiben ausgeblendet.
- Echte Case-Reel-/Reveal-Sequenz: doppelte Start-Anfrage verhindert, genau ein TEST-Reward, Journal geleert und OWNED-Skin plus Audit dauerhaft gespeichert.
- `/csadmin exportpack`: 713 Skins erfolgreich im laufenden Server exportiert; der Vanilla-Client hat das neu gesendete Pack geladen. HTTP-Datei, ZIP und alle Modell-/Textur-/Item-Inhalte danach erneut geprüft.
- `/csadmin reload`: 713 Skins/21 Cases und 0 Warnungen. Dev-Loadout nach Neustart weiterhin Butterfly/Doppler, AK-47 auf Bogen und AWP auf Armbrust; keine PENDING-Rewards zurückgeblieben.
- Resource-Pack-HTTP: 200 + ZIP-MIME, Hash/Datei korrekt, ZIP vollständig, falsche Pfade 404. Modelle, Texturen und Item-Definitionen entsprechen dem Release-Pack. Das Pack-Icon kann beim Export anders gerendert werden.

## Behobene Fehler

1. Galerie/Inspect hielt die Armbrust beim Wechsel zum Bogen fest: Event wird freigegeben, kosmetische Aktualisierung erfolgt nach dem Slotwechsel.
2. Shop-Zahlung hatte Integer-Überläufe, ungültige Mengen, veraltete Angebote und falsche Platzprüfung. Zahlung und Lieferung werden jetzt zusammen geplant und erst bei Erfolg übernommen.
3. Cases/Keys konnten per Nebenhand in Arbeitsstationen gelangen.
4. Test-Schlüssel erzeugten unmarkierte Drops; Case-/Key-Test-Flags bleiben jetzt auch bei Rückerstattung erhalten, und das Audit markiert den Test.
5. Unvollständige Journal-Einträge konnten durch fehlenden Origin-Wert einen Fehler auslösen.
6. Bereits abgebrochene asynchrone Karten-Vorschauen konnten nachträglich wieder erscheinen.
7. Inspect schwebte zu hoch: neue Ego-Position unten rechts und separater Körperhand-Modus für F5. Aktive Sessions behalten ihre Position/Skalierung auch beim Reload.
8. Beobachter sahen zuvor die Ego-Position vor dem Gesicht. Eigene öffentliche Körperhand-Szene; private Ego-Szene nur für den Besitzer.

## Optimierungen

Alle Teile und beide Inspect-Szenen verwenden pro Sample gemeinsam berechnete Gruppenposen. Szenen werden nur bei geänderter Position/Ansicht teleportiert. Client-Sprach-, Pickup- und Inventar-Ereignisse werden pro Spieler und Tick zusammengefasst. Übersetzungen werden einmal beim Laden eingelesen, ohne Laufzeit-Netzwerkzugriffe. Identische Cases/Keys und unveränderte Inventar-Tokens werden nicht erneut in den Slot geschrieben. Das sind gezielte Optimierungen; ein Lasttest mit vielen Spielern wurde nicht durchgeführt.

Paper meldete mit beiden verbundenen Clients für die letzten 5/10 Sekunden jeweils 1,4 ms durchschnittliche Tickzeit. Die 1-Minuten-Messung enthielt noch den Client-Start. Diese lokale Momentaufnahme ist kein Beleg für große Server.

## Visuelle Prüfung und Grenzen

Dein Screenshot wurde als F5-Frontansicht geprüft: Das Messer lag vor dem Gesicht. Neue Kamera-Kontaktbögen für Waffen und Messer wurden visuell geprüft; `hand_pack_*` zeigt die Handposition anhand einer geometrischen Avatar-Referenz. Alle Varianten bleiben in den gerenderten Ansichten vollständig im Bild.

Die doppelte Größe wurde auf dem laufenden Server eingesetzt und mit echten Player-/Display-Objekten geprüft. Die Renderprüfung kontrolliert jeden Tick bei 70° vertikalem FOV und 16:9, einschließlich transparenter Pack-Sprite-Konturen. Andere FOV-/Seitenverhältnis-Einstellungen können die sichtbare Größe verändern. Animierte Pack-Renderbelege: `pack_butterfly.gif`, `hand_pack_butterfly.gif`, `pack_rifle.gif`, `hand_pack_rifle.gif`.

Die Kontaktbögen sind Renderprüfungen, keine neuen Screenshots des laufenden Minecraft-Fensters. Native Bildschirmsteuerung/-aufnahme ist in dieser Sitzung nicht verfügbar; die tatsächliche Client-Interpolation wurde deshalb nicht automatisiert visuell aufgenommen.

F bzw. `/inspect view`: Ego-Ansicht unten rechts. `/inspect hand`: F5-Körperhand; danach nutzt auch F diesen Modus. Beim nächsten Login gilt wieder Ego-Ansicht. Der Server erhält keine F5-Kameraeinstellung. Exakte CS2-Modelle und frei animierbare Minecraft-Skin-Arme sind mit einem Vanilla-Client rein serverseitig nicht verfügbar; das Plugin nutzt Display-/Sprite-Animationen. Pack-Sprites zeigen den allgemeinen Skin; exakte Pattern/Float-Abnutzung gibt es in Map-/Hologramm-Vorschauen. Ohne Pack werden Blockmodelle verwendet.

Eigennamen bleiben Katalognamen; nicht gebündelte Client-Sprachen nutzen den dokumentierten Fallback. MySQL, andere Paper-Versionen, hohe Spielerzahlen und ein absichtlich ausgelöster Hard-Crash wurden nicht als Laufzeittest geprüft. Die SQLite-/Journal-Wiederherstellung und der sichere Dev-Aufräumpfad wurden gezielt getestet.

Abschließend: Release-JAR und laufender Plugin-JAR bytegleich, 45 Sprachen im JAR, Dev-Testplugin nicht im Release. Pack-HTTP mit 200/ZIP und alle 2.205 Asset-Einträge gegen das gebaute Pack geprüft; falsche Pfade 404. SQLite zeigt keine PENDING-Skins; Butterfly/Doppler, AK-47 und AWP sind weiterhin auf Messer/Bogen/Armbrust ausgerüstet. Der französische Testclient wurde beendet und sein eigener Laufzeitordner entfernt.

## Weiter testen

Vanilla-Client und Dev-Server laufen auf `127.0.0.1:25565`. Der Supervisor beendet sie beim Schließen der überwachten Anwendung oder mit `python3 scripts/dev.py stop` und entfernt die isolierte `.dev/runtime`-Umgebung. Ein einmaliger LaunchAgent räumt nach Reboot/nächstem Login verbliebene Laufzeitdateien auf; Source und Release-Dateien bleiben erhalten.

Server-Belege: `paper-audit.log` im selben Ordner. Visuelle Belege: `build/filmstrip/`, darunter `pack_butterfly_reverse.png`, `hand_pack_butterfly_reverse.png`, `pack_rifle.png` und `hand_pack_rifle.png`.
