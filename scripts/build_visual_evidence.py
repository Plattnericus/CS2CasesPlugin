"""Make labeled contact sheets from real client captures, keeping their audit metadata."""
import argparse,json
from pathlib import Path
from PIL import Image,ImageDraw
import yaml

def sheet(files,target,columns=4):
 width,height,label=400,225,20;image=Image.new('RGB',(columns*width,((len(files)+columns-1)//columns)*(height+label)),(30,34,40));draw=ImageDraw.Draw(image)
 for i,file in enumerate(files):
  frame=Image.open(file);frame.thumbnail((width,height));x=i%columns*width;y=i//columns*(height+label);image.paste(frame,(x,y));draw.text((x+5,y+height),file.stem,fill='white')
 image.save(target)

def main():
 p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);p.add_argument('--captures',type=Path,action='append',required=True);args=p.parse_args();repo=Path(__file__).resolve().parents[1];out=args.output;out.mkdir(parents=True,exist_ok=True);merged={};paths={}
 for folder in args.captures:
  for row in json.loads((folder/'manifest.json').read_text()):merged[row['file']]=row;paths[row['file']]=folder/row['file']
 weapons=yaml.safe_load((repo/'src/main/resources/defaults/catalog/weapons.yml').read_text())['weapons']
 profiles=yaml.safe_load((repo/'src/main/resources/defaults/inspect-profiles.yml').read_text())['animation-pools']
 expected=sum(len(profiles[id])*(13 if w['category'] in {'knife','glove'} else 6) for id,w in weapons.items())
 assert len(merged)==expected,(len(merged),expected)
 for id,weapon in weapons.items():
  rows=[row for row in merged.values() if row['weapon']==id];assert {r['animation'] for r in rows}==set(profiles[id]),id
  assert len(rows)==len(profiles[id])*(13 if weapon['category'] in {'knife','glove'} else 6),(id,len(rows))
  sheet([paths[r['file']] for r in sorted(rows,key=lambda r:r['file'])],out/(id+'.png'))
 for category in ['pistol','smg','rifle','sniper','heavy','equipment','knife','glove']:
  rows=[r for r in merged.values() if r['mode']=='eye' and r['viewer']=='client' and weapons[r['weapon']]['category']==category]
  rows.sort(key=lambda r:(r['weapon'],r['animation'],r['tick']))
  for page,start in enumerate(range(0,len(rows),24)):
   sheet([paths[r['file']] for r in rows[start:start+24]],out/f'{category}-{page+1}.png')
 (out/'manifest.json').write_text(json.dumps(list(merged.values()),indent=2))
 (out/'README.txt').write_text(f'MCCases visual checks\n{len(merged)} actual Minecraft 26.3 client framebuffer captures, summarized in labeled sheets.\nAll {len(weapons)} weapon/knife/glove types: all {sum(map(len,profiles.values()))} variants at 8/22/40 ticks, owner and observer.\nKnives and gloves: additional front/left/right/rear observer views, left hand and both F5 cameras.\nFrozen frames use the live plugin scene and model transforms. Motion GIFs separately show real server interpolation.\n70-degree FOV; full-timeline geometry checks also cover 4:3 and fallback models.\nPerspective and tick metadata are recorded in manifest.json.\nThe eye scene is private; the other player sees the body-hand scene.\nF5 requires /inspect hand; /inspect eye restores the owner view.\n')
 print(f'Verified visual coverage: {len(weapons)} types, {sum(map(len,profiles.values()))} variants, {len(merged)} real captures.')
if __name__=='__main__':main()
