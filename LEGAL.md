# Rechtliche Hinweise und Betrieb in Italien

Stand: **10. Oktober 2026**. Projekt: **MCCases**, gepflegt von **Plattnericus**.
Kontakt für Projektfragen und Rechteanfragen: **info@plattnericus.dev**.

Dieses Dokument beschreibt den angegebenen Betrieb mit ausschließlich kostenloser
Spielwährung. Es ist keine anwaltliche Prüfung, behördliche Genehmigung oder Zusicherung
der Rechtmäßigkeit. Die tatsächlichen Serverfunktionen, Assets und Betreiberangaben
müssen zu diesen Angaben passen.

## Unabhängiges Projekt und Marken

MCCases ist ein inoffizielles Plugin für Minecraft und weder von Mojang bzw. Microsoft
noch von Valve genehmigt oder unterstützt. Der englische Hinweis wird in der
[beigelegten Notice](src/main/resources/defaults/LEGAL-NOTICE.txt) mitgeliefert.
Counter-Strike und Steam sind Marken von Valve. Fremde Marken werden ausschließlich
zur Beschreibung verwendet; eine Markenlizenz wird nicht erteilt.
[Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines),
[Valve Legal Info](https://store.steampowered.com/legal).

Verantwortlich für einen konkreten Server ist dessen Betreiber. Der Projektkontakt
ersetzt nicht dessen rechtlichen Namen und Datenschutzangaben. Verwende eigene
Serverlogos und eine erreichbare E-Mail; behaupte keine offizielle Partnerschaft.

## Betriebsregeln für die angegebene kostenlose Nutzung

Diese Regeln sind bewusst enger als eine Aussage über sämtliche rechtlich möglichen
Geschäftsmodelle. Sie gelten für den dokumentierten Serverbetrieb:

- Cases, Keys, Diamanten, Emeralds und Skins werden ausschließlich im Spiel erworben
  oder kostenlos vergeben. Keine Käufe mit Geld, Krypto, Gutscheinen oder sonstigen
  Vermögenswerten, auch nicht über bezahlte Ränge, Boosts, Bundles oder Spendenboni.
- Keine Auszahlung, externe Verkäufe, Steam-Transfers, Sachpreise oder Umrechnung
  in Währungen anderer Server. Externe Verkäufe auch in den Serverregeln untersagen
  und bei Bekanntwerden moderieren.
- Sammlung und Handel sind kosmetische Spielfunktionen. Die angezeigten Werte und
  „Value“-Filter beschreiben die konfigurierte Spielökonomie, keinen Geldwert.
- Die tatsächlichen Chancen aus dem aktuellen Katalog veröffentlichen. Betreiber
  prüfen sie mit `/csadmin odds <Case>`; andere Gewichte oder fehlende Tiers verändern
  die Verteilung. Mehrfachöffnungen erhöhen nicht die Chance einer einzelnen Öffnung.

Minecraft beschränkt virtuelle Währungen und verlangt einen für alle Altersgruppen
geeigneten Server. Der Ausschluss von Geldkäufen allein ist keine pauschale Freigabe
einer zufallsbasierten Mechanik.
[Offizielle Serverregeln](https://www.minecraft.net/en-us/usage-guidelines#servers-and-hosting).

Das Plugin verarbeitet Minecraft-Items und enthält keinen Geldkauf oder Cash-out.
Es kann Webshops, andere Plugins oder Absprachen außerhalb des Servers jedoch nicht
kontrollieren. Vor Monetarisierung, Wettbewerben mit Preisen oder einer externen
Handelsplattform ist eine neue Prüfung durch eine in Italien qualifizierte Beratung
erforderlich. Aus diesem Dokument lässt sich insbesondere keine abschließende
Einordnung nach italienischem Glücksspielrecht ableiten.

## Urheberrecht und Lizenzstatus

Im Repository wurde bislang **keine allgemeine Open-Source-Lizenz** erteilt. Diese
Überarbeitung führt keine MIT-, GPL- oder andere allgemeine Lizenz ein. Öffentliche
Quelltexte sind keine pauschale Erlaubnis zur Weiterverwendung. Rechte an eigenen
Beiträgen verbleiben bei ihren jeweiligen Rechteinhabern; gesetzliche Rechte und
bereits gesondert erteilte Erlaubnisse bleiben unberührt. Für zusätzliche Nutzung
oder Weiterverbreitung bitte **info@plattnericus.dev** kontaktieren.

Die Standardgrafiken werden durch die Projektwerkzeuge aus Masken, Mustern und
Paletten gerendert. Das belegt den technischen Erstellungsweg, nicht automatisch
die rechtliche Freigabe aller Vorlagen, Namen oder an bekannte Designs angelehnten
Gestaltungen. Der vorhandene Katalog enthält solche Anlehnungen. Unklare Inhalte
müssen anhand ihrer Herkunft geprüft und gegebenenfalls durch eigene Gestaltungen
ersetzt werden. Ein Hinweis auf „Fanprojekt“ behebt keine fehlende Erlaubnis.

Das separat zusammengeführte **Fusion-HD-Pack** enthält zusätzlich gelieferte
Grafiken bzw. Fonts. Für diese liegt hier kein vollständiger Rechtebeleg vor.
Es wird deshalb nicht als frei lizenzierter Bestandteil dieses öffentlichen Builds
veröffentlicht. Auch private Verwendung ist keine automatische Rechtefreigabe.
Für jedes fremde Asset Quelle, Urheber, Lizenz, erlaubte Verwendung und erforderliche
Namensnennung dokumentieren. Änderungen an Grafiken beseitigen deren Rechte nicht.

Keine Minecraft-/Counter-Strike-Installationen, extrahierten Spieltexturen, fremden
Sounds oder Client-JARs als Bestandteil des Plugins verteilen. Die
[Minecraft EULA](https://www.minecraft.net/en-us/eula) unterscheidet eigenständige
Modifikationen von weiterverteilten Spielversionen. Die tatsächlichen technischen
Abhängigkeiten stehen in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Datenschutz im tatsächlichen Plugin

UUIDs und Spielernamen sind personenbezogene bzw. personenbeziehbare Daten.
Der geprüfte Code speichert insbesondere:

| Daten | Technischer Ort / sichtbare Verwendung |
| --- | --- |
| UUID, Skin-ID, Float, Pattern, StatTrak, Herkunft, Zeitstempel, Favoriten und Status | SQL-Tabelle `skins`; Sammlung anderer bekannter Spieler ist standardmäßig sichtbar |
| Ausgerüstete Skins | `equipped` |
| UUID, Spielername und Öffnungsergebnisse | `openings`; optionale Konsolenausgabe |
| Verkäufername, Käufer-/Verkäufer-UUID, Angebote und Item-Zahlungen | `market_listings`, `market_payments`, `commerce_log` und Reservierungen |
| Auszahlungsvorgänge und Verträge | `emerald_claims`, `emerald_deliveries`, `trade_contracts` |
| Frühere Coin-Bestände und Angebote nach Migration | `wallets`, `legacy_market_listings` |
| Wiederherstellungsbelege | Persistent Data in Minecraft-Spielerdaten sowie deren Backups |

Die Datenbank liegt standardmäßig in `plugins/MCCases/data.db`; alternativ wird die
konfigurierte MySQL-/MariaDB-Instanz verwendet. Entfernte Skins bleiben mit Status
`REMOVED` gespeichert. Es gibt **keine vollständige DSGVO-Löschfunktion** und keine
automatische Aufbewahrungsfrist. Löschanfragen müssen konsistent über abhängige
Tabellen, Spielerdaten und Backups bearbeitet werden; nicht blind einzelne Zeilen
entfernen, solange offene Zahlungen oder Wiederherstellungen bestehen.

Öffentliche Marktangebote, Sammlungsansichten und Goldmeldungen legen Spielernamen
oder Aktivität offen. Deaktivierbare Zusatzdarstellungen sollten bewusst gewählt
werden. `mccases.view` kann über ein Permission-System entzogen werden;
`opening.broadcast-rare`, `trade-in.broadcast-gold` und `display.log-openings`
sind konfigurierbar. Datenbankspeicherung bleibt für die Spielfunktionen erforderlich.

Im geprüften Plugin-Code wurde keine externe Analyse-/Telemetrieintegration gefunden.
Der optionale Pack-Webserver, konfigurierte Datenbankverbindungen und die Auflösung
von NPC-Spielerprofilen sind Netzwerkfunktionen. Minecraft/Paper, Hosting, Proxies,
Webshops und weitere Plugins haben eigene Datenverarbeitungen; deren Logs können
auch IP-Adressen enthalten und gehören in die tatsächliche Serverinformation.

Die italienische [Datenschutzvorlage](docs/PRIVACY-IT.md) muss vor Verwendung mit
Betreiberidentität, Rechtsgrundlage, Empfängern und Aufbewahrung ausgefüllt werden.
Zugriff begrenzen, Hostingvereinbarungen prüfen und Lösch-/Auskunftsanfragen planen.
[DSGVO, insbesondere Art. 5, 6, 12–22, 28 und 32](https://eur-lex.europa.eu/eli/reg/2016/679/oj/eng),
[Informationen des Garante](https://www.garanteprivacy.it/informativa-protezione-dati).

Bei unmittelbar angebotenen Online-Diensten auf Einwilligungsbasis gilt in Italien
grundsätzlich die Schwelle von 14 Jahren für die eigene digitale Einwilligung.
Das ist keine allgemeine Zugangserlaubnis ab 14 und ersetzt keine Prüfung anderer
Rechtsgrundlagen oder des konkreten Angebots.
[Garante zur digitalen Einwilligung Minderjähriger](https://www.garanteprivacy.it/home/docweb/-/docweb-display/docweb/9880951).

## Noch durch den Betreiber zu vervollständigen

- Rechtlicher Name bzw. Organisation und erforderliche Betreiberangaben;
  **info@plattnericus.dev** ist als öffentlicher Kontakt hinterlegt.
- Tatsächlicher Hoster, Länder, Verträge und konkrete Aufbewahrungs-/Löschfristen
  für SQL, Serverlogs, Spielerdaten und Backups.
- Herkunfts- und Nutzungsnachweise für Designs, zusätzliche Fonts, Logos und
  NPC-Skins; gegebenenfalls eigene Ersatzgrafiken verwenden.
- Veröffentlichung der ausgefüllten Datenschutzinformation und Serverregeln an
  einer vor Teilnahme erreichbaren Stelle. Eine Datei im Repository allein reicht
  dafür bei einem separat betriebenen Server nicht aus.

Diese Angaben können nicht aus dem Programmcode verlässlich bestimmt werden.
