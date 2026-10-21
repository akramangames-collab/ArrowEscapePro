#!/usr/bin/env python3
"""Generate 400 interwoven puzzles with intentional space and bounded lengths."""
import argparse
import hashlib
import json
import time
from concurrent.futures import ProcessPoolExecutor, as_completed
from pathlib import Path
from readable_levels import readable
from spaced_levels import inspect
from weave_levels import expand, piece_of, ray, self_profile

ROOT=Path(__file__).resolve().parents[1]
SEEDS=ROOT/'scripts/seeds/spaced-v40.txt'
LEVELS=ROOT/'app/src/main/assets/levels.txt'
COUNT=400
RECIPE_HASH=hashlib.sha256(Path(__file__).read_bytes()+(ROOT/'scripts/spaced_levels.py').read_bytes()+(ROOT/'scripts/weave_levels.py').read_bytes()+(ROOT/'scripts/readable_levels.py').read_bytes()+SEEDS.read_bytes()).hexdigest()

def tier(level):
    return 3 if level%30==0 else 2 if level%12==0 else 1 if level%6==0 else 0

def pack(w,h,pieces):
    return f'{w},{h}|'+';'.join(','.join(map(str,[dx,dy]+[v for q in pts for v in q])) for dx,dy,pts,_ in pieces)

def build_level(level):
    cache=ROOT/'build/v41-level-cache'/f'{level:03d}.json'
    if cache.exists():
        entry=json.loads(cache.read_text())
        if entry.get('recipe')==RECIPE_HASH:return entry['pack'],entry['metrics']
    header,payload=SEEDS.read_text().splitlines()[level-1].split('|')
    w,h=map(int,header.split(','));seed=[]
    for raw in payload.split(';'):
        v=list(map(int,raw.split(',')))
        seed.append(piece_of(expand(list(zip(v[2::2],v[3::2])))))
    pieces,bends=readable(w,h,seed,level)
    walks=[expand(p[2]) for p in pieces]
    _,deps,depth,roots,bridges=inspect(w,h,walks)
    from weave_levels import frontier_bound
    lengths=[len(p[3])-1 for p in pieces]
    metrics=dict(usable_density=round(sum(len(p[3]) for p in pieces)/((w+1)*(h+1)-81),6),
        initial_safe_arrows=roots,dependency_depth=depth,max_solve_frontier=frontier_bound(deps),
        min_horizontal_bridges=min(bridges),no_horizontal_bands=True,
        short_arrows=sum(n<=14 for n in lengths),long_arrows=sum(n>=24 for n in lengths),
        max_bends=max(bends),mean_bends=round(sum(bends)/len(bends),3),
        simple_arrows=sum(n<=3 for n in bends),complex_arrows=sum(n>=4 for n in bends),
        min_path_length=min(lengths),max_path_length=max(lengths))
    assert metrics['max_bends']<=6,'An overcomplicated arrow remains'
    assert metrics['simple_arrows']>=len(pieces)*.9,'Too many complex arrows'
    assert metrics['usable_density']>=.30,'Board became excessively empty' 
    metrics.update(level=level,arrows=len(pieces),grid=[w,h],
        occupied_nodes=sum(len(p[3]) for p in pieces),
        self_crossing_arrows=sum(any(q in p[3] for q in ray(p,w,h)) for p in pieces),
        milestone=tier(level)>=2,boss=level%30==0,
        phase=('NORMAL','HARD','SUPER HARD','EXTREME HARD')[tier(level)])
    assert metrics['usable_density']<=.76,'Board is still overcrowded'
    assert metrics['short_arrows']>=len(pieces)*.5,'Short arrows must be the majority'
    assert metrics['long_arrows']<=len(pieces)*.12,'Too many long arrows'
    assert metrics['max_path_length']<=32
    output=pack(w,h,pieces)
    cache.parent.mkdir(parents=True,exist_ok=True)
    cache.write_text(json.dumps(dict(recipe=RECIPE_HASH,pack=output,metrics=metrics)))
    return output,metrics

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--check',action='store_true')
    parser.add_argument('--workers',type=int,default=8,choices=range(1,9))
    args=parser.parse_args();started=time.monotonic();levels={}
    with ProcessPoolExecutor(max_workers=args.workers) as pool:
        futures={pool.submit(build_level,lv):lv for lv in range(1,COUNT+1)}
        for future in as_completed(futures):
            lv=futures[future]
            try:levels[lv]=future.result()
            except Exception as error:print(f'FAILED level {lv}: {error}',flush=True);continue
            if len(levels)%50==0:print(f'Spaced levels: {len(levels)}/400',flush=True)
    assert len(levels)==COUNT,f'{COUNT-len(levels)} layouts still need generation'
    assets={LEVELS:'\n'.join(levels[lv][0] for lv in range(1,401))+'\n',
        ROOT/'tests/hard-level-metrics.json':json.dumps([levels[lv][1] for lv in range(1,401)],indent=2)+'\n'}
    for path,content in assets.items():
        if args.check:assert path.read_text()==content,f'Generated asset drift: {path}'
        else:
            temp=path.with_suffix(path.suffix+'.tmp');temp.write_text(content);temp.replace(path)
    print(f'400 V41 spaced layouts verified/generated in {time.monotonic()-started:.1f}s',flush=True)

if __name__=='__main__':main()
