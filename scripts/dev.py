#!/usr/bin/env python3
"""Isolated macOS Paper + Minecraft development session with optional persistent supervision.
No account tokens or global Minecraft settings are read or changed.
"""
import argparse, concurrent.futures, hashlib, json, os, platform, plistlib, re, shutil, signal, socket, subprocess, sys, time, uuid, urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
RUNTIME=ROOT/'.dev/runtime'
SERVER=RUNTIME/'server'
CLIENT=RUNTIME/'client'
STATE=RUNTIME/'session.json'
CONTROL=RUNTIME/'control.sock'
LABEL='dev.mccases.cleanup-'+hashlib.sha256(str(ROOT).encode()).hexdigest()[:10]
AGENT=Path.home()/'Library/LaunchAgents'/f'{LABEL}.plist'
GAME=Path.home()/'Library/Application Support/minecraft'
JAVA=Path.home()/'Library/Java/JavaVirtualMachines/temurin-25.0.2/Contents/Home/bin/java'
if not JAVA.exists():JAVA=Path(shutil.which('java') or 'java')

def fingerprint(pid):
 r=subprocess.run(['ps','-p',str(pid),'-o','lstart=','-o','comm='],capture_output=True,text=True)
 return r.stdout.strip() if r.returncode==0 else None

def request(command):
 with socket.socket(socket.AF_UNIX) as s:
  s.settimeout(90 if command.get('action')=='restart' else 5);s.connect(str(CONTROL));s.sendall(json.dumps(command).encode());s.shutdown(socket.SHUT_WR)
  return s.recv(65536).decode()

def write_state(state):
 temporary=STATE.with_suffix('.tmp');temporary.write_text(json.dumps(state));temporary.replace(STATE)

def download(url, path, checksum, algorithm='sha256'):
 path.parent.mkdir(parents=True,exist_ok=True)
 if path.exists() and hashlib.new(algorithm,path.read_bytes()).hexdigest()==checksum:return
 headers={'User-Agent':'MCCasesDev/1.0 (https://github.com/Plattnericus/CS2CasesPlugin)'}
 with urllib.request.urlopen(urllib.request.Request(url,headers=headers),timeout=60) as response:
  temp=path.with_suffix(path.suffix+'.download');temp.write_bytes(response.read())
 if hashlib.new(algorithm,temp.read_bytes()).hexdigest()!=checksum:temp.unlink();raise RuntimeError('Download checksum mismatch')
 temp.replace(path)

def prepare_assets(version):
 assets=RUNTIME/'assets';index=version['assetIndex']
 target=assets/'indexes'/f"{index['id']}.json"
 download(index['url'],target,index['sha1'],'sha1')
 pending=[]
 for entry in json.loads(target.read_text())['objects'].values():
  digest=entry['hash'];relative=Path('objects')/digest[:2]/digest
  path=assets/relative;existing=GAME/'assets'/relative
  if path.exists():continue
  path.parent.mkdir(parents=True,exist_ok=True)
  if existing.exists() and hashlib.sha1(existing.read_bytes()).hexdigest()==digest:path.symlink_to(existing)
  else:pending.append((f'https://resources.download.minecraft.net/{digest[:2]}/{digest}',path,digest))
 # Several logical assets may share the same object hash.
 pending=list({str(job[1]):job for job in pending}.values())
 print(f'Client assets: downloading {len(pending)} missing objects; reusing installed assets.',flush=True)
 with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
  list(pool.map(lambda job:download(*job,algorithm='sha1'),pending))

def prepare_client_preferences(client):
 options=client/'options.txt'
 if not options.exists():options.write_text('renderDistance:6\nsimulationDistance:5\nmaxFps:90\nonboardAccessibility:false\n')
 managed={'lang':'en_us','resourcePacks':'["vanilla","file/MCCases.zip"]','pauseOnLostFocus':'false','tutorialStep':'none'}
 lines=[line for line in options.read_text().splitlines() if line.split(':',1)[0] not in managed]
 options.write_text('\n'.join(lines+[f'{key}:{value}' for key,value in managed.items()])+'\n')
 # Remember pack acceptance for this disposable localhost connection only.
 import struct
 def string(value):
  encoded=value.encode();return struct.pack('>H',len(encoded))+encoded
 entry=b'\x08'+string('name')+string('MCCases Local')+b'\x08'+string('ip')+string('127.0.0.1:25565')+b'\x01'+string('acceptTextures')+b'\x01\x00'
 (client/'servers.dat').write_bytes(b'\x0a'+string('')+b'\x09'+string('servers')+b'\x0a'+struct.pack('>i',1)+entry+b'\x00')

def build_artifacts():
 match=re.search(r'^version\s*=\s*"([A-Za-z0-9._-]+)"', (ROOT/'build.gradle.kts').read_text(), re.MULTILINE)
 if not match:raise RuntimeError('Cannot identify the current Gradle release version')
 release=match.group(1)
 plugin=ROOT/f'build/libs/MCCases-{release}.jar'
 checks=ROOT/f'build/libs/MCCases-DevChecks-{release}.jar'
 pack=ROOT/f'build/distributions/MCCases-ResourcePack-Fusion-HD-{release}.zip'
 for artifact in (plugin,pack):
  if not artifact.is_file():raise FileNotFoundError(f'Build the current release first: {artifact}')
 return plugin,checks,pack

def prepare():
 plugin_build,checks,pack_build=build_artifacts()
 SERVER.mkdir(parents=True,exist_ok=True);CLIENT.mkdir(parents=True,exist_ok=True)
 download('https://fill-data.papermc.io/v1/objects/2224a0b2b6b096ff4c429ad926e97977213e4f633e90cb3a49b5eeb82f94bab0/paper-26.3-159.jar',SERVER/'paper.jar','2224a0b2b6b096ff4c429ad926e97977213e4f633e90cb3a49b5eeb82f94bab0')
 version=json.loads((GAME/'versions/26.3/26.3.json').read_text())
 prepare_assets(version)
 for lib in version['libraries']:
  artifact=lib.get('downloads',{}).get('artifact')
  if artifact and not (GAME/'libraries'/artifact['path']).exists():
   download(artifact['url'],RUNTIME/'libraries'/artifact['path'],artifact['sha1'],'sha1')
 plugins=SERVER/'plugins/MCCases';plugins.mkdir(parents=True,exist_ok=True)
 # The disposable server must test the current plugin and pack together, without old duplicate jars.
 for obsolete in (SERVER/'plugins').glob('MCCases-*.jar'):obsolete.unlink()
 shutil.copy2(plugin_build,SERVER/'plugins/MCCases.jar')
 if checks.exists():shutil.copy2(checks,SERVER/'plugins/MCCases-DevChecks.jar')
 for source in (ROOT/'src/main/resources/defaults').glob('messages_*.yml'):shutil.copy2(source,plugins/source.name)
 shutil.copy2(ROOT/'src/main/resources/defaults/inspect.yml',plugins/'inspect.yml')
 import yaml
 settings=yaml.safe_load((ROOT/'src/main/resources/defaults/config.yml').read_text())
 settings['resource-pack']['enabled']=True
 settings['resource-pack']['distribution'].update({'enabled':True,'bind-address':'127.0.0.1','port':8165,'public-url':'http://127.0.0.1:8165'})
 (plugins/'config.yml').write_text(yaml.safe_dump(settings,sort_keys=False,allow_unicode=True))
 properties=SERVER/'server.properties'
 rcon=[]
 if properties.exists():
  rcon=[line for line in properties.read_text().splitlines() if line.split('=',1)[0] in ('enable-rcon','rcon.port','rcon.password')]
 properties.write_text('''server-ip=127.0.0.1
server-port=25565
online-mode=false
white-list=false
enforce-secure-profile=false
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
level-name=dev-world
spawn-protection=0
gamemode=creative
difficulty=peaceful
view-distance=6
simulation-distance=4
max-players=4
motd=MCCases Local Development
pause-when-empty-seconds=-1
allow-flight=true
'''+''.join(line+'\n' for line in rcon))
 if not (SERVER/'eula.txt').exists():(SERVER/'eula.txt').write_text('eula=false\n')
 packs=CLIENT/'resourcepacks';packs.mkdir(exist_ok=True)
 shutil.copy2(pack_build,packs/'MCCases.zip')
 prepare_client_preferences(CLIENT)
 print('Prepared isolated server and client in',RUNTIME)

def client_command(username='DevTester'):
 if username not in ('DevTester','DevObserver'):raise ValueError('Unknown isolated test profile')
 client=CLIENT if username=='DevTester' else RUNTIME/'observer'
 client.mkdir(parents=True,exist_ok=True)
 if username=='DevObserver':
  (client/'resourcepacks').mkdir(exist_ok=True)
  shutil.copy2(CLIENT/'resourcepacks/MCCases.zip',client/'resourcepacks/MCCases.zip')
  if not (client/'options.txt').exists():(client/'options.txt').write_text('renderDistance:4\nmaxFps:30\nonboardAccessibility:false\n')
 prepare_client_preferences(client)
 version=json.loads((GAME/'versions/26.3/26.3.json').read_text())
 libraries=[]
 for lib in version['libraries']:
  allow=not lib.get('rules')
  for rule in lib.get('rules',[]):
   system=rule.get('os',{})
   architectures=('arm64','aarch64') if platform.machine()=='arm64' else ('x86_64',)
   matches=(not system.get('name') or system['name']=='osx') and (not system.get('arch') or system['arch'] in architectures)
   if matches:allow=rule['action']=='allow'
  if allow and 'artifact' in lib.get('downloads',{}):
   path=GAME/'libraries'/lib['downloads']['artifact']['path']
   if not path.exists():path=RUNTIME/'libraries'/lib['downloads']['artifact']['path']
   if not path.exists():raise RuntimeError(f'Missing installed client library: {path.name}')
   libraries.append(str(path))
 jar=GAME/'versions/26.3/26.3.jar'
 if not jar.exists():raise RuntimeError('Minecraft 26.3 client jar is not installed')
 libraries.append(str(jar));natives=client/'natives';natives.mkdir(exist_ok=True)
 ident=bytearray(hashlib.md5(('OfflinePlayer:'+username).encode()).digest());ident[6]=(ident[6]&15)|48;ident[8]=(ident[8]&63)|128
 capture=RUNTIME/'capture-harness.jar'
 capture_args=[f'-javaagent:{capture}={client}/capture'] if capture.is_file() else []
 return [str(JAVA),'-XstartOnFirstThread','-Xmx2G','--sun-misc-unsafe-memory-access=allow','--enable-native-access=ALL-UNNAMED',
  f'-Djava.library.path={natives}/java',f'-Djna.tmpdir={natives}/jna',f'-Dorg.lwjgl.system.SharedLibraryExtractPath={natives}/lwjgl',f'-Dio.netty.native.workdir={natives}/netty',
  '-Dminecraft.launcher.brand=MCCasesDev','-Dminecraft.launcher.version=1.0',*capture_args,'-cp',os.pathsep.join(libraries),version['mainClass'],
  '--username',username,'--version','26.3','--gameDir',str(client),'--assetsDir',str(RUNTIME/'assets'),'--assetIndex',version['assetIndex']['id'],
  '--uuid',str(uuid.UUID(bytes=bytes(ident))),'--accessToken','0','--clientId','MCCasesDev','--xuid','0','--versionType','release',
  '--width','1280','--height','720','--quickPlayMultiplayer','127.0.0.1:25565']

def install_cleanup(persistent=False):
 AGENT.parent.mkdir(parents=True,exist_ok=True)
 arguments=['start','--persistent'] if persistent else ['cleanup-stale']
 AGENT.write_bytes(plistlib.dumps({'Label':LABEL,'ProgramArguments':[sys.executable,str(Path(__file__).resolve()),*arguments],'RunAtLoad':True,'KeepAlive':False}))
 # The launch agent is picked up at the next login; no background polling job is installed.

def cleanup_stale():
 d={}
 if STATE.exists():
  try:d=json.loads(STATE.read_text())
  except (json.JSONDecodeError,UnicodeDecodeError):d={}
  if d.get('supervisor') and fingerprint(d['supervisor'])==d.get('fingerprint'):return
  for key in ['server','client','observer']:
   pid=d.get(key);identity=d.get(key+'Fingerprint')
   if pid and identity and fingerprint(pid)==identity:
    os.kill(pid,signal.SIGTERM)
    for _ in range(20):
     if fingerprint(pid)!=identity:break
     time.sleep(.25)
    if fingerprint(pid)==identity:os.kill(pid,signal.SIGKILL)
 if d.get('persistent'):
  CONTROL.unlink(missing_ok=True)
 else:
  if RUNTIME.exists():shutil.rmtree(RUNTIME)
  AGENT.unlink(missing_ok=True)

def supervise(app_pid,app_identity):
 persistent=app_pid==0
 RUNTIME.mkdir(parents=True,exist_ok=True)
 serverlog=(RUNTIME/'server.log').open('a');clientlog=(RUNTIME/'client.log').open('a');observerlog=(RUNTIME/'observer.log').open('a')
 log_start=(RUNTIME/'server.log').stat().st_size
 server=subprocess.Popen([str(JAVA),'-Xms512M','-Xmx2G','-jar',str(SERVER/'paper.jar'),'--nogui'],cwd=SERVER,stdin=subprocess.PIPE,stdout=serverlog,stderr=subprocess.STDOUT,text=True)
 client=None;observer=None;observer_requested=False;stop=False;ready=False
 state={'supervisor':os.getpid(),'fingerprint':fingerprint(os.getpid()),'server':server.pid,'serverFingerprint':fingerprint(server.pid),'app':app_pid,'appFingerprint':app_identity,'client':None,'persistent':persistent}
 write_state(state);CONTROL.unlink(missing_ok=True)
 control=socket.socket(socket.AF_UNIX);control.bind(str(CONTROL));os.chmod(CONTROL,0o600);control.listen(4);control.settimeout(1)
 def shutdown(signum=None,frame=None):
  nonlocal stop;stop=True
 signal.signal(signal.SIGTERM,shutdown);signal.signal(signal.SIGINT,shutdown)
 try:
  while not stop and (persistent or fingerprint(app_pid)==app_identity):
   if server.poll() is not None:
    print('Server exited unexpectedly; restarting the local session.',flush=True)
    for process in (client,observer):
     if process is not None and process.poll() is None:
      process.terminate()
      try:process.wait(timeout=10)
      except subprocess.TimeoutExpired:process.kill();process.wait()
    client=observer=None;ready=False;time.sleep(3)
    serverlog.flush();log_start=(RUNTIME/'server.log').stat().st_size
    server=subprocess.Popen([str(JAVA),'-Xms512M','-Xmx2G','-jar',str(SERVER/'paper.jar'),'--nogui'],cwd=SERVER,stdin=subprocess.PIPE,stdout=serverlog,stderr=subprocess.STDOUT,text=True)
    state.update(server=server.pid,serverFingerprint=fingerprint(server.pid),client=None,observer=None);write_state(state)
   if not ready and b'Done (' in (RUNTIME/'server.log').read_bytes()[log_start:]:
    ready=True
    for command in ['op DevTester','op DevObserver','gamerule minecraft:advance_time false','gamerule minecraft:advance_weather false','time set day','weather clear']:
     server.stdin.write(command+'\n')
    server.stdin.flush()
    client=subprocess.Popen(client_command(),cwd=CLIENT,stdin=subprocess.DEVNULL,stdout=clientlog,stderr=subprocess.STDOUT)
    state['client']=client.pid;state['clientFingerprint']=fingerprint(client.pid);write_state(state)
   if ready and client is not None and client.poll() is not None:
    time.sleep(3);client=subprocess.Popen(client_command(),cwd=CLIENT,stdin=subprocess.DEVNULL,stdout=clientlog,stderr=subprocess.STDOUT)
    state.update(client=client.pid,clientFingerprint=fingerprint(client.pid));write_state(state)
   if ready and observer_requested and (observer is None or observer.poll() is not None):
    observer=subprocess.Popen(client_command('DevObserver'),cwd=RUNTIME,stdin=subprocess.DEVNULL,stdout=observerlog,stderr=subprocess.STDOUT)
    state.update(observer=observer.pid,observerFingerprint=fingerprint(observer.pid));write_state(state)
   try:connection,_=control.accept()
   except socket.timeout:continue
   with connection:
    message=connection.recv(65536);data=json.loads(message)
    if data.get('action')=='stop':stop=True;result='Stopping and cleaning this dev session.'
    elif data.get('action')=='restart':
     if observer is not None and observer.poll() is None:
      observer.terminate();observer.wait(timeout=15)
     observer=None
     if client is not None and client.poll() is None:
      client.terminate();client.wait(timeout=15)
     server.stdin.write('stop\n');server.stdin.flush();server.wait(timeout=30)
     prepare()
     serverlog.flush();log_start=(RUNTIME/'server.log').stat().st_size
     server=subprocess.Popen([str(JAVA),'-Xms512M','-Xmx2G','-jar',str(SERVER/'paper.jar'),'--nogui'],cwd=SERVER,stdin=subprocess.PIPE,stdout=serverlog,stderr=subprocess.STDOUT,text=True)
     state['server']=server.pid;state['serverFingerprint']=fingerprint(server.pid);state['client']=None;state['observer']=None
     ready=False;write_state(state);result='Restarting with the current build.'
    elif data.get('action')=='observer':
     observer_requested=True;result='Starting the second isolated client.'
    elif data.get('action')=='console':
     command=data['command']
     if '\n' in command or '\r' in command:raise ValueError('one command at a time')
     server.stdin.write(command+'\n');server.stdin.flush();result='Command sent.'
    else:result=json.dumps({**state,'ready':ready,'serverRunning':server.poll() is None,'clientRunning':client is not None and client.poll() is None,'observerRunning':observer is not None and observer.poll() is None})
    connection.sendall(result.encode())
 finally:
  control.close()
  if server.poll() is None:
   try:server.stdin.write('stop\n');server.stdin.flush();server.wait(timeout=25)
   except (OSError,subprocess.TimeoutExpired):
    server.terminate()
    try:server.wait(timeout=10)
    except subprocess.TimeoutExpired:server.kill();server.wait()
  for process in (client,observer):
   if process is not None and process.poll() is None:
    process.terminate()
    try:process.wait(timeout=10)
    except subprocess.TimeoutExpired:process.kill();process.wait()
  serverlog.close();clientlog.close();observerlog.close()
  # Only this disposable runtime is deleted. Source, build outputs and the user's Minecraft remain.
  if not persistent and RUNTIME.exists():shutil.rmtree(RUNTIME)
  CONTROL.unlink(missing_ok=True)
  AGENT.unlink(missing_ok=True)

def main():
 p=argparse.ArgumentParser();p.add_argument('action',choices=['prepare','start','status','console','restart','observer','stop','cleanup-stale','supervise']);p.add_argument('--persistent',action='store_true',help='Keep the server and client running independently of the desktop app; preserve the dev world.');p.add_argument('arguments',nargs='*');a=p.parse_args()
 if a.action=='prepare':prepare()
 elif a.action=='start':
  if CONTROL.exists():
   try:print(request({'action':'status'}));return
   except (ConnectionError,OSError):cleanup_stale()
  if 'eula=true' not in (SERVER/'eula.txt').read_text():raise SystemExit('Minecraft EULA has not been accepted. Explicit user consent is required before eula=true.')
  app_pid=0 if a.persistent else int(a.arguments[0]);identity='' if a.persistent else fingerprint(app_pid)
  if not a.persistent and not identity:raise SystemExit('Watched application process not found')
  # Check client inputs before starting a long-running session.
  client_command();install_cleanup(a.persistent)
  with (RUNTIME/'supervisor.log').open('w') as out:
   process=subprocess.Popen([sys.executable,str(Path(__file__).resolve()),'supervise',str(app_pid),identity],start_new_session=True,stdin=subprocess.DEVNULL,stdout=out,stderr=subprocess.STDOUT)
  print('Dev supervisor started:',process.pid)
 elif a.action=='supervise':supervise(int(a.arguments[0]),a.arguments[1])
 elif a.action=='cleanup-stale':cleanup_stale()
 else:print(request({'action':a.action,'command':' '.join(a.arguments)}))
if __name__=='__main__':main()
