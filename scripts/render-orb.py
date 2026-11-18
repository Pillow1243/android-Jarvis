#!/usr/bin/env python3
"""Regenerate the original phosphor-field texture. Optional art tool: Python 3 + Pillow.
The APK ships this transparent image; Android does not tessellate 3,000 wire segments per frame.
"""
from math import cos, sin, pi
from pathlib import Path
from PIL import Image, ImageDraw

scale = 3
side, radius = 384 * scale, 148 * scale
pixels = Image.new('RGBA', (side, side))
screen = ImageDraw.Draw(pixels)
segments, sparks = [], []
for i in range(39):
    lat = -1.53 + i * 3.06 / 38
    sl, cl = sin(lat), cos(lat)
    prev = None
    for j in range(97):
        a = 2 * pi * j / 96
        ripple = (.125 * sin(3*a + 2.8*lat + 1.1) + .085 * cos(2*a - 4*lat - .65)) * cl
        r = (1 + ripple) * cl
        x, z = r * cos(a), r * sin(a)
        y = sl * (1 + ripple*.4) + .085 * cl * sin(2*a + lat*2 + 1.1)
        vy, depth = .89605*y - .44395*z, .44395*y + .89605*z
        pt = (side/2 + radius*(x*.97367 + vy*.22798), side/2 + radius*(vy*.97367 - x*.22798))
        light = .5 + .5*sin(a*2.5 + lat*3 + .4)
        v = max(0, min(1, .58*depth+.42*light))
        color = (int(70+176*v),int(180+74*v),int(123+63*v),int(89+166*v)) if depth>0 else (98,173,136,51)
        if prev:
            segments.append((depth,prev,pt,color,max(2,int(scale*(.6 + 1.4*v)))))
        if depth>.07 and i%3==0 and j%6==0: sparks.append((pt,v))
        prev = pt
for _,start,end,color,width in sorted(segments,key=lambda s:s[0]):screen.line((start,end),fill=color,width=width)
for (x,y),v in sparks:
    r=scale*(.65+v*.7);screen.ellipse((x-r,y-r,x+r,y+r), fill=(220,255,199,210))
path=Path(__file__).resolve().parents[1]/'app/src/main/res/drawable-nodpi/nexus_core.webp'
path.parent.mkdir(parents=True,exist_ok=True)
pixels.resize((384,384),Image.Resampling.LANCZOS).save(path,format='WEBP',lossless=True,method=6)
