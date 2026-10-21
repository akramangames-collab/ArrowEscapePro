#!/usr/bin/env python3
"""Render the actual packed level geometry and a standalone playable preview."""
import base64,json
from pathlib import Path
from PIL import Image
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch,Polygon,Ellipse

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT.parent/'deliverables'
OUT.mkdir(exist_ok=True)
atlas=Image.open(ROOT/'app/src/main/assets/characters/rescue-boy-expressions.png').convert('RGBA')
frames=[]
for state in range(4):
    x=(state%2)*atlas.width//2;y=(state//2)*atlas.height//2
    alpha=atlas.crop((x,y,x+atlas.width//2,y+atlas.height//2)).getchannel('A').point(lambda a:255 if a>24 else 0)
    box=alpha.getbbox();frames.append([x+box[0],y+box[1],box[2]-box[0],box[3]-box[1]])
levels=[]
for line in (ROOT/'app/src/main/assets/levels.txt').read_text().splitlines()[:3]:
    header,payload=line.split('|');w,h=map(int,header.split(','));pieces=[]
    for raw in payload.split(';'):
        v=list(map(int,raw.split(',')));pieces.append(dict(dx=v[0],dy=v[1],pts=list(zip(v[2::2],v[3::2]))))
    levels.append(dict(w=w,h=h,pieces=pieces))

fig=plt.figure(figsize=(7.2,15.6),dpi=100,facecolor='#050d1e')
ax=fig.add_axes([0,0,1,1]);ax.set_xlim(0,720);ax.set_ylim(1560,0);ax.axis('off')
def card(x,y,w,h,fill='#142941',edge='#436b83',r=20):
    ax.add_patch(FancyBboxPatch((x,y),w,h,boxstyle=f'round,pad=0,rounding_size={r}',facecolor=fill,edgecolor=edge,linewidth=1.5))
def text(x,y,s,size=15,color='#c3d2e9',weight='normal'):
    ax.text(x,y,s,ha='center',va='center',fontsize=size,color=color,weight=weight,fontfamily='DejaVu Sans')
card(14,14,692,118,'#202d54','#416181');text(360,46,'Level 1',23,'#ffffff','bold')
text(360,77,'CH 1 · First Escape · NORMAL',11)
text(65,45,'‹',34,'#50e0ff');text(653,45,'⚙',23,'#50e0ff')
text(63,106,'51',16,'#ffffff','bold');text(360,106,'♥  ♥  ♥',22,'#ff5d86');text(650,106,'100',15,'#ffe28a')
lv=levels[0];w,h=lv['w'],lv['h'];sx=640/w;sy=1150/h;left=40;top=182
for p in lv['pieces']:
    xy=[(left+x*sx,top+y*sy) for x,y in p['pts']]
    ax.plot(*zip(*xy),color='#50e0ff',linewidth=2.2,solid_capstyle='round',solid_joinstyle='round')
    x,y=xy[-1];dx,dy=p['dx'],p['dy'];px,py=-dy,dx
    ax.add_patch(Polygon([(x+dx*5,y+dy*5),(x-dx*8+px*4,y-dy*8+py*4),(x-dx*5,y-dy*5),(x-dx*8-px*4,y-dy*8-py*4)],closed=True,color='#50e0ff'))
cx=left+(w//2)*sx;cy=top+(h//2)*sy;cw=8*sx;ch=8*sy
card(cx-cw/2,cy-ch/2,cw,ch,'#10243a','#436b83',20)
card(cx-cw/2+12,cy-ch/2+12,cw-24,22,'#294557','#294557',10)
text(cx,cy-ch/2+24,'Help me escape!',10,'#ffffff','bold')
ax.add_patch(Ellipse((cx,cy+ch*.27),cw*.77,ch*.11,facecolor='#123e50',edgecolor='#267085',linewidth=2))
x,y,fw,fh=frames[0];sprite=atlas.crop((x,y,x+fw,y+fh));height=ch*.57;width=height*fw/fh
ax.imshow(sprite,extent=[cx-width/2,cx+width/2,cy+ch*.28,cy+ch*.28-height],zorder=3)
ax.plot([cx-cw*.34,cx+cw*.34],[cy+ch*.39]*2,color='#2d4558',linewidth=3)
text(cx,cy+ch*.45,'0% CLEAR',8)
text(360,1387,'PINCH TO ZOOM · 100%',11,'#8199bd')
for i,(name,count,fill,edge) in enumerate([('Hint','1','#142c43','#70bcea'),('Eraser','0','#142c43','#70bcea'),('Bomb','0','#242445','#b5a5ff'),('Sweep','0','#142e3c','#64dacb')]):
    x=16+i*174;card(x,1414,166,108,fill,edge,20);text(x+83,1450,name,15,'#ffffff','bold');text(x+83,1484,count,13,'#8be1ec')
text(360,1542,'LEVEL LAYOUT PREVIEW',9,'#8199bd')
fig.savefig(OUT/'Arrow-Escape-v41-Spaced-Level-Preview.png',dpi=100);plt.close(fig)

template=(ROOT/'scripts/spaced-preview-template.html').read_text()
template=template.replace('__LEVELS__',json.dumps(levels,separators=(',',':')))
template=template.replace('__FRAMES__',json.dumps(frames))
template=template.replace('__ATLAS_WIDTH__',str(atlas.width)).replace('__ATLAS_HEIGHT__',str(atlas.height))
template=template.replace('__ATLAS__',base64.b64encode((ROOT/'app/src/main/assets/characters/rescue-boy-expressions.png').read_bytes()).decode())
(OUT/'Arrow-Escape-v41-Playable-Preview.html').write_text(template)
print('Exact geometry preview and playable levels 1–3 created.')
