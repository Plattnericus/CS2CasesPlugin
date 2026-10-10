"""Record actual server interpolation and natural scene completion with both connected clients."""
import argparse,json,time
from pathlib import Path
import yaml
from PIL import Image,ImageDraw
from capture_inspect_audit import Rcon,client

def main():
 p=argparse.ArgumentParser();p.add_argument('--root',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--only',default='');p.add_argument('--all-variants',action='store_true');args=p.parse_args();root=args.root.resolve();out=args.output.resolve();out.mkdir(parents=True,exist_ok=True)
 repo=Path(__file__).resolve().parents[1];weapons=yaml.safe_load((repo/'src/main/resources/defaults/catalog/weapons.yml').read_text())['weapons'];profiles=yaml.safe_load((repo/'src/main/resources/defaults/inspect-profiles.yml').read_text())
 if args.only:weapons={id:weapons[id] for id in args.only.split(',')}
 rc=Rcon(root/'server');client(root,'client','camera|FIRST_PERSON');client(root,'client','hand|RIGHT');time.sleep(.2)
 evidence=[]
 motions=[(id,v,animation) for id in weapons for v,animation in enumerate(profiles['animation-pools'][id]) if args.all_variants or v==0]
 for id,v,animation in motions:
  stem=f'{id}_{v}' if args.all_variants else id
  duration=max(sum(k['ticks'] for k in g['keyframes']) for g in profiles['animations'][animation]['groups'].values())/20
  response=rc.command(f'mccasesdevcheck DevTester visual DevObserver {id} {v} play eye 1')
  if 'VISUAL READY' not in response:raise RuntimeError(f'{id}: {response}')
  start=time.monotonic();frames=[]
  for i in range(8):
   delay=duration*i/7+.12-(time.monotonic()-start)
   if delay>0:time.sleep(delay)
   pair=Image.new('RGB',(960,290),(30,34,40));d=ImageDraw.Draw(pair)
   for j,folder in enumerate(['client','observer']):
    file=out/f'{stem}_{i}_{folder}.png';client(root,folder,'shot|'+str(file));im=Image.open(file);im.thumbnail((480,270));pair.paste(im,(480*j,0));d.text((480*j+5,270),f'{id}/{v} · {folder} · {time.monotonic()-start:.2f}s',fill='white')
   frames.append(pair)
  delay=duration+.35-(time.monotonic()-start)
  if delay>0:time.sleep(delay)
  if rc.command('execute if entity @e[type=minecraft:item_display] run list').strip():raise RuntimeError('Display survived natural completion: '+id)
  frames[0].save(out/f'{stem}.gif',save_all=True,append_images=frames[1:],duration=max(80,int(duration*1000/7)),loop=0)
  strip=Image.new('RGB',(1920,580),(30,34,40))
  for i,im in enumerate(frames):im=im.resize((480,145));strip.paste(im,((i%4)*480,(i//4)*145))
  strip=strip.crop((0,0,1920,290));strip.save(out/f'{stem}-motion.png')
  evidence.append(dict(weapon=id,variant=v,animation=animation,seconds=duration,naturalCleanup=True));(out/'manifest.json').write_text(json.dumps(evidence,indent=2));print('PASS LIVE',id,v,flush=True)
 print('COMPLETE LIVE',len(evidence),flush=True)
if __name__=='__main__':main()
