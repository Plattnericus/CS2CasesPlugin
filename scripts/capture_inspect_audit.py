"""Capture actual isolated Minecraft clients through their framebuffer harness and local RCON."""
import argparse,json,socket,struct,time
from pathlib import Path
import yaml

class Rcon:
 def __init__(self, root):
  props=dict(line.split('=',1) for line in (root/'server.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
  self.socket=socket.create_connection(('127.0.0.1',int(props['rcon.port'])),5);self.counter=0
  self.send(3,props['rcon.password'])
 def exact(self,n):
  out=b''
  while len(out)<n:
   piece=self.socket.recv(n-len(out))
   if not piece: raise RuntimeError('RCON closed')
   out+=piece
  return out
 def send(self,kind,text):
  self.counter+=1;payload=struct.pack('<ii',self.counter,kind)+text.encode()+b'\0\0';self.socket.sendall(struct.pack('<i',len(payload))+payload)
  length=struct.unpack('<i',self.exact(4))[0];packet=self.exact(length);id,typ=struct.unpack('<ii',packet[:8])
  if id<0: raise RuntimeError('RCON authentication rejected')
  return packet[8:-2].decode()
 def command(self,text): return self.send(2,text)

def client(root,folder,text):
 directory=root/folder/'capture';directory.mkdir(exist_ok=True)
 for f in ['done.txt','error.txt']:
  (directory/f).unlink(missing_ok=True)
 tmp=directory/'next.txt';tmp.write_text(text);tmp.replace(directory/'command.txt')
 deadline=time.monotonic()+12
 while time.monotonic()<deadline:
  if (directory/'error.txt').exists(): raise RuntimeError((directory/'error.txt').read_text())
  if (directory/'done.txt').exists(): return
  time.sleep(.025)
 raise TimeoutError(f'{folder}: {text}')

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--root',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);parser.add_argument('--only',default='');args=parser.parse_args()
 root=args.root.resolve();out=args.output.resolve();out.mkdir(parents=True,exist_ok=True);repo=Path(__file__).resolve().parents[1]
 weapons=yaml.safe_load((repo/'src/main/resources/defaults/catalog/weapons.yml').read_text())['weapons'];profiles=yaml.safe_load((repo/'src/main/resources/defaults/inspect-profiles.yml').read_text())
 if args.only: weapons={id:weapons[id] for id in args.only.split(',')}
 rcon=Rcon(root/'server');manifest=[]
 for folder in ['client','observer']:
  client(root,folder,'camera|FIRST_PERSON');client(root,folder,'chat|HIDDEN');client(root,folder,'hand|RIGHT');client(root,folder,'fov|70')
 def shot(folder,name,data):
  file=out/(name+'.png');client(root,folder,'shot|'+str(file));manifest.append(dict(data,viewer=folder,file=file.name))
 def frame(id,v,tick,mode,angle):
  response=rcon.command(f'mccasesdevcheck DevTester visual DevObserver {id} {v} {tick} {mode} {angle}')
  if 'VISUAL READY' not in response: raise RuntimeError(f'{id}/{v} at {tick}: {response}')
  time.sleep(.45)
 for id,weapon in weapons.items():
  for v,animation in enumerate(profiles['animation-pools'][id]):
   for tick in [8,22,40]:
    frame(id,v,tick,'eye',0);data=dict(weapon=id,animation=animation,tick=tick,mode='eye',hand='right',angle=0)
    shot('client',f'{id}_{v}_{tick}_eye',data);shot('observer',f'{id}_{v}_{tick}_front',data)
   if weapon['category'] in {'knife','glove'}:
    for angle in [1,2,3]:
     frame(id,v,22,'eye',angle);data=dict(weapon=id,animation=animation,tick=22,mode='eye',hand='right',angle=angle)
     shot('observer',f'{id}_{v}_side{angle}',data)
    client(root,'client','hand|LEFT');time.sleep(.15)
    frame(id,v,22,'eye',0);data=dict(weapon=id,animation=animation,tick=22,mode='eye',hand='left',angle=0)
    shot('client',f'{id}_{v}_left',data);shot('observer',f'{id}_{v}_left_front',data)
    frame(id,v,22,'hand',1)
    for camera,suffix in [('THIRD_PERSON_BACK','f5_back'),('THIRD_PERSON_FRONT','f5_front')]:
     client(root,'client','camera|'+camera);time.sleep(.65);shot('client',f'{id}_{v}_{suffix}',dict(data,mode='hand',camera=camera,angle=1))
    client(root,'client','camera|FIRST_PERSON');client(root,'client','hand|RIGHT');time.sleep(.15)
  (out/'manifest.json').write_text(json.dumps(manifest,indent=2));print(f'CAPTURED {id}: {len(manifest)} frames',flush=True)
 rcon.command('mccasesdevcheck DevTester visual DevObserver stop');print(f'COMPLETE {len(manifest)} real frames',flush=True)

if __name__=='__main__': main()
