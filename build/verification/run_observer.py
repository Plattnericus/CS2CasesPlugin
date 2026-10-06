"""Short-lived second vanilla client for the two-player locale/visibility audit."""
import hashlib, importlib.util, json, shutil, signal, subprocess, time, uuid
from pathlib import Path

root = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('mccases_dev', root / 'scripts/dev.py')
dev = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dev)
state = json.loads(dev.STATE.read_text())
game = dev.RUNTIME / 'observer-client'
stop_file = dev.RUNTIME / 'observer.stop'
stop_file.unlink(missing_ok=True)
assert not game.exists(), 'Observer directory already exists'
game.mkdir()
(game / 'resourcepacks').mkdir()
(game / 'natives').mkdir()
options = dev.CLIENT.joinpath('options.txt').read_text().splitlines()
values = {'lang': 'fr_fr', 'maxFps': '20', 'renderDistance': '4', 'simulationDistance': '4', 'onboardAccessibility': 'false'}
(game / 'options.txt').write_text('\n'.join(line for line in options if line.split(':', 1)[0] not in values)
                                + '\n' + '\n'.join(k + ':' + v for k, v in values.items()) + '\n')
shutil.copy2(root / 'build/distributions/MCCases-ResourcePack-1.0.0.zip', game / 'resourcepacks/MCCases.zip')
if (dev.CLIENT / 'servers.dat').exists(): shutil.copy2(dev.CLIENT / 'servers.dat', game / 'servers.dat')
command = dev.client_command()
command = [argument.replace(str(dev.CLIENT / 'natives'), str(game / 'natives')) for argument in command]
ident = bytearray(hashlib.md5(b'OfflinePlayer:DevObserver').digest())
ident[6] = (ident[6] & 15) | 48
ident[8] = (ident[8] & 63) | 128
for flag, value in [('--username', 'DevObserver'), ('--uuid', str(uuid.UUID(bytes=bytes(ident)))), ('--gameDir', str(game))]:
    command[command.index(flag) + 1] = value
command[command.index('-Xmx2G')] = '-Xmx1G'
stopping = False
def stop(*unused):
    global stopping
    stopping = True
signal.signal(signal.SIGTERM, stop)
signal.signal(signal.SIGINT, stop)
client = None
log = root / 'build/verification/observer-client.log'
try:
    with log.open('w') as output:
        client = subprocess.Popen(command, cwd=game, stdin=subprocess.DEVNULL, stdout=output, stderr=subprocess.STDOUT)
        print('French observer client started:', client.pid, flush=True)
        deadline = time.monotonic() + 150
        while (client.poll() is None and not stopping and not stop_file.exists() and time.monotonic() < deadline
               and dev.fingerprint(state['app']) == state['appFingerprint']
               and dev.fingerprint(state['server']) == state['serverFingerprint']):
            time.sleep(1)
finally:
    if client is not None and client.poll() is None:
        client.terminate()
        try: client.wait(timeout=10)
        except subprocess.TimeoutExpired: client.kill(); client.wait()
    if game.exists(): shutil.rmtree(game)
    stop_file.unlink(missing_ok=True)
    print('Observer client stopped and its isolated files removed.', flush=True)
