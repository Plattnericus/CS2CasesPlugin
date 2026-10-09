"""Refresh a combined HD pack with the current models while preserving its artwork and fonts."""
import argparse, io, json, os, zipfile
from pathlib import Path
from PIL import Image

def inspect_texture(artwork, mask):
    art = Image.open(io.BytesIO(artwork)).convert('RGBA')
    current = Image.open(io.BytesIO(mask)).convert('RGBA')
    if art.width != art.height or art.width % current.width:
        raise ValueError('Inspect texture sizes must use an integer square scale')
    scale = art.width // current.width
    src, dest, reference = art.load(), Image.new('RGBA', art.size), current.load()
    pixels = dest.load()
    for cy in range(current.height):
        for cx in range(current.width):
            if reference[cx, cy][3] != 255:
                continue
            # Use nearby artwork inside this geometry cell for newly filled edge pixels.
            opaque = [(x, y) for y in range(cy*scale, (cy+1)*scale)
                      for x in range(cx*scale, (cx+1)*scale) if src[x, y][3] >= 128]
            for y in range(cy*scale, (cy+1)*scale):
                for x in range(cx*scale, (cx+1)*scale):
                    color = src[x, y]
                    if color[3] < 128:
                        nearest = min(opaque, key=lambda p: (p[0]-x)**2+(p[1]-y)**2) if opaque else None
                        color = src[nearest] if nearest else reference[cx, cy]
                    pixels[x, y] = (*color[:3], 255)
    data = io.BytesIO(); dest.save(data, format='PNG'); return data.getvalue()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', type=Path, required=True)
    parser.add_argument('--current', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.resolve() in {args.base.resolve(), args.current.resolve()}:
        raise ValueError('Write to a new pack; preserve both input archives')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_suffix('.tmp')
    try:
        with zipfile.ZipFile(args.base) as base, zipfile.ZipFile(args.current) as current, zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as out:
            for z in (base, current):
                if z.testzip(): raise ValueError('Corrupt input archive')
            original = set(base.namelist()); names = sorted(original | set(current.namelist()))
            for name in names:
                if name.endswith('/'): continue
                if name.startswith('assets/mccases/models/') or name.startswith('assets/mccases/items/'):
                    data = current.read(name)
                elif name.startswith('assets/mccases/textures/item/inspect/'):
                    data = inspect_texture(base.read(name), current.read(name)) if name in original else current.read(name)
                else:
                    data = base.read(name) if name in original else current.read(name)
                if name == 'README.txt':
                    data = ("MCCases Fusion HD · 1.2.0\n"
                            "815 skins · 63 weapon, knife and glove types · four inspect animations per type.\n"
                            "128px artwork, corrected hand/display orientation and opaque inspect edges.\n"
                            "Custom server font and its seven glyph textures are preserved.\n\n"
                            "Activate this ZIP in Minecraft's resourcepacks menu.\n"
                            "Enable resource-pack.enabled in plugins/MCCases/config.yml.\n"
                            "Set opening.max-active-per-player: 9 for nine simultaneous openings.\n"
                            "In /inventory, scroll wheel and number keys select the hotbar.\n"
                            "Use the collection's page buttons to change pages.\n"
                            "Load this pack above older MCCases packs.\n"
                            "exportpack generates the standard pack; preserve this combined HD pack separately.\n").encode()
                if name == 'pack.mcmeta':
                    metadata = json.loads(data)
                    metadata['pack']['description'] = 'KlassenServerTP · MCCases Fusion HD · 815 skins · 63 rigs'
                    data = json.dumps(metadata, ensure_ascii=False, indent=2).encode()
                info = zipfile.ZipInfo(name, (2026, 10, 9, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED
                out.writestr(info, data)
        with zipfile.ZipFile(temporary) as result:
            if result.testzip(): raise ValueError('Corrupt output archive')
            for name in original:
                if name.startswith('assets/minecraft/') and result.read(name) != base_read(args.base, name):
                    raise ValueError('Changed custom vanilla asset: ' + name)
        os.replace(temporary, args.output)
        print('Refreshed and CRC-verified:', args.output)
    finally:
        temporary.unlink(missing_ok=True)

def base_read(path, name):
    with zipfile.ZipFile(path) as archive: return archive.read(name)

if __name__ == '__main__': main()
