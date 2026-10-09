"""Run isolated Paper menu/opening checks and retain native screenshots plus server evidence."""
import argparse, json, sqlite3, time
import yaml
from pathlib import Path
from capture_inspect_audit import Rcon, client

def main():
 p=argparse.ArgumentParser();p.add_argument('--root',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 root=a.root.resolve();out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
 log=root/'server.log';server=root/'server';deadline=time.monotonic()+60
 storage=yaml.safe_load((server/'plugins/MCCases/config.yml').read_text())['storage']
 database=server/'plugins/MCCases'/storage['sqlite-file']
 table='"'+(storage['table-prefix']+'skins').replace('"','""')+'"'
 while True:
  try:
   rc=Rcon(server)
   if 'DevTester' in rc.command('list') and 'DevObserver' in rc.command('list'):break
  except (OSError,KeyError):pass
  if time.monotonic()>deadline:raise TimeoutError('Both isolated clients must be online')
  time.sleep(.5)
 client(root,'client','chat|HIDDEN');results=[]
 def check(command,marker,screenshot=None,shot_after=1):
  start=log.stat().st_size;began=time.monotonic();response=rc.command(command)
  if 'unexpected error' in response:raise RuntimeError(response)
  captured=False
  while time.monotonic()-began<40:
   text=log.read_bytes()[start:].decode(errors='replace')
   if 'SEVERE' in text or 'ERROR' in text:raise RuntimeError(text)
   if screenshot and not captured and time.monotonic()-began>shot_after:
    client(root,'client','shot|'+str(out/screenshot));captured=True
   if marker in text:
    line=next(line for line in text.splitlines() if marker in line);results.append(dict(command=command,result=line))
    (out/'ui-results.json').write_text(json.dumps(results,indent=2));print(line,flush=True);return
   time.sleep(.1)
  raise TimeoutError(command+' did not produce '+marker)
 check('mccasesdevcheck items','PASS ITEM RUNTIME')
 check('mccasesdevcheck DevTester inventory','PASS INVENTORY MODES')
 check('mccasesdevcheck DevTester tradein-ui','PASS TRADEIN UI','tradein.png',.8)
 check('mccasesdevcheck DevTester spam','PASS SPAM','spam-nine.png',2)
 check('mccasesdevcheck DevTester nine','PASS QUEUE 9','nine.png',2)
 check('mccasesdevcheck DevTester gold','PASS TRADEUP RUNTIME')
 rc.command('csadmin giveskin DevTester butterfly_doppler')
 deadline=time.monotonic()+15;instance=None
 while time.monotonic()<deadline:
  with sqlite3.connect(database) as db:
   row=db.execute("SELECT instance_id FROM "+table+" WHERE skin_id='butterfly_doppler' AND origin='ADMIN' AND status='OWNED' ORDER BY created_at DESC LIMIT 1").fetchone()
  if row:instance=row[0];break
  time.sleep(.1)
 if instance is None:raise TimeoutError('Knife fixture was not persisted')
 rc.command('csadmin equip DevTester '+instance)
 time.sleep(.3)
 check('mccasesdevcheck DevTester','PASS INVENTORY INPUT')
 (out/'server-ui.log').write_text(log.read_text(errors='replace'))
 print('COMPLETE LIVE UI '+str(len(results)),flush=True)

if __name__=='__main__':main()
