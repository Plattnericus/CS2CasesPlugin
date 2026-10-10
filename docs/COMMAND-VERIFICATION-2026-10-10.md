# Befehls- und Live-Prüfung — 10. Oktober 2026

Geprüft wurde die aktualisierte **MCCases-1.2.0.jar** mit Temurin 25.0.2,
Paper 26.3 Build 159 beta, SQLite und zwei verbundenen Vanilla-Minecraft-26.3-Clients.
Server, Welt, Datenbank und Clientprofile waren getrennt von normalen Installationen.
Die Veröffentlichung verwendet dieselbe Produktions-JAR wie der erfolgreiche Live-Durchlauf.

## Änderungen

- Ungültige oder zusätzliche Argumente erhalten eine Fehlermeldung mit Syntaxhinweis.
  Unbekannte Cases und Skin-IDs, gesperrte Aktionen, Inspect-Cooldown und fehlende
  Inspect-Modelle melden einen konkreten Grund.
- Unerwartete synchrone und asynchrone Befehlsfehler erhalten eine Referenz im Chat;
  technische Details werden mit dieser Referenz im Serverlog gespeichert.
- Admin-Vergaben, Änderungen, Löschungen und Ausrüstungswechsel bestätigen den Erfolg
  nach dem Datenbank-Commit. Fehlgeschlagene Schreibvorgänge bestätigen keinen Erfolg.
  Änderungen an Float, Pattern und StatTrak verändern die aktive Instanz erst nach dem Speichern.
- Skin-Löschung und Entfernung der Ausrüstungsreferenz sowie Ausrüstungswechsel zwischen
  Slots werden jeweils in einer SQL-Transaktion gespeichert. Fehlgeschlagene Wechsel
  stellen die vorherige Anzeige und die bisherigen Slots wieder her.
- Gleichzeitige Ausrüstungswechsel und erneutes Bestätigen eines bereits speichernden
  Handels melden eine Sperre. Skin-IDs unter acht Zeichen und mehrdeutige Präfixe
  werden abgelehnt. Skin-spezifische Float-Grenzen und StatTrak-Unterstützung werden geprüft.
- Ungültiges `config.yml` lässt beim Reload den zuvor geladenen Katalog aktiv.
  Pack-Export meldet fehlgeschlagene Assets oder Verteilungsfehler statt vollständigen Erfolg.

## Befehlsprüfung

**PASS ALL 135 COMMAND CHECKS**. Das separate Entwicklungsplugin prüfte alle 14
Spielerbefehlsnamen einschließlich Aliasen sowie den Admin-Baum. Die eigentlichen
Produktionshandler, Dienste und SQL-Verbindungen liefen auf Paper. Kontrollierte
Player-/CommandSource-Proxies fingen Antworten ab und stellten Rechteverweigerung,
Konsolenquellen und aufgelöste Spielerargumente her; sie ersetzten keine Datenbank.
Zusätzlich wurden die registrierten Befehle mit tatsächlichen Spielern ausgeführt.

Geprüfte Bereiche:

- Registrierung, Konsolenfehler und Rechteverweigerung für alle Spielerbefehle/Aliase;
  ungültige Argumentzahlen, Case-Mengen, fehlende Spieler/Anfragen und unbekannte IDs.
- Admin-Hilfe, Info, Case-/Key-/Skin-Vergaben, Liste, Historie, Odds, Verwaltung,
  Vorschau, Pattern, Browser, Scan, Händler beider Typen, Reload und Pack-Export.
- Equip/EquipSlot, Float/Pattern/StatTrak, ungültige Slots/Werte, kurze IDs,
  Skin-Löschung, temporäre und behaltene Testcases sowie `/cases open` bis zum gespeicherten Ergebnis.
- Tatsächliche Menüs für Markt, eigene Listings, Suche, Skin-Auswahl, Verkauf,
  Handel, Cases, Öffnungen und Trade-in; alle drei Inspect-Modi und Cooldown-Fehler.
- Absichtlich ausgelöste synchrone/asynchrone Exceptions und SQLite-Triggerfehler
  bei Vergabe, Bearbeitung, Löschung und Ausrüstungswechsel. Keine falsche
  Erfolgsmeldung, keine verloren gegangene Skin-Instanz und bestätigte Wiederherstellung
  des ursprünglichen Ausrüstungsslots im Speicher **und** in SQLite.
- Absichtlich ungültiges YAML: Fehlermeldung und unveränderter aktiver Katalog.
  Eine zusätzliche Build-Regression prüft zwei UUIDs mit identischem achtstelligem
  Präfix und die eindeutige Auflösung über die vollständige UUID.

Fehlerlogs mit `EXPECTED ... audit injection` bzw. `EXPECTED ... audit SQL`
gehören zu den absichtlichen Fehlertests. Das Ergebnisprotokoll steht im Testserver
unter `plugins/MCCasesDevChecks/command-audit.txt`.

## Weitere Laufzeitprüfungen

Alle zehn nacheinander ausgeführten Live-Suites bestanden; die Rückmeldungen wurden
aus den tatsächlichen Clientlogs gelesen. Zusätzlich bestand der Paper-Item-/PDC-Test.

| Prüfung | Abgedecktes Verhalten |
| --- | --- |
| Vollständige Integration | Signaturen und Fälschungen, Material/Metadaten von Kosmetikitems, Händler, Journal, Galerie/Hotbar, F5/F/Sneak, Item-/Map-Vorschauen, SQL und zwei parallele Öffnungen |
| Zwei Clients | Getrennte Owner-/Observer-Szenen, Spracheinstellung, signierte Items, Körper-/Handanker und vollständiges Entity-Cleanup; beide Clients verwendeten `en_US`, Plugin-Sprache `en` |
| Handel/Markt | Paginierte 81-Skin-Auswahl, Reservierungen, geschützte Angebote, zwei Bestätigungen, Emerald-Kauf, Teil-/Vollinventar-Auszahlung, sechs parallele Öffnungen und gleichzeitiger Direkt-Handel |
| Trade-up | Tatsächlicher Alias und Menüs, zehn normale bzw. fünf Covert-Eingaben, Messer-/Handschuh-Ausgänge, atomarer Verbrauch und Herkunft/Ankündigung |
| Trade-in-Menü | 48 isolierte Fixtures, fünf Sortierungen, Waffen-/Rarity-/StatTrak-Filter, Seiten, Auffüllen, Favoritenschutz, Review/Zurück und Hotbar-Schutz |
| Case Guide | Preis/Wert, Favoriten-/Messerfilter, Vorschau/Zurück, Budget null, ID-Suche und Reset |
| Neunerwarteschlange | Exakte signierte Paare, Schlüssel-/Doppelanfragenfehler, neun verschiedene gespeicherte Rewards, Journal und Entity-Cleanup |
| Warteschlangensteuerung | GUI-Menge 100, ungültige Zahlen, Abbruch gibt 91 Reservierungen frei; Teleport erhält deren Paare und finalisiert neun vorbereitete Ergebnisse |
| Spam-Öffnungen | Gleicher Tick erzeugt keine Duplikate, Limit neun, neun Rewards und exakt neun verbrauchte Paare |
| Paper-Items/PDC | Emeralds in Storage/Offhand, Armor-Ausschluss, Überläufe/Negativwerte, Kapazität, idempotente Receipts, 20 Case-/Key-Paare und Journalformate |

Zusätzliche Netzwerkbefehle prüften den Admin-Alias, Vergabemengen, Parserfehler,
unbekannte IDs, Legacy-Anzeige, Markt-Guthaben/Auszahlungen/Recovery, Handelsanfrage,
Annahme/Abbruch und den Admin-Befehl ohne Operatorrechte. Das tatsächliche Handelsmenü
wurde im Observer-Client aufgenommen und angesehen.

Die Testvorbereitung wurde korrigiert: Der Item-Preview-Test benötigt aktivierte
Pack-Modelle; der Trade-in-Filtertest isoliert seine Sammlung von vorherigen Test-Skins.
Die zusätzlichen Netzwerkproben wurden zeitlich verteilt, nachdem das Vanilla-Limit
für zu viele schnell gesendete Befehle einen Testclient getrennt hatte.
Der abschließende Durchlauf ist erfolgreich; keine dieser Testannahmen wird als
ungeklärter Produktfehler geführt.

## Assets und Grenzen

Der SHA-256-Vergleich vor/nach der Arbeit bestätigt **4.758 unveränderte Dateien**:
PNG-/JSON-Assets unter den vorhandenen Textur-/Resource-Pack-Verzeichnissen,
das öffentliche Standardpack und das vorhandene lokale Fusion-HD-Pack.
Auch das neu gebaute Standardpack ist bytegleich zum bisherigen Download:
`3ff1006677f903ad8be6d4df8dae02f929aea28a3003e052af7a660e9e745275`.

Der [Buildbericht](BUILD-2026-10-10.md) dokumentiert Katalog-/Modellprüfungen,
Produktionsklassen und JAR-Prüfsumme; das [gekürzte Protokoll](COMMAND-VERIFICATION-2026-10-10.log)
enthält die tatsächlichen Prüfergebnisse. Das Entwicklungsplugin wird nicht veröffentlicht
oder in die Produktions-JAR eingebettet.

Die Prüfungen decken die angegebenen Szenarien auf dieser Paper-/SQLite-Konfiguration ab.
MySQL/MariaDB-Laufzeit, große Spielerzahlen, beliebige andere Plugins, Netzbedingungen
und abweichende Versionen wurden hier nicht geprüft. Es wird keine universelle
Fehlerfreiheit oder individuelle rechtliche Freigabe zugesichert.
