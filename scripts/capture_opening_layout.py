"""Capture every live grid size 1..9 on the isolated test server, using nine signed pairs."""
import argparse,json,time,sqlite3
import yaml
from pathlib import Path
from capture_inspect_audit import Rcon,client

def main():
 p=argparse.ArgumentParser();p.add_argument('--root',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 root=a.root.resolve();out=a.output.resolve();out.mkdir(parents=True,exist_ok=True);rc=Rcon(root/'server')
 rc.command('mccasesdevcheck DevTester visual DevObserver stop')
 # Use a fresh location, away from dealer fixtures created by the runtime audits.
 rc.command('tp DevTester 100.5 100 100.5 0 0');rc.command('tp DevObserver 100.5 100 92.5 0 0')
 rc.command('clear DevTester');rc.command('csadmin givecase DevTester kilowatt_case 9 test');rc.command('csadmin givekey DevTester case_key 9 test')
 settings=yaml.safe_load((root/'server/plugins/MCCases/config.yml').read_text())['storage']
 database=root/'server/plugins/MCCases'/settings['sqlite-file'];table='"'+(settings['table-prefix']+'skins').replace('"','""')+'"'
 def rewards():
  with sqlite3.connect(database.as_uri()+'?mode=ro',uri=True) as db:
   return db.execute('SELECT COUNT(*) FROM '+table+" WHERE owner=? AND status IN ('PENDING','OWNED')",('e4ac7d31-0311-3306-bf41-92ae317dd453',)).fetchone()[0]
 baseline=rewards()
 for folder in ('client','observer'):
  client(root,folder,'camera|FIRST_PERSON');client(root,folder,'chat|HIDDEN');client(root,folder,'fov|70')
 manifest=[]
 for amount in range(1,10):
  client(root,'client','command|cases open kilowatt_case 1');deadline=time.monotonic()+5
  while rewards()!=baseline+amount:
   if time.monotonic()>deadline:raise TimeoutError('Opening '+str(amount)+' did not persist')
   time.sleep(.025)
  time.sleep(.35)
  file=out/f'grid-{amount}.png';client(root,'client','shot|'+str(file))
  manifest.append(dict(count=amount,file=file.name,viewer='owner',distance=3))
  file=out/f'grid-{amount}-overview.png';client(root,'observer','shot|'+str(file))
  manifest.append(dict(count=amount,file=file.name,viewer='observer',distance=11))
  print('CAPTURED GRID',amount,'owner and overview',flush=True)
 (out/'manifest.json').write_text(json.dumps(manifest,indent=2))

if __name__=='__main__':main()
