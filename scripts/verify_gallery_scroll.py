"""Verify actual client scroll packets, server hotbar state and unchanged gallery pages."""
import argparse, json, math, re, sqlite3, time
from pathlib import Path
import yaml
from PIL import Image, ImageDraw
from capture_inspect_audit import Rcon, client

def main():
    p = argparse.ArgumentParser(); p.add_argument('--root', type=Path, required=True); p.add_argument('--output', type=Path, required=True)
    a = p.parse_args(); root = a.root.resolve(); out = a.output.resolve(); out.mkdir(parents=True, exist_ok=True)
    rc = Rcon(root/'server'); log = root/'server.log'; checks = []
    def check(command, marker):
        start = log.stat().st_size; rc.command(command); deadline = time.monotonic()+30
        while time.monotonic()<deadline:
            text = log.read_bytes()[start:].decode(errors='replace')
            if 'ERROR' in text or 'SEVERE' in text: raise RuntimeError(text)
            if marker in text:
                result = next(line for line in text.splitlines() if marker in line)
                checks.append(result); print(result, flush=True); return
            time.sleep(.1)
        raise TimeoutError(command)
    config = yaml.safe_load((root/'server/plugins/MCCases/config.yml').read_text())
    storage = config['storage']; database = root/'server/plugins/MCCases'/storage['sqlite-file']
    table = '"'+(storage['table-prefix']+'skins').replace('"','""')+'"'
    rc.command('csadmin giveskin DevTester butterfly_doppler'); time.sleep(.5)
    with sqlite3.connect(database) as db:
        instance = db.execute('SELECT instance_id FROM '+table+" WHERE skin_id='butterfly_doppler' AND status='OWNED' ORDER BY created_at DESC LIMIT 1").fetchone()[0]
    rc.command('csadmin equip DevTester '+instance); time.sleep(.3)
    check('mccasesdevcheck DevTester', 'PASS INVENTORY INPUT')
    check('mccasesdevcheck DevTester inventory', 'PASS INVENTORY MODES')
    check('mccasesdevcheck DevTester tradein-ui', 'PASS TRADEIN UI')
    # Keep enough persisted gallery cards to make unintended wheel paging observable.
    skins = ['ak47_asiimov','butterfly_doppler','awp_chrome_cannon','sport_gloves_vice']
    for i in range(40): rc.command('csadmin giveskin DevTester '+skins[i%len(skins)])
    time.sleep(1)
    rc.command('fill -20 99 -20 20 99 20 minecraft:stone'); rc.command('tp DevTester 0.5 100 0.5 0 0'); rc.command('tp DevObserver 20 100 20')
    materials = ['bow','crossbow','diamond','apple','stick','emerald','gold_ingot','iron_ingot','paper']
    for slot, item in enumerate(materials): rc.command(f'item replace entity DevTester hotbar.{slot} with minecraft:{item}')
    client(root,'client','camera|FIRST_PERSON'); client(root,'client','hand|RIGHT'); client(root,'client','chat|HIDDEN'); client(root,'client','command|inventory'); time.sleep(1)
    def state():
        reply = rc.command('mccasesdevcheck DevTester gallery-state')
        values = dict(re.findall(r'(\w+)=(\w+)',reply)); assert values.get('open')=='true', reply
        return values
    initial = state(); assert int(initial['entities'])>40, initial
    records = []; screenshots = []
    for sneak in [False, True]:
        client(root,'client','sneak|'+str(sneak).lower()); time.sleep(.4)
        for hover in [True, False]:
            cfg = config['skin-inventory']; distance = cfg['distance']; x = cfg['spacing']/2
            yaw = -math.degrees(math.atan2(x,distance)) if hover else 85
            dy = .209 + (.35 if sneak else 0)
            pitch = -math.degrees(math.atan2(dy,math.hypot(x,distance))) if hover else 0
            client(root,'client',f'look|{yaw},{pitch}'); time.sleep(.6)
            baseline = state(); assert baseline['hovered']==str(hover).lower(), baseline
            assert baseline['sneak']==str(sneak).lower(), baseline
            seen = set()
            for delta in [-1,1]:
                for step in range(12):
                    previous = int(state()['slot']); client(root,'client','scroll|'+str(delta)); time.sleep(.15)
                    actual = state(); expected = (previous-delta)%9
                    assert int(actual['slot'])==expected, (previous,delta,expected,actual)
                    assert actual['page']==baseline['page'], ('wheel changed page',actual,baseline)
                    assert actual['entities']==baseline['entities'], ('wheel rebuilt wall',actual,baseline)
                    capture = out/'client-state.txt'; client(root,'client','state|'+str(capture))
                    native_slot = int(re.search(r'slot=(\d+)',capture.read_text()).group(1))
                    assert native_slot==expected, ('client/server slot mismatch',native_slot,actual)
                    seen.add(expected); records.append(dict(sneak=sneak,hover=hover,delta=delta,slot=expected,page=int(actual['page'])))
                    if delta==-1 and step in [0,4,8]:
                        shot = out/f'hotbar_{"sneak" if sneak else "standing"}_{"hover" if hover else "away"}_{expected+1}.png'
                        client(root,'client','shot|'+str(shot)); screenshots.append(shot)
            assert seen==set(range(9)), seen
            print('PASS NATIVE WHEEL:', 'sneaking' if sneak else 'standing', 'over card' if hover else 'away', 'all nine slots, both directions, wrap, unchanged page',flush=True)
    client(root,'client','sneak|false'); time.sleep(.4)
    # A real client use action on the next-page Interaction must still browse the collection.
    cfg = config['skin-inventory']; y = (cfg['rows']-1)/2*(cfg['item-scale']+.3)+.2-cfg['rows']*(cfg['item-scale']+.3)+.05
    x = 3*min(cfg['spacing'],.42); distance = cfg['distance']; dy = y
    client(root,'client',f'look|{math.degrees(math.atan2(x,distance))},{-math.degrees(math.atan2(dy,math.hypot(x,distance)))}'); time.sleep(.6)
    before = state(); assert before['hovered']=='true', before
    client(root,'client','use'); time.sleep(.7); after = state()
    assert int(after['page'])==int(before['page'])+1, ('next button did not change page',before,after)
    assert after['slot']==before['slot'], ('page button changed held slot',before,after)
    shot = out/'next-page-button.png'; client(root,'client','shot|'+str(shot)); screenshots.append(shot)
    sheet = Image.new('RGB',(1280,((len(screenshots)+1)//2)*380),(30,34,40)); draw = ImageDraw.Draw(sheet)
    for i,path in enumerate(screenshots):
        frame = Image.open(path); frame.thumbnail((640,360)); x=i%2*640; y=i//2*380; sheet.paste(frame,(x,y)); draw.text((x+6,y+362),path.stem,fill='white')
    sheet.save(out/'hotbar-scroll-checks.png')
    (out/'results.json').write_text(json.dumps(dict(paper_checks=checks,native_packets=records,next_button=dict(before=before,after=after)),indent=2))
    print('COMPLETE:',len(records),'actual client wheel callbacks, matching client/server slots and working next-page button.',flush=True)

if __name__=='__main__': main()
