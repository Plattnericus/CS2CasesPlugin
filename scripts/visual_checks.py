#!/usr/bin/env python3
"""Real-client visual checks for held skins, use poses and inspects on the local dev session.

Requires `scripts/dev.py` with the capture harness, RCON and the MCCases-DevChecks plugin. Every
weapon is equipped through `mccasesdevcheck DevTester held <weapon> [bow|crossbow]` and captured
through Minecraft's own framebuffer. Run only against the isolated dev session.
"""
import argparse, json, socket, struct, time
from pathlib import Path
import yaml

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / '.dev/runtime'


class Rcon:
    def __init__(self, server):
        props = dict(line.split('=', 1) for line in (server / 'server.properties').read_text().splitlines()
                     if '=' in line and not line.startswith('#'))
        self.socket = socket.create_connection(('127.0.0.1', int(props['rcon.port'])), 5)
        self.counter = 0
        self.send(3, props['rcon.password'])

    def exact(self, n):
        out = b''
        while len(out) < n:
            piece = self.socket.recv(n - len(out))
            if not piece:
                raise RuntimeError('RCON closed')
            out += piece
        return out

    def send(self, kind, text):
        self.counter += 1
        payload = struct.pack('<ii', self.counter, kind) + text.encode() + b'\0\0'
        self.socket.sendall(struct.pack('<i', len(payload)) + payload)
        length = struct.unpack('<i', self.exact(4))[0]
        packet = self.exact(length)
        if struct.unpack('<ii', packet[:8])[0] < 0:
            raise RuntimeError('RCON authentication rejected')
        return packet[8:-2].decode()

    def command(self, text):
        return self.send(2, text)


def client(text, folder='client', timeout=60):
    directory = RUNTIME / folder / 'capture'
    directory.mkdir(parents=True, exist_ok=True)
    for name in ('done.txt', 'error.txt'):
        (directory / name).unlink(missing_ok=True)
    temporary = directory / 'next.txt'
    temporary.write_text(text)
    temporary.replace(directory / 'command.txt')
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if (directory / 'error.txt').exists():
            raise RuntimeError((directory / 'error.txt').read_text())
        if (directory / 'done.txt').exists():
            return
        time.sleep(.025)
    raise TimeoutError(f'{folder}: {text}')


def hold(rcon, weapon, slot):
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        response = rcon.command(f'mccasesdevcheck DevTester held {weapon} {slot}')
        if 'VISUAL READY' in response:
            return response.split()[3]
        if 'VISUAL UNKNOWN' in response:
            return None
        if 'VISUAL PENDING' not in response:
            raise RuntimeError(response)
        time.sleep(.4)
    raise TimeoutError(f'fixture {weapon}')


def family(weapon, category):
    # Mirrors HeldRig.family for choosing the pool to capture.
    if weapon in ('karambit', 'talon'): return 'reverse'
    if weapon == 'shadow_daggers': return 'daggers'
    if category == 'knife': return 'butterfly' if weapon == 'butterfly' else 'knife'
    if weapon in ('deagle', 'r8_revolver'): return 'revolver'
    if category in ('pistol', 'equipment'): return 'pistol'
    return {'smg': 'smg', 'sniper': 'sniper', 'heavy': 'heavy'}.get(category, 'rifle')


def sheet(files, output, columns=3, width=640):
    from PIL import Image, ImageDraw
    if not files:
        return
    height = width * 9 // 16
    rows = (len(files) + columns - 1) // columns
    image = Image.new('RGB', (columns * width, rows * height), (24, 26, 30))
    draw = ImageDraw.Draw(image)
    for index, (label, path) in enumerate(files):
        tile = Image.open(path).convert('RGB').resize((width, height))
        x, y = index % columns * width, index // columns * height
        image.paste(tile, (x, y))
        draw.rectangle([x, y, x + 8 + 7 * len(label), y + 16], fill=(0, 0, 0))
        draw.text((x + 4, y + 3), label, fill=(255, 255, 255))
    image.save(output)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['held', 'inspect', 'transitions', 'use', 'third', 'left', 'others', 'cases'])
    parser.add_argument('--only', default='')
    parser.add_argument('--output', type=Path, default=RUNTIME / 'evidence/visual')
    parser.add_argument('--all-animations', action='store_true', help='inspect: play every animation of the pool')
    parser.add_argument('--frames', type=int, default=3, help='others: frames per inspect and camera')
    parser.add_argument('--cameras', default='THIRD_PERSON_BACK,THIRD_PERSON_FRONT', help='others: owner cameras to capture')
    args = parser.parse_args()
    inspects = yaml.safe_load((ROOT / 'src/main/resources/defaults/inspect.yml').read_text())
    pools = inspects.get('view-pools', {})
    durations = {name: max(sum(k['ticks'] for k in g['keyframes']) for g in a['groups'].values())
                 for name, a in inspects.get('view-animations', {}).items()}
    weapons = yaml.safe_load((ROOT / 'src/main/resources/defaults/catalog/weapons.yml').read_text())['weapons']
    ids = args.only.split(',') if args.only else [w for w, d in weapons.items() if d['category'] != 'glove']
    out = args.output / args.mode
    out.mkdir(parents=True, exist_ok=True)
    rcon = Rcon(RUNTIME / 'server')
    client('closescreen'); client('chat|HIDDEN'); client('fov|70'); client('camera|FIRST_PERSON'); client('hand|RIGHT')
    rcon.command('gamemode survival DevTester')
    if args.mode == 'others':
        # The observer stands in front of the tester's right side, looking at the hand.
        rcon.command('tp DevTester 0.5 -60 0.5 0 0')
        rcon.command('tp DevObserver 2.0 -60 3.4 152 14')
        client('closescreen', 'observer'); client('chat|HIDDEN', 'observer'); client('fov|70', 'observer')
        client('camera|FIRST_PERSON', 'observer')
    client('look|0,4')
    manifest, files = [], []

    def shot(label, folder='client'):
        path = out / f'{label}.png'
        client('shot|' + str(path), folder)
        manifest.append({'label': label, 'file': path.name})
        files.append((label, path))

    if args.mode == 'cases':
        # Test openings consume nothing; the second client watches the public reel.
        rcon.command('tp DevTester 0.5 -60 0.5 0 0')
        rcon.command('tp DevObserver 4.5 -60 -2.5 45 10')
        client('closescreen', 'observer'); client('chat|HIDDEN', 'observer')
        cases = args.only.split(',') if args.only else ['kilowatt_case', 'chroma_case', 'csgo_weapon_case']
        for case in cases:
            client('command|cases'); time.sleep(1.2); shot(f'{case}_menu'); client('closescreen'); time.sleep(.3)
            client('look|0,4')
            client('command|csadmin testcase ' + case)
            for index, delay in enumerate((.6, 1.4, 1.6, 1.8, 2.2)):
                time.sleep(delay); shot(f'{case}_reel_{index}')
                if index in (1, 4): shot(f'{case}_observer_{index}', 'observer')
            time.sleep(5)
            print('captured', case, flush=True)
        (out / 'manifest.json').write_text(json.dumps(manifest, indent=2))
        sheet(files, out / 'sheet.png')
        print('COMPLETE', args.mode, len(files), 'frames ->', out)
        return
    for weapon in ids:
        knife = weapons[weapon]['category'] == 'knife'
        if hold(rcon, weapon, 'bow') is None:
            print('no catalog skin for', weapon, flush=True)
            continue
        if args.mode in ('held', 'third', 'left'):
            if args.mode == 'left':
                client('hand|LEFT')
            hold(rcon, weapon, 'bow')
            client('select|1'); time.sleep(.15); client('select|0'); client('closescreen')
            time.sleep(1.0)
            if args.mode == 'third':
                for camera in ('THIRD_PERSON_BACK', 'THIRD_PERSON_FRONT'):
                    client('camera|' + camera); time.sleep(.5); shot(f'{weapon}_{camera.lower()}')
                client('camera|FIRST_PERSON')
            else:
                shot(f'{weapon}_{args.mode}')
            if args.mode == 'left':
                client('hand|RIGHT')
        elif args.mode == 'inspect':
            hold(rcon, weapon, 'bow')
            client('select|1'); time.sleep(.15); client('select|0'); client('closescreen'); time.sleep(.9)
            animations = pools.get(weapon) or pools.get(family(weapon, weapons[weapon]['category'])) or [None]
            for animation in animations if args.all_animations else animations[:1]:
                if animation:
                    response = rcon.command(f'mccasesdevcheck DevTester inspect {animation}')
                    if 'VISUAL INSPECT' not in response:
                        raise RuntimeError(response)
                else:
                    client('press|keySwapOffhand')
                for index, delay in enumerate((.35, .5, .5, .5, .6, .6)):
                    time.sleep(delay); shot(f'{weapon}_{animation or "inspect"}_{index}')
                time.sleep(1.6)
        elif args.mode == 'transitions':
            # Back-to-back frames at the start and the end: the hold must hand over to the scene and
            # back without the item dipping, blinking or showing twice.
            hold(rcon, weapon, 'bow')
            client('select|1'); time.sleep(.15); client('select|0'); client('closescreen'); time.sleep(.9)
            animation = (pools.get(weapon) or pools.get(family(weapon, weapons[weapon]['category'])))[0]
            shot(f'{weapon}_hold')
            started = time.monotonic()
            response = rcon.command(f'mccasesdevcheck DevTester inspect {animation}')
            if 'VISUAL INSPECT' not in response:
                raise RuntimeError(response)
            for index in range(3):
                shot(f'{weapon}_start_{index}')
            end = started + durations[animation] / 20
            time.sleep(max(0, end - .3 - time.monotonic()))
            for index in range(8):
                shot(f'{weapon}_end_{index}')
            time.sleep(1.2)
        elif args.mode == 'others':
            hold(rcon, weapon, 'bow')
            client('select|1'); time.sleep(.15); client('select|0'); client('closescreen'); time.sleep(1.0)
            shot(f'{weapon}_observer_idle', 'observer')
            pool = pools.get(weapon) or pools.get(family(weapon, weapons[weapon]['category'])) or [None]
            for animation in pool if args.all_animations else pool[:1]:
                for camera in args.cameras.split(','):
                    client('camera|' + camera); time.sleep(.3)
                    response = rcon.command(f'mccasesdevcheck DevTester inspect {animation}')
                    if 'VISUAL INSPECT' not in response:
                        raise RuntimeError(response)
                    gap = max(.2, durations[animation] / 20 / (args.frames + 1))
                    label = f'{weapon}_{animation}' if args.all_animations else weapon
                    for index in range(args.frames):
                        time.sleep(gap)
                        shot(f'{label}_{camera.lower()}_{index}')
                        if camera == args.cameras.split(',')[0]:
                            shot(f'{label}_observer_inspect_{index}', 'observer')
                    time.sleep(max(0, durations[animation] / 20 - gap * args.frames) + 1.0)
            client('camera|FIRST_PERSON')
        elif args.mode == 'use' and not knife:
            hold(rcon, weapon, 'bow')
            client('select|1'); time.sleep(.15); client('select|0'); client('closescreen'); time.sleep(.9)
            client('hold|keyUse,true'); time.sleep(1.3); shot(f'{weapon}_bow_drawn'); client('hold|keyUse,false'); time.sleep(.6)
            hold(rcon, weapon, 'crossbow')
            client('select|1'); time.sleep(.15); client('select|0'); client('closescreen'); time.sleep(.9)
            client('hold|keyUse,true'); time.sleep(.45); shot(f'{weapon}_crossbow_charging'); time.sleep(1.2)
            client('hold|keyUse,false'); time.sleep(.6); shot(f'{weapon}_crossbow_loaded')
            client('use'); time.sleep(.6)
        print('captured', weapon, flush=True)
    (out / 'manifest.json').write_text(json.dumps(manifest, indent=2))
    sheet(files, out / 'sheet.png')
    print('COMPLETE', args.mode, len(files), 'frames ->', out)


if __name__ == '__main__':
    main()
