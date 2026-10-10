"""Capture real first/third-person item renderer transforms for every stock weapon type."""
import argparse,json,time
from pathlib import Path
import yaml
from capture_inspect_audit import Rcon,client

def main():
 p=argparse.ArgumentParser();p.add_argument('--root',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--only-f5',action='store_true');p.add_argument('--only',default='');a=p.parse_args()
 root=a.root.resolve();out=a.output.resolve();out.mkdir(parents=True,exist_ok=True);repo=Path(__file__).resolve().parents[1]
 weapons=yaml.safe_load((repo/'src/main/resources/defaults/catalog/weapons.yml').read_text())['weapons'];rc=Rcon(root/'server');manifest=[]
 if a.only:weapons={id:weapons[id] for id in a.only.split(',')}
 client(root,'observer','camera|FIRST_PERSON');client(root,'observer','chat|HIDDEN')
 client(root,'client','chat|HIDDEN')
 for folder in ['client','observer']:client(root,folder,'fov|70')
 for id in weapons:
  for hand in ['RIGHT','LEFT']:
   client(root,'client','hand|'+hand);time.sleep(.1)
   response=rc.command('mccasesdevcheck DevTester visual DevObserver held '+id)
   if 'HELD READY' not in response:raise RuntimeError(response)
   # Let the vanilla re-equip animation finish before judging the held model.
   client(root,'client','camera|FIRST_PERSON');time.sleep(.8)
   for folder,camera in [('client','FIRST_PERSON'),('observer','FRONT_OBSERVER'),('client','THIRD_PERSON_BACK'),('client','THIRD_PERSON_FRONT')]:
    file=out/f'{id}_{hand.lower()}_{camera.lower()}.png'
    if camera.startswith('THIRD_'):
     rc.command('tp DevObserver 20 100 20');client(root,folder,'camera|'+camera);time.sleep(.25)
    if not a.only_f5 or camera.startswith('THIRD_'):client(root,folder,'shot|'+str(file))
    if not file.exists():raise FileNotFoundError(file)
    manifest.append(dict(weapon=id,hand=hand,camera=camera,file=file.name))
  (out/'manifest.json').write_text(json.dumps(manifest,indent=2));print('CAPTURED HELD',id,flush=True)
 client(root,'client','camera|FIRST_PERSON');client(root,'client','hand|RIGHT');rc.command('mccasesdevcheck DevTester visual DevObserver stop')
 print('COMPLETE HELD',len(weapons),len(manifest),flush=True)

if __name__=='__main__':main()
