"""Capture the real Vanilla menus; remove only the temporary skin grants created here."""
import argparse
import json
import sqlite3
import time
from pathlib import Path
import yaml
from capture_inspect_audit import Rcon, client


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root, output = args.root.resolve(), args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    rc = Rcon(root / 'server')
    storage = yaml.safe_load((root / 'server/plugins/MCCases/config.yml').read_text())['storage']
    database = root / 'server/plugins/MCCases' / storage['sqlite-file']
    table = '"' + (storage['table-prefix'] + 'skins').replace('"', '""') + '"'
    owner = 'e4ac7d31-0311-3306-bf41-92ae317dd453'

    def owned():
        with sqlite3.connect(database.as_uri() + '?mode=ro', uri=True) as db:
            return {row[0] for row in db.execute('SELECT instance_id FROM ' + table + " WHERE owner=? AND status='OWNED'", (owner,))}

    def command(text):
        client(root, 'client', 'command|' + text)
        time.sleep(.2)

    def click(slot):
        rc.command('mccasesdevcheck DevTester guiclick ' + str(slot))
        time.sleep(.15)  # Respect the production one-click-per-tick guard.

    manifest = []

    def shot(name, description):
        client(root, 'client', 'hover|0.02,0.02')
        time.sleep(.2)
        client(root, 'client', 'shot|' + str(output / (name + '.png')))
        manifest.append(dict(file=name + '.png', description=description, client='Vanilla 26.3'))

    client(root, 'client', 'chat|HIDDEN')
    rc.command('tp DevTester 0.5 -60 -5.5 0 0')
    rc.command('csadmin givecase DevTester kilowatt_case 18')
    rc.command('csadmin givekey DevTester case_key 18')
    command('cases'); shot('cases-menu', 'Original pumpkin branding and simplified help')
    click(0); shot('case-sorting', 'Six direct case sort choices')
    click(22); click(46); click(9); shot('case-preview', 'Single opening and one batch start, default nine')
    click(47); shot('batch-quantity', 'Direct quantities, with nine selected')
    command('inventory vanilla'); shot('inventory-menu', 'Original deer branding and collection')
    click(47); shot('inventory-sorting', 'Seven direct collection sort choices')
    command('market'); click(47); shot('market-sorting', 'Direct marketplace sort choices')
    baseline = owned(); fixtures = set()
    try:
        for _ in range(10):
            rc.command('csadmin giveskin DevTester dual_berettas_hideout')
            time.sleep(.1)
        deadline = time.monotonic() + 15
        while len(owned() - baseline) < 10:
            if time.monotonic() > deadline:
                raise TimeoutError('Temporary trade-up skins were not persisted')
            time.sleep(.1)
        fixtures = owned() - baseline
        command('tradeup'); shot('tradeup-selection', 'Original prosse branding and auto selection')
        click(0); shot('tradeup-sorting', 'Direct trade-up sort choices')
        click(22); click(7); shot('tradeup-selected', 'Ten compatible skins selected, review before confirmation')
        click(6); shot('tradeup-rewards', 'Every eligible output in the selected case pool')
        client(root, 'client', 'hover|0.329,0.220')
        time.sleep(.2)
        client(root, 'client', 'shot|' + str(output / 'tradeup-odds.png'))
        manifest.append(dict(file='tradeup-odds.png', description='Reward odds tooltip', client='Vanilla 26.3'))
    finally:
        command('inventory vanilla')  # Closing the preview releases its selections.
        fixtures |= owned() - baseline
        for instance in fixtures:
            rc.command('csadmin removeskin DevTester ' + instance)
        deadline = time.monotonic() + 15
        while owned() & fixtures:
            if time.monotonic() > deadline:
                raise TimeoutError('Temporary grants were not removed')
            time.sleep(.1)
    command('cases')
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2))
    print('CAPTURED MENUS ' + str(len(manifest)) + '; temporary grants removed', flush=True)


if __name__ == '__main__':
    main()
