"""Run release suites on the disposable Paper server and two Vanilla clients."""
import argparse
import json
import time
from pathlib import Path
from capture_inspect_audit import Rcon, client

SUITES = [
    ('commands', 'PASS ALL 155 COMMAND CHECKS'),
    ('full', 'PASS FULL AUDIT'),
    ('observer DevObserver', 'PASS TWO CLIENTS'),
    ('commerce DevObserver', 'PASS TWO-CLIENT COMMERCE'),
    ('gold', 'PASS TRADEUP RUNTIME'),
    ('tradein-ui', 'PASS TRADEIN UI'),
    ('choices', 'PASS CHOICE MENUS'),
    ('random-tradeup', 'PASS RANDOM TRADEUP'),
    ('guide', 'PASS CASE GUIDE'),
    ('nine', 'PASS QUEUE 9'),
    ('queue-controls', 'PASS QUEUE CONTROLS'),
    ('spam', 'PASS SPAM'),
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--suite', action='append', help='Optional suite names from SUITES')
    args = parser.parse_args()
    root, output = args.root.resolve(), args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    deadline = time.monotonic() + 60
    while True:
        try:
            rc = Rcon(root / 'server')
            online = rc.command('list')
            if 'DevTester' in online and 'DevObserver' in online:
                break
            rc.socket.close()
        except OSError:
            pass
        if time.monotonic() > deadline:
            raise RuntimeError('Both disposable clients must be online')
        time.sleep(.5)
    logs = [root / 'server.log', root / 'client.log']
    client(root, 'client', 'chat|FULL')
    initial_offsets = [log.stat().st_size for log in logs]
    results = []
    for suite, marker in SUITES:
        if args.suite and suite.split()[0] not in args.suite:
            continue
        offsets = [log.stat().st_size for log in logs]
        command = 'mccasesdevcheck DevTester ' + suite
        began = time.monotonic()
        client(root, 'client', 'command|' + command)
        response = ''
        while time.monotonic() - began < 120:
            segments = [log.read_bytes()[offset:].decode(errors='replace') for log, offset in zip(logs, offsets)]
            text = response + '\n' + '\n'.join(segments)
            # The command suite deliberately injects storage errors. Its expected
            # error references are valid; audit failures always carry these markers.
            failures = [line for line in text.splitlines() if 'CHECK FAILED' in line or 'audit failed' in line.lower() or 'Runtime checks failed' in line]
            if failures:
                (output / 'failure.log').write_text(text)
                raise RuntimeError(command + ': ' + '\n'.join(failures))
            if marker in text:
                passed = list(dict.fromkeys(line for line in text.splitlines() if 'PASS' in line))
                results.append(dict(command=command, seconds=round(time.monotonic()-began, 2), results=passed))
                (output / 'live-results.json').write_text(json.dumps(results, indent=2))
                print(command + ': ' + marker, flush=True)
                break
            time.sleep(.1)
        else:
            (output / 'failure.log').write_text(text)
            raise TimeoutError(command + ' did not produce ' + marker)
        time.sleep(.25)
    for log, offset, name in zip(logs, initial_offsets, ['live-server.log', 'live-client.log']):
        (output / name).write_text(log.read_bytes()[offset:].decode(errors='replace'))
    print('COMPLETE LIVE SUITES ' + str(len(results)), flush=True)


if __name__ == '__main__':
    main()
