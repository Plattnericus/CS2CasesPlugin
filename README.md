# MCCases

**Cases öffnen. Skins sammeln. Gemeinsam handeln.**

Ein serverseitiges Plugin für Paper mit einer virtuellen Skin-Sammlung, animierten
Öffnungen, individuellen Inspect-Animationen und einer Spielökonomie aus Minecraft-Items.
Inspiriert von Counter-Strike 2 — für einen normalen Minecraft-Client, ohne Client-Mod.

**1.2.0** · **Paper 26.3, API Build 159 beta** · **Java 25** · **22 Cases · 815 Skins · 63 Modelle**

[Plugin herunterladen](https://github.com/Plattnericus/CS2CasesPlugin/raw/refs/heads/main/release/MCCases-1.2.0.jar) ·
[Resource Pack herunterladen](https://github.com/Plattnericus/CS2CasesPlugin/raw/refs/heads/main/release/MCCases-ResourcePack-1.2.0.zip) ·
[Prüfsummen](release/SHA256SUMS-1.2.0) ·
[Vollständige Referenz (English)](docs/REFERENCE.md)

> **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**
> MCCases ist auch unabhängig von Valve. Kontakt: **info@plattnericus.dev**.

![Beispielgrafiken aus dem MCCases-Katalog](docs/images/skins.png)

[Schnellstart](#schnellstart) · [Spielen](#spielen) · [Befehle](#befehle) ·
[Resource Pack](#resource-pack) · [Konfiguration](#konfiguration) ·
[Aktualisieren](#aktualisieren) · [Rechtliches](#rechtliches) · [Entwicklung](#entwicklung)

## Was enthalten ist

| Funktion | Verhalten |
| --- | --- |
| Katalog | 22 Cases und 815 Skins für 35 Waffen, 20 Messer und acht Handschuhtypen |
| Öffnungen | Bis zu neun öffentliche Roulettes gleichzeitig, automatisch zentriert; größere Mengen als Warteschlange |
| Sammlung | Private 3D-Galerie oder Chest-Menü, Favoriten, Kategorien, Suche, Filter und Sortierung |
| Inspects | 252 individuelle Varianten, bewegliche Modellteile, beide Hände und getrennte Ansichten für Besitzer und Beobachter |
| Direktes Trading | Zwei Angebote, gemeinsame Reservierungen und Bestätigung beider Spieler nach der letzten Änderung |
| Marktplatz | Bezahlung mit Emerald-Items, Such- und Sortierfunktionen, Auszahlung auch an zuvor offline gewesene Verkäufer |
| Trade-in | Zehn kompatible Waffen zur nächsten Seltenheit; fünf Covert-Waffen für einen Gold-Gegenstand |
| Skin-Eigenschaften | Float, Wear, Pattern, StatTrak, Herkunft und Erstellungsdatum; Doppler, Fade und Blue-Gem-Analyse |
| Händler | Villager oder Mannequin, Kauf mit Diamanten, optional wechselnde Skins und Gesten |
| Speicherung | SQLite standardmäßig; alternativ MySQL/MariaDB; Journal und Wiederherstellung unterbrochener Vorgänge |
| Sprache | Englisch als Standard; 45 Sprachdateien, optional Auswahl anhand der Client-Sprache |

Skins sind kosmetisch. Schaden, Haltbarkeit und Verzauberungen bleiben erhalten.
Handschuhe sind Sammel- und Inspect-Objekte; sie ersetzen keine getragene Rüstung.

## Schnellstart

1. Einen **Paper-26.3-Server mit Java 25** vorbereiten und die Minecraft EULA selbst lesen und akzeptieren.
2. [MCCases-1.2.0.jar](release/MCCases-1.2.0.jar) nach `plugins/` kopieren. Nur eine MCCases-Produktions-JAR installieren.
3. Den Server starten. Die Dateien entstehen in `plugins/MCCases/`.
4. Als Operator `/csadmin info` ausführen und mit `/csadmin shop spawn` einen Händler aufstellen.
5. Das passende [Resource Pack](release/MCCases-ResourcePack-1.2.0.zip) im Client aktivieren
   und anschließend `resource-pack.enabled: true` setzen; alternativ die automatische Verteilung verwenden.

Für die ersten Cases, mit einem verbundenen Spieler statt `PlayerName`:

```text
/csadmin givecase PlayerName kilowatt_case 9
/csadmin givekey PlayerName case_key 9
```

Der Spieler öffnet `/cases`, wählt die Case und **Open 9 cases together**.
Jede Öffnung verbraucht eine signierte Case und den passenden Key.

Andere Plugins sind nicht erforderlich. Ohne Resource Pack funktionieren die Sammlung,
Karten-/Hologramm-Vorschau und Blockdarstellungen; die Skin-Sprites benötigen das Pack.
Die `MCCases-DevChecks`-JAR gehört ausschließlich auf einen Testserver.

Die API-Version **26.3 Build 159 ist eine Beta**. Der Build ist auf diesen Stand ausgerichtet;
andere Paper-Versionen und Serverkonfigurationen brauchen eigene Laufzeitprüfungen.

## Spielen

### Sammlung und Ausrüstung

`/inventory` oder `/skins` öffnet die 3D-Galerie. `/inventory vanilla` öffnet deine
Sammlung in einem normalen Chest-Menü. `/knife` zeigt Messer.

| Eingabe in der Galerie | Aktion |
| --- | --- |
| Auf einen Skin schauen | Name und Eigenschaften in der Actionbar |
| Rechtsklick | Inspect-Menü öffnen |
| Linksklick auf ein Messer | Messer ausrüsten oder ablegen |
| Linksklick auf eine Waffe | Skin für den Bogen ausrüsten oder ablegen |
| Schleichen + Linksklick auf eine Waffe | Skin für die Armbrust ausrüsten oder ablegen |
| Schleichen + Rechtsklick | Favorit umschalten |
| Scrollrad / Zahlentasten | Hotbar auswählen; die Galerieseite bleibt erhalten |
| Seitenbuttons | Durch die Sammlung blättern |
| Zehn Blöcke entfernen | Galerie schließen |

Ein Skin kann jeweils einen der Slots `knife`, `bow` oder `crossbow` belegen.
Die Skin-Sammlung ist virtuell; ein fallengelassenes Schwert überträgt keinen Skin.
Andere bekannte Spieler lassen sich ansehen, sofern `mccases.view` erlaubt ist.

### Inspect und Perspektive

- **F** oder `/inspect`: ausgerüsteten Skin inspizieren.
- **Schleichen + F**: normal die Hände tauschen.
- **Schleichen + Rechtsklick** mit einer Skin-Waffe: Inspect starten.
- `/inspect hand`: Ansicht an der Körperhand, geeignet für F5.
- `/inspect view`: Ansicht für die erste Person.

Minecraft meldet den F5-Kameramodus nicht an Paper; deshalb wird die Ansicht per Befehl gewählt.
Die Modelle animieren eigene Display-Entities und ersetzen nicht die Minecraft-Arme.

![Butterfly-Inspect im Minecraft-Client; Aufnahme der ursprünglichen 1.2-Prüfung](docs/images/inspect-butterfly-1.2.png)

### Mehrere Cases öffnen

```text
/cases open kilowatt_case 100
/openings
/cases cancel
```

Mengen von **1 bis 1000** sind möglich. Im Weltmodus laufen bis zu neun Öffnungen
parallel, weitere warten. Im GUI-Modus erscheinen Ergebnisse nacheinander.
Der Server bestimmt jeden Gewinn vor seiner Animation. Wiederholte Klicks können
nicht dasselbe Case-/Key-Paar zweimal reservieren.

`/cases cancel` entfernt wartende Anfragen. Bereits vorbereitete Gewinne werden
abgeschlossen oder wiederhergestellt. Für bestehende Konfigurationen
`opening.max-active-per-player: 9` setzen.

### Trading, Markt und Trade-in

| System | Einstieg | Bezahlung / Bestätigung |
| --- | --- | --- |
| Direktes Trading | `/trade PlayerName` | Beide Seiten bestätigen dasselbe aktuelle Angebot; Änderungen setzen die Bestätigungen zurück |
| Marktplatz | `/market` | Emerald-Items aus dem Inventar; Verkäufer holen Erlöse mit `/market claims` ab |
| Trade-in | `/tradein` oder `/tradeup` | Auswahl prüfen und separat bestätigen; Eingaben werden dauerhaft verbraucht |

Trade-in-Eingaben brauchen kompatible Seltenheit, denselben StatTrak-Status und eine
gültige Quell-Case. Sortierung, Filter und Auswahl bleiben beim Blättern erhalten;
automatisches Auffüllen lässt Favoriten aus. Ergebnisse zeigen **Source: TRADE IN**.

Offene Auszahlungen bleiben gespeichert. Ein volles Inventar wirft keine Erlöse auf
den Boden. SQLite und Minecraft-Spielerdaten sind getrennte Speichersysteme;
[Details zur Wiederherstellung](docs/UPGRADE-1.1.md) beschreiben die Grenzen.

## Befehle

| Spieler | Zweck |
| --- | --- |
| `/inventory`, `/skins`, `/inventory vanilla` | Sammlung anzeigen |
| `/knife [Player]` | Messer anzeigen |
| `/cases`, `/cases open <Case> <Amount>` | Cases und Öffnungen |
| `/openings`, `/cases cancel` | Warteschlange und Ergebnisse |
| `/inspect [hand\|view]` | Inspect starten und Perspektive wählen |
| `/trade [Player]`, `/trade accept`, `/trade decline`, `/trade cancel` | Direktes Trading |
| `/market`, `/market own`, `/market sell <Skin-ID> <Price>` | Marktplatz |
| `/market search <Name>`, `/market claims`, `/market recover` | Suche, Auszahlung und Wiederherstellung |
| `/tradein`, `/tradeup` | Trade-in-Verträge |

| Operator | Zweck |
| --- | --- |
| `/csadmin info` | Version und geladenen Katalog prüfen |
| `/csadmin givecase <Player> <Case> [Amount]` | Cases vergeben |
| `/csadmin givekey <Player> <Key> [Amount]` | Keys vergeben |
| `/csadmin giveskin <Player> <Skin> [Float] [Pattern] [StatTrak]` | Skin vergeben |
| `/csadmin manage <Player>` | Sammlung verwalten, auch bei bekannten Offline-Spielern |
| `/csadmin shop spawn [villager\|mannequin]` | Händler aufstellen |
| `/csadmin odds <Case>` | Tatsächliche konfigurierte Drop-Chancen anzeigen |
| `/csadmin exportpack` | Pack für den aktuellen Serverkatalog erzeugen |
| `/csadmin reload` | Konfiguration und Katalog neu laden |

`<Argument>` ist erforderlich, `[Argument]` optional. Klammern nicht mit eingeben.
Die [vollständige Befehls- und Permission-Referenz](docs/REFERENCE.md#commands)
enthält weitere Admin-, Pattern- und Ausrüstungsbefehle.

`mccases.use`, `mccases.inspect`, `mccases.view`, `mccases.shop`, `mccases.trade`,
`mccases.market` und `mccases.tradein` sind standardmäßig für Spieler freigegeben.
`mccases.admin` ist standardmäßig Operatoren vorbehalten.

## Resource Pack

JAR und [Standard-ZIP](release/MCCases-ResourcePack-1.2.0.zip) gehören zusammen.
Die JAR enthält genau dieses Pack und extrahiert es nach
`plugins/MCCases/resourcepack/MCCases-ResourcePack.zip`.

Für automatische Verteilung die vorhandene `resource-pack`-Sektion bearbeiten:

```yaml
resource-pack:
  enabled: false
  namespace: mccases
  distribution:
    enabled: true
    bind-address: 0.0.0.0
    port: 8165
    public-url: "https://packs.example.net"
    required: false
    prompt: "<gray>MCCases skin textures"
```

Die öffentliche URL muss Spieler über einen eingerichteten Proxy zum Pack-Webserver
führen. Der eingebaute Server selbst verwendet HTTP; er stellt kein HTTPS-Zertifikat
bereit. Alternativ eine erreichbare HTTP-Adresse mit Port angeben.
Die aktive Verteilung schaltet auch die Item-Modelle ein.

Für ein eigenes Serverpack `assets/mccases/` aus der **aktuellen ZIP** übernehmen,
`resource-pack.enabled: true` setzen und die eigene Verteilung verwenden. Nach
Katalogänderungen neu exportieren und das verteilte Pack aktualisieren.
Ein separat kombiniertes Fusion-HD-Pack benötigt zusätzlich Rechte an dessen Grafiken
und Fonts; es ist kein Bestandteil des Standard-Downloads.

## Konfiguration

| Datei / Einstellung | Verwendung |
| --- | --- |
| `config.yml` | Anzeigen, Öffnungen, Sprache, Datenbank und Pack-Verteilung |
| `shop.yml` | Händler, Case-/Key-Preise; Standardwährung `DIAMOND` |
| `market.yml` | Emerald-Preise, Auszahlung, Listings und Trade-Limits |
| `inspect.yml` | Modelle, Gelenke, Animationen und Kamerapositionen |
| `catalog/` | Cases, Skins, Raritäten, Gewichte, Keys und Pattern |
| `messages_*.yml` | Übersetzungen; Standard `language: en`, `client-language: false` |
| `skin-inventory-item.enabled: false` | Kein dauerhafter Shortcut im Inventar |
| `opening.max-active-per-player: 9` | Bis zu neun vorbereitete Öffnungen je Spieler |

Vorhandene Dateien und Einstellungen bleiben bei Updates erhalten. Fehlende Standarddateien
werden ergänzt. Datenbankwechsel erfordern einen Neustart.
[Konfigurationsreferenz und eigene Inhalte](docs/REFERENCE.md#configuration).

Die Standardgewichte sind **79,923 / 15,985 / 3,197 / 0,639 / 0,256** für
Mil-Spec / Restricted / Classified / Covert / Gold. StatTrak beträgt für geeignete
Items standardmäßig **10 %**. Maßgeblich sind die tatsächlich geladenen Gewichte
und Inhalte; `/csadmin odds <Case>` zeigt die resultierenden Chancen.

## Aktualisieren

1. Den Server sauber stoppen.
2. `plugins/MCCases/`, Datenbank, Welt-/Spielerdaten, Journale und `secret.key` gemeinsam sichern.
3. Die alte Produktions-JAR ersetzen und das passende Resource Pack aktualisieren.
4. Starten, `/csadmin info` prüfen und vorhandene Einstellungen bewusst vergleichen.

Die Dateinamen bleiben bei diesem Build **1.2.0**. Für den Stand vom 10. Oktober 2026
sind deshalb die [SHA-256-Prüfsummen](release/SHA256SUMS-1.2.0) entscheidend.

## Rechtliches

Der angegebene Betrieb in **Italien** nutzt ausschließlich kostenlos erworbene
Spielwährung: keine Geldkäufe für Cases, Keys, Öffnungswährung oder Skins, keine
Auszahlung, externe Verkäufe oder Sachpreise. Andere Plugins und Shops dürfen diesen
Betrieb nicht indirekt verändern. Diese Regeln sind keine rechtliche Freigabe.

- [Rechtliche Hinweise und noch offene Betreiberangaben](LEGAL.md).
- [Italienische Datenschutzvorlage — vor Verwendung vervollständigen](docs/PRIVACY-IT.md).
- [Abhängigkeiten, Marken und Asset-Hinweise](THIRD_PARTY_NOTICES.md).

Die Hinweise werden auch in der JAR, dem Pack und beim ersten Start im Datenordner
mitgeliefert. Im Repository besteht **keine allgemeine Open-Source-Lizenz**; öffentliche
Quelltexte allein erlauben keine beliebige Weiterverwendung. Rechteanfragen und
Projektkontakt: **info@plattnericus.dev**.

## Entwicklung

JDK 25 und der mitgelieferte Gradle Wrapper genügen:

```sh
bash gradlew build
```

Das erzeugt `build/libs/MCCases-1.2.0.jar` und
`build/distributions/MCCases-ResourcePack-1.2.0.zip` und führt `verifyFeatures` sowie
`verifyPack` aus. Für die Veröffentlichung werden geprüfte Dateien nach `release/`
kopiert und deren Prüfsummen aktualisiert.

Der [Buildbericht vom 10. Oktober 2026](docs/BUILD-2026-10-10.md) dokumentiert diesen
Upload. Frühere [Live-Prüfungen für Hotbar und HD-Pack](docs/HOTBAR-HD-VERIFICATION.md)
und [Multi-Opening/Trade-in](docs/MULTI-OPENING-VERIFICATION.md) sind separat datiert;
sie sind keine neu ausgeführten Client-Tests dieses Builds.

[Entwicklungsanleitung](docs/DEVELOPMENT.md) · [Vollständige Referenz](docs/REFERENCE.md) ·
[Issues](https://github.com/Plattnericus/CS2CasesPlugin/issues) · [Projektwebsite](https://plattnericus.dev)

MySQL/MariaDB, hohe Spielerzahlen, andere Plugins und abweichende Client-/Serverversionen
brauchen eigene Laufzeitprüfungen. Eigene Kameraeinstellungen und Gelände können die
Display-Modelle verdecken. Reproduzierbare Fehler bitte mit Version, Konfiguration und
bereinigtem Log melden; keine Datenbank, Passwörter oder `secret.key` öffentlich hochladen.
