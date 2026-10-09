"""Capture current pack inspect layers in the real client for every weapon family."""
import argparse, json, time
from pathlib import Path
import yaml
from capture_inspect_audit import Rcon, client

def main():
    p=argparse.ArgumentParser();p.add_argument('--root',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    root=a.root.resolve();out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
    weapons=yaml.safe_load((Path(__file__).resolve().parents[1]/'src/main/resources/defaults/catalog/weapons.yml').read_text())['weapons']
    rc=Rcon(root/'server');rows=[]
    for folder in ['client','observer']:
        client(root,folder,'camera|FIRST_PERSON');client(root,folder,'hand|RIGHT');client(root,folder,'chat|HIDDEN');client(root,folder,'fov|70')
    for weapon,definition in weapons.items():
        angles=[0,1,2,3] if definition['category'] in {'knife','glove'} else [0]
        for angle in angles:
            response=rc.command(f'mccasesdevcheck DevTester visual DevObserver {weapon} 0 22 eye {angle}')
            if 'VISUAL READY' not in response:raise RuntimeError(response)
            time.sleep(.35)
            for folder in (['client','observer'] if angle==0 else ['observer']):
                file=out/f'{weapon}_{folder}_angle{angle}.png';client(root,folder,'shot|'+str(file))
                rows.append(dict(weapon=weapon,viewer=folder,angle=angle,file=file.name))
        (out/'manifest.json').write_text(json.dumps(rows,indent=2));print('CAPTURED HD INSPECT',weapon,flush=True)
    rc.command('mccasesdevcheck DevTester visual DevObserver stop')
    print('COMPLETE HD INSPECT:',len(weapons),'types,',len(rows),'native captures.',flush=True)

if __name__=='__main__':main()
