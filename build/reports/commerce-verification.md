# Prüfprotokoll: vereinfachte Handelsoberfläche

5. Oktober 2026 · Paper 26.2 Build 129 · Java 25 · SQLite · zwei verbundene Vanilla-Clients.

- `bash gradlew build devChecks`: bestanden. Einschließlich Bestätigungsrevisionen, Zurücknehmen der Zusage, Angebotsgrenzen, atomarer Übergabe, echten SQLite-Zahlungen, Doppelkäufen, Rückabwicklung, Migration und Neustart der Datenbank.
- `python3 -m unittest discover -s scripts -p 'test_*.py'`: bestanden.
- `mccasesdevcheck DevTester commerce DevObserver`: bestanden. Spielerköpfe mit korrekten Profilen, farblich getrennte Hälften, sofort sichtbare eigene Skins, tatsächliche Auswahl-/Entfernen-/Blättern-/Annehmen-/Abbrechen-Schaltflächen, Live-Gegenangebote, unveränderte Inventarinstanzen, Countdown, Zurücknehmen der Zusage, Änderungen nach Zusage, veraltete Bestätigungen und atomarer Tausch.
- `/trade accept`: über die Befehlsregistrierung sowohl für eine Anfrage als auch für laufende Angebote getestet; ein falscher Partnername wurde zurückgewiesen.
- Geschenk: sichtbarer Hinweis, dass der Geber nichts erhält; Übergabe mit Annehmen-Knopf und `/trade accept` erfolgreich geprüft.
- Marktplatz: Veröffentlichung und Kauf über die Schaltflächen, exakte Coin-Zahlung, Skin-Übergabe und Zurückziehen bestanden. Schließen des Handels und der Quit-Listener geben Reservierungen frei.
- `mccasesdevcheck DevTester full`: bestanden; bestehende Shop-, Case-, Inventar-, Speicher-, Inspect- und Resource-Pack-Funktionen.
- Kopf-Icons verwenden die vorhandenen Spielerprofile als statische Kopien. Im abschließenden Lauf entstanden keine zusätzlichen serverseitigen Profil-Abfragen.
- Installiertes Server-Plugin und Release-JAR haben denselben SHA-256-Wert.

Die Laufzeitprüfungen verwenden Serverdienste und Inventarereignisse bei verbundenen Vanilla-Clients. Visuelle Mausbedienung und MySQL/MariaDB wurden nicht geprüft.

## Release

SHA-256: `37624780bb214288db616ea385c46b036e9ef8908f28893cc95d0eb1663907f0`

## Laufzeit-Ergebnisse

```text
[15:01:49 INFO]: PASS SIMPLE TRADE UI: both real player heads and coloured halves; own skins visible immediately; actual select/remove, pagination and one-click Accept; acceptance withdrawal, /trade accept for invitations and active offers; live counteroffers, cooldown, stale confirmation rejection and atomic exchange.
[15:01:49 INFO]: PASS: trade reservation/equip/delete guards, cooldown and changed-offer confirmation reset with two connected Vanilla clients.
[15:01:50 INFO]: PASS: marketplace menus and icon protection; live listing, cosmetic unequip, buy/publish buttons, exact Coin payment, skin delivery, withdrawal, trade close/quit cancellation and request decline.
[15:01:52 INFO]: PASS: cancel button, clear gift warning and one-sided gift accepted through the button and /trade accept; original owners/locks cleared.
[15:01:52 INFO]: PASS: all commerce runtime checks completed; test skins removed and physical inventories restored.
[15:02:35 INFO]: PASS: signed items, forged markers, atomic pair consumption and workstation offhand protection.
[15:02:35 INFO]: PASS: sword/bow/crossbow cosmetics preserve material, durability, enchantments and original metadata; equip guards.
[15:02:35 INFO]: PASS: shop quantity/payment/stacking, full inventory rollback, payment-freed slot, modified currency, overflow and stale offers; reserved-item guards.
[15:02:35 INFO]: PASS: journal mutations and malformed-entry recovery; case/shop/skin/list menus render and protect icons; dealer interaction and damage protection.
[15:02:36 INFO]: PASS: /inventory keeps the gallery open while scrolling/using number keys in both sneak states; 8/9.999 blocks stay open, exactly 10 and beyond close with complete cleanup, cancelled moves ignored; crossbow -> bow / inspect slot changes preserve the selected weapon.
[15:02:37 INFO]: PASS: F5 body-hand anchor, head-pitch independence, F inspect action and sneak + F normal swap.
[15:02:37 INFO]: PASS: resource-pack item and map previews, asynchronous cancellation, callback and real inventory preserved.
[15:02:37 INFO]: PASS: SQLite opening transaction/idempotency, PENDING recovery, favorite, concurrent StatTrak increments, equip persistence and offline profile snapshot.
[15:02:46 INFO]: PASS FULL AUDIT: actual case reel/reveal, duplicate request guard, exact item consumption, test-key origin, journal cleared and OWNED reward + audit saved. Original inventory restored.
```
