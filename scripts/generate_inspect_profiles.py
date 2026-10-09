"""Rebuild the editable weapon timelines from their joint choreography."""
from pathlib import Path
import yaml

ROOT = Path(__file__).resolve().parents[1]
weapons = yaml.safe_load((ROOT / 'src/main/resources/defaults/catalog/weapons.yml').read_text())['weapons']
# Knife-specific grip pivots, axis, turn angle and weight (ticks). Three named actions each.
KNIVES = {
 'karambit': (-.30, 'z', 360, 12, ['finger_roll', 'reverse_grip', 'ring_twirl']),
 'butterfly': (-.03, 'z', 360, 16, ['rollover', 'helix', 'fan']),
 'm9_bayonet': (-.22, 'x', 180, 18, ['sawback_check', 'heavy_roll', 'guard_flip']),
 'bayonet': (-.20, 'z', 360, 15, ['draw_flip', 'edge_check', 'grip_roll']),
 'flip': (0, 'z', 180, 14, ['hinge_flick', 'blade_check', 'reverse_fold']),
 'gut': (-.20, 'x', 180, 16, ['hook_check', 'grip_flip', 'hook_roll']),
 'huntsman': (-.19, 'x', 180, 20, ['spine_check', 'weighted_roll', 'grip_turn']),
 'falchion': (0, 'z', 360, 17, ['hinge_flip', 'balance', 'palm_spin']),
 'bowie': (-.23, 'x', 180, 22, ['broadside_check', 'heavy_flip', 'edge_roll']),
 'shadow_daggers': (-.16, 'z', 180, 13, ['cross_draw', 'paired_roll', 'alternating_flip']),
 'navaja': (-.04, 'z', 180, 12, ['compact_flick', 'hinge_check', 'pocket_flip']),
 'stiletto': (-.03, 'z', 360, 11, ['spring_flick', 'finger_toss', 'needle_twirl']),
 'ursus': (-.16, 'z', 360, 14, ['palm_toss', 'catch_flip', 'blade_roll']),
 'talon': (-.28, 'z', 720, 18, ['free_ring_spin', 'reverse_catch', 'finger_loop']),
 'classic': (-.19, 'x', 180, 17, ['classic_turn', 'tip_check', 'grip_spin']),
 'paracord': (-.21, 'x', 180, 19, ['cord_check', 'field_roll', 'lanyard_turn']),
 'survival': (-.22, 'x', 180, 21, ['saw_check', 'field_flip', 'grip_inspect']),
 'nomad': (-.17, 'z', 180, 18, ['thumb_open', 'palm_check', 'grip_catch']),
 'skeleton': (-.16, 'z', 360, 13, ['ring_flip', 'extended_spin', 'toss_catch']),
 'kukri': (-.20, 'z', 180, 23, ['weighted_draw', 'curve_check', 'palm_roll']),
}
GUNS = {
 'glock18': ['slide_check','compact_roll','frame_inspect'], 'usps':['suppressor_check','slide_roll','tactical_peek'],
 'p2000':['slide_peek','frame_roll','sight_check'], 'p250':['compact_peek','slide_turn','grip_inspect'],
 'fiveseven':['polymer_check','slide_peek','sight_roll'], 'tec9':['receiver_check','magazine_peek','barrel_roll'],
 'cz75':['front_grip_check','compact_turn','magazine_peek'], 'dual_berettas':['paired_peek','crossed_roll','alternating_check'],
 'deagle':['finger_twirl','heavy_slide_check','palm_flip'], 'r8_revolver':['cylinder_check','barrel_turn','trigger_roll'],
 'mac10':['strap_check','receiver_roll','compact_peek'], 'mp9':['foregrip_check','compact_turn','sight_peek'],
 'mp7':['stock_check','receiver_turn','foregrip_roll'], 'mp5sd':['suppressor_peek','receiver_roll','stock_check'],
 'ump45':['magazine_check','stock_roll','receiver_peek'], 'p90':['top_magazine_check','bullpup_turn','receiver_roll'],
 'ppbizon':['drum_check','receiver_roll','stock_peek'], 'ak47':['magazine_peek','receiver_roll','wood_check'],
 'm4a4':['rail_check','receiver_turn','stock_peek'], 'm4a1s':['suppressor_check','receiver_roll','rail_peek'],
 'galil':['foregrip_peek','receiver_roll','magazine_check'], 'famas':['bullpup_check','carry_handle_peek','receiver_roll'],
 'aug':['scope_check','bullpup_roll','stock_peek'], 'sg553':['optic_check','receiver_roll','magazine_peek'],
 'ssg08':['bolt_check','scope_roll','stock_peek'], 'awp':['scope_peek','long_barrel_roll','bolt_check'],
 'scar20':['optic_check','magazine_roll','receiver_peek'], 'g3sg1':['scope_roll','stock_peek','receiver_check'],
 'nova':['pump_check','tube_roll','stock_peek'], 'xm1014':['tube_check','receiver_roll','stock_peek'],
 'sawedoff':['short_barrel_check','grip_roll','tube_peek'], 'mag7':['box_magazine_check','compact_roll','receiver_peek'],
 'm249':['belt_cover_check','support_roll','barrel_peek'], 'negev':['box_check','belt_cover_roll','support_peek'],
 'zeus':['capacitor_check','contact_peek','compact_roll'],
}
GLOVES = {id: ['knuckle_check', 'palm_turn', 'cuff_inspect'] for id, w in weapons.items() if w['category'] == 'glove'}

class Frame(dict): pass
yaml.SafeDumper.add_representer(Frame, lambda dumper, value: dumper.represent_mapping('tag:yaml.org,2002:map',value,flow_style=True))
def frame(t, ease='inout', **kw): return Frame(ticks=t, **kw, ease=ease)
def group(frames, parent=None, pivot=None):
 d={'keyframes':frames}
 if parent: d['parent']=parent
 if pivot: d['pivot']=pivot
 return d

def body(i, variant, weight):
 # Specific timings and pose sizes retain the mass/length differences of each weapon.
 yaw = 17 + (i % 7)*3
 bank = 13 + (i % 5)*2
 sign = -1 if variant == 1 else 1
 hold = 12 + (i % 4)*2
 if variant == 3:
  # A deliberate presentation arc with a soft wrist recoil and a longer readable broadside.
  return group([frame(0,move=[0,-.04,0],rotate=[0,10,12]),
    frame(5,move=[.018,-.075,-.01],rotate=[-4,6,8]),
    frame(11,ease='out',move=[-.085,.045,.035],rotate=[12,-yaw,bank+4]),
    frame(weight,move=[-.06,.065,.025],rotate=[4,yaw+12,bank]),
    frame(hold+6),frame(weight,move=[.015,-.02,0],rotate=[-6,-yaw/2,-bank/2]),
    frame(7,ease='out',move=[-.008,-.03,.002],rotate=[2,14,16]),
    frame(9,move=[0,-.04,0],rotate=[0,10,12])])
 return group([frame(0,move=[0,-.04,0],rotate=[0,10,12]),
   frame(4,move=[.012,-.065,-.012],rotate=[-2,7,8]),
   frame(8+variant,ease='out',move=[-.07,.035,.015],rotate=[-5+variant*3,sign*yaw,sign*bank]),
   frame(weight,move=[-.04,.05,0],rotate=[8-variant*4,sign*(yaw+22),sign*(bank+8)]),
   frame(hold),frame(weight,rotate=[-4,-sign*yaw,-sign*bank]),
   frame(6,move=[-.006,-.032,.003],rotate=[1,12,14]),
   frame(9,move=[0,-.04,0],rotate=[0,10,12])])

out={'enabled':True,'anchor':{'forward':1.75,'right':.43,'up':-.07},'animations':{},'animation-pools':{}}
for i,(id,w) in enumerate(weapons.items()):
 knife=id in KNIVES
 glove=id in GLOVES
 names=KNIVES[id][4] if knife else GLOVES[id] if glove else GUNS[id]
 names=names+(['blade_presentation'] if knife else ['paired_presentation'] if glove or id=='dual_berettas' else ['broadside_check'])
 pool=[]
 for v,name in enumerate(names):
  aid=f'{id}__{name}'; pool.append(aid)
  weight=KNIVES[id][3] if knife else (16 if w['category'] in ['sniper','heavy'] else 12)+(i%3)
  groups={'body':body(i,v,weight)}
  duration=sum(f['ticks'] for f in groups['body']['keyframes'])
  if knife:
   pivot,axis,angle,_,_=KNIVES[id]
   # Every gesture differs in grip axis, direction and turn count.
   turn=angle if v==0 else (-180 if v==1 else (360 if angle<360 else -angle)) if v==2 else 0
   rotation=[0,0,0];rotation['xyz'.index(axis)]=turn
   # A completed full turn is already the held orientation. Do not unwind it backwards.
   recovery=rotation if turn % 360 == 0 else [0,0,0]
   groups['roll']=group([frame(0,rotate=[0,0,0]),frame(14),frame(weight,rotate=rotation),frame(duration-14-weight-10),frame(10,rotate=recovery)], 'body',[pivot,0,0])
   if id=='butterfly':
    # Open handles swing independently around the common blade tang, never a single sprite.
    for side,sgn in [('handle_a',1),('handle_b',-1)]:
     sequence=[frame(0,rotate=[0,0,0]),frame(3+v),frame(6,rotate=[0,0,sgn*(170+v*10)]),
       frame(7,rotate=[0,0,sgn*(330 if v==1 else 80)]),frame(6,rotate=[0,0,sgn*(190 if v==2 else 0)]),
       frame(8,rotate=[0,0,sgn*(360 if v==1 else 0)]),frame(duration-30-v,rotate=[0,0,0])]
     groups[side]=group(sequence,'roll',[.005,.009,0])
   if id in ['flip','navaja','stiletto','falchion'] and v != 3:
    # Hinge in the pack is around its canvas blade boundary, close to the block rig's origin.
    hinge=w['regions']['blade'][0];px=(hinge-.5)*.75*60/64
    fold=165 if id!='stiletto' else 175
    groups['blade']=group([frame(0,rotate=[0,0,0]),frame(3),frame(5,rotate=[0,0,-fold]),frame(3),
      frame(5 if v!=1 else 8,rotate=[0,0,0]),frame(duration-(16 if v!=1 else 19))], 'roll',[round(px,4),0,0])
   if id=='falchion' and v==1:
    groups['roll']=group([frame(0,rotate=[0,0,0]),frame(10),frame(10,rotate=[0,0,90]),frame(24),frame(duration-44,rotate=[0,0,0])],'body',[.25,0,0])
  if glove:
   for side,sgn in [('pair_a',1),('pair_b',-1)]:
    groups[side]=group([frame(0,rotate=[0,0,0],move=[0,0,0]),
      frame(12+v,rotate=[sgn*(6+v*3),sgn*(20+v*8),sgn*8],move=[sgn*.025,.025,0]),
      frame(18,rotate=[-sgn*12,sgn*(45+v*5),-sgn*10]),frame(12),
      frame(duration-42-v,rotate=[0,0,0],move=[0,0,0])],'body',[-sgn*.2,0,0])
  if id in ['shadow_daggers','dual_berettas']:
   for side,sgn in [('pair_a',1),('pair_b',-1)]:
    parent='body'
    groups[side]=group([frame(0,rotate=[0,0,0],move=[0,0,0]),frame(9+v,rotate=[0,sgn*(25+v*10),sgn*12],move=[sgn*.04,sgn*.035,0]),
      frame(weight,rotate=[0,sgn*65,sgn*(180 if knife else 25)]),frame(12),
      frame(duration-21-v-weight,rotate=[0,0,0],move=[0,0,0])],parent,[-.29,.03,0] if side=='pair_a' else [.25,-.03,0])
  if not knife and not glove and id!='dual_berettas':
   slide = id in ['glock18','usps','p2000','p250','fiveseven','cz75','deagle','nova','sawedoff','ssg08','awp']
   # A light slide/bolt check; no ammunition or weapon mechanics are changed.
   shift=[(-.04-.008*v) if slide else 0,0 if slide else (-.025-.006*v),0]
   rot=[0,0,0] if slide else ([0,0,-24] if id in ['m249','negev','r8_revolver'] else [0,0,-7])
   groups['mechanism']=group([frame(0,move=[0,0,0],rotate=[0,0,0]),frame(12+v),
     frame(5,move=shift,rotate=rot),frame(6+v),frame(7,move=[0,0,0],rotate=[0,0,0]),frame(duration-30-2*v)],'roll' if id=='deagle' and v==0 else 'body',[-.04,.06,0])
  if id=='deagle' and v==0:
   groups['roll']=group([frame(0,rotate=[0,0,0]),frame(10),frame(14,rotate=[0,0,360]),frame(duration-24)],'body',[-.12,-.07,0])
  # Return all groups to the same physical held orientation (full turns are equivalent).
  sounds={'12':'inspect.swipe',str(duration-12):'inspect.end'}
  if knife: sounds[str(14+weight//2)]='inspect.spin'
  elif not glove and id!='dual_berettas': sounds[str(17+v)]='inspect.mech'
  out['animations'][aid]={'substeps':4,'sounds':sounds,'groups':groups}
 out['animation-pools'][id]=pool
path=ROOT/'src/main/resources/defaults/inspect-profiles.yml'
path.write_text(f'# Individual, editable inspect rigs for all {len(weapons)} weapons/glove types. Generated by scripts/generate_inspect_profiles.py.\n# Disable enabled to use the original inspect.yml pools. No immediate random repeats.\n'+yaml.safe_dump(out,sort_keys=False,width=120))
print(f'{len(weapons)} weapons, {len(out["animations"])} individual timelines')
