"""Compatibility CLI: use the same Java Fusion merge as normal builds and server exports."""
import argparse, subprocess
from pathlib import Path

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, required=True)
    parser.add_argument('--current', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.resolve() in {args.base.resolve(), args.current.resolve()}:
        parser.error('Write to a new pack; preserve both input archives')
    for source in (args.base, args.current):
        if not source.is_file(): parser.error('Missing input pack: ' + str(source))
    root = Path(__file__).resolve().parents[1]
    subprocess.run(['bash', str(root/'gradlew'), 'resourcePack', '--console=plain',
                    '-PfusionBasePack='+str(args.base.resolve()),
                    '-PfusionCurrentPack='+str(args.current.resolve()),
                    '-PfusionOutputPack='+str(args.output.resolve())], cwd=root, check=True)

if __name__ == '__main__': main()
