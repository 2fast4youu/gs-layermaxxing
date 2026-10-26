"""Derive the opt-in v8 occupancy contract. Never changes historic v6 geometry or art sizes."""
import argparse
import copy
import json
from pathlib import Path

GEO = Path(__file__).resolve().parents[1] / 'app' / 'geometry'
OUT = GEO / 'island_constant_ground.json'


def generate():
    old = json.loads((GEO / 'island_ground.json').read_text())
    data = copy.deepcopy(old)
    data['version'] = 8
    data['comment'] = ('Rule 8: each building retains its stage-0 ground and sea at every stage, on each land. '
                       'Decoration, terrain and visual sizes stay v6. Saved ledgers are never cleaned, moved or refunded. '
                       'Harbour uses the beach socket and first water berth at every stage. New/moved lighthouse retains v7 coast contact.')
    for key, piece in data['pieces'].items():
        if not key.startswith('building:'):
            continue
        base = old['pieces'][key.rsplit(':', 1)[0] + ':0']
        for land, shape in enumerate(piece['lands']):
            shape['ground'] = base['lands'][land]['ground'][:]
            shape.pop('sea', None)
            if 'sea' in base['lands'][land]:
                shape['sea'] = base['lands'][land]['sea'][:]
    return data


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    value = generate()
    if args.check:
        assert json.loads(OUT.read_text()) == value
    else:
        OUT.write_text(json.dumps(value, sort_keys=True, separators=(',', ':')) + '\n')
