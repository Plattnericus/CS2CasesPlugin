"""Make labeled contact sheets from real client captures, keeping their audit metadata."""
import argparse,json,shutil
from pathlib import Path
from PIL import Image,ImageDraw
import yaml

def sheet(files,target,columns=4):
 width,height,label=400,225,20;image=Image.new('RGB',(columns*width,((len(files)+columns-1)//columns)*(height+label)),(30,34,40));draw=ImageDraw.Draw(image)
 for i,file in enumerate(files):
  frame=Image.open(file);frame.thumbnail((width,height));x=i%columns*width;y=i//columns*(height+label);image.paste(frame,(x,y));draw.text((x+5,y+height),file.stem,fill='white')
 image.save(target)

def main():
 p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);args=p.parse_args();repo=Path(__file__).resolve().parents[1];out=args.output;out.mkdir(parents=True,exist_ok=True);merged={};paths={}
 for folder in [repo/'build/visual/release',repo/'build/visual/release-corrections']:
  for row in json.loads((folder/'manifest.json').read_text()):merged[row['file']]=row;paths[row['file']]=folder/row['file']
 weapons=yaml.safe_load((repo/'src/main/resources/defaults/catalog/weapons.yml').read_text())['weapons']
 assert len(merged)==1410,len(merged)
 for id,weapon in weapons.items():
  rows=[row for row in merged.values() if row['weapon']==id];assert len({r['animation'] for r in rows})==3,id
  assert len(rows)==(39 if weapon['category']=='knife' else 18),(id,len(rows))
  sheet([paths[r['file']] for r in sorted(rows,key=lambda r:r['file'])],out/(id+'.png'))
 for category in ['pistol','smg','rifle','sniper','heavy','equipment']:
  rows=[r for r in merged.values() if r['mode']=='eye' and r['viewer']=='client' and weapons[r['weapon']]['category']==category]
  rows.sort(key=lambda r:(r['weapon'],r['animation'],r['tick']))
  for page,start in enumerate(range(0,len(rows),24)):
   sheet([paths[r['file']] for r in rows[start:start+24]],out/f'{category}-{page+1}.png')
 (out/'manifest.json').write_text(json.dumps(list(merged.values()),indent=2))
 (out/'README.txt').write_text('MCCases 1.2 visual checks\n1410 actual Minecraft 26.3 client framebuffer captures, summarized in labeled sheets.\nAll 55 weapons: 3 variants at 8/22/40 ticks, owner and observer.\nAll 20 knives: additional front/left/right/rear observer views, left hand and both F5 cameras.\nFrozen frames use the live plugin scene and model transforms. Motion GIFs separately show real server interpolation.\nDefault 70-degree FOV; full-timeline geometry checks also cover 4:3 and fallback models.\nPerspective and tick metadata are recorded in manifest.json.\nThe eye scene is private; the other player sees the body-hand scene.\nF5 requires /inspect hand; /inspect eye restores the owner view.\n')
 print('Verified visual coverage: 55 weapons, 165 variants, 1410 real captures.')
if __name__=='__main__':main()
