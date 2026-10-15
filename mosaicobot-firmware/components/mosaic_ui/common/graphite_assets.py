# SPDX-License-Identifier: Apache-2.0
"""Offline vector icon rasterization for the RGB565 GSP image pipeline.

The 24-unit paths are source assets; Pillow antialiases them at build time.
No runtime image renderer, network fonts or shared legacy assets are changed.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent / 'assets' / 'graphite'
INK = '#F1F2E9'
DARK = '#101416'
ORANGE = '#FF753E'
SUB = '#A6B0B5'
# Each icon consists of open polylines and ellipse bounds on a 24-unit grid.
GLYPHS = {
 'camera': ([(3,7,7,7,9,4,15,4,17,7,21,7,21,20,3,20,3,7)], [(8,9,16,17)]),
 'claw': ([(4,9,20,9,20,19,4,19,4,9),(8,5,8,9),(16,5,16,9),(1,12,1,16),(23,12,23,16),(8,19,8,22),(16,19,16,22),(9,13,9,15),(15,13,15,15)], []),
 'skills': ([(3,3,10,3,10,10,3,10,3,3),(14,3,21,3,21,10,14,10,14,3),(3,14,10,14,10,21,3,21,3,14),(14,17,21,17),(17,14,17,21)], []),
 'settings': ([(9,3,8,6,5,7,3,10,5,12,5,15,7,18,10,17,12,20,15,19,16,16,19,15,21,12,19,10,19,9,17,6,14,7,12,4,9,3)], [(9,9,15,15)]),
 'imu': ([(12,1,12,6),(12,18,12,23),(1,12,6,12),(18,12,23,12),(9,13,12,8,15,15)], [(4,4,20,20)]),
 'album': ([(3,3,21,3,21,21,3,21,3,3),(3,17,8,12,12,16,16,10,21,16)], [(6,6,10,10)]),
 'music': ([(10,18,10,5,20,3,20,16),(10,9,20,7)], [(3,15,10,21),(13,13,20,19)]),
 'bricks': ([(3,3,10,3,10,7,3,7,3,3),(14,3,21,3,21,7,14,7,14,3),(3,11,10,11,10,15,3,15,3,11),(4,21,16,21),(17,12,20,9)], [(16,15,20,19)]),
 'weather': ([(12,1,12,4),(12,20,12,23),(1,12,4,12),(20,12,23,12),(4,4,6,6),(18,18,20,20),(4,20,6,18),(18,6,20,4)], [(7,7,17,17)]),
 'controller': ([(7,14,7,7,10,5,11,7,11,12,11,3,14,3,15,13,15,7,18,6,19,8,19,15,17,21,9,21,3,14,4,11,7,14)], []),
 'partner': ([(2,21,2,17,5,13,11,13,14,17,14,21),(18,14,22,17,22,21)], [(5,4,11,10),(15,4,21,10)]),
 'module': ([(5,5,19,5,19,19,5,19,5,5),(9,9,15,9,15,15,9,15,9,9),(8,2,8,5),(16,2,16,5),(8,19,8,22),(16,19,16,22),(2,8,5,8),(19,8,22,8),(2,16,5,16),(19,16,22,16)], []),
 'wifi': ([(3,8,7,5,12,4,17,5,21,8),(6,12,9,10,12,9,15,10,18,12),(9,16,12,14,15,16)], [(11,19,13,21)]),
 'airdrop': ([(9,9,9,5,12,2,16,2,19,5,19,9,16,12),(15,15,15,19,12,22,8,22,5,19,5,15,8,12),(12,8,12,16)], []),
 'bt': ([(12,2,12,22,19,16,5,5),(5,19,19,8,12,2)], []),
 'batt': ([(5,22,16,9),(5,17,3,12,5,6,12,3,20,3,21,10,18,17,12,20,5,17)], []),
 'bellOn': ([(5,17,19,17,17,13,17,7,14,3,10,3,7,7,7,13,5,17),(10,21,14,21)], []),
 'vibOn': ([(8,3,16,3,16,21,8,21,8,3),(4,7,2,10,4,14,2,17),(20,7,22,10,20,14,22,17)], []),
 'slotL': ([(3,7,21,7,21,17,3,17,3,7),(7,10,7,14),(12,10,12,14),(17,10,17,14)], []),
 'soundLow_active': ([(3,9,8,9,13,4,13,20,8,15,3,15,3,9),(17,8,19,12,17,16),(20,5,23,12,20,19)], []),
 'up_chevron': ([(6,14,12,8,18,14)], []),
 'back': ([(14,6,8,12,14,18)], []),
 'arrow': ([(5,12,19,12),(13,6,19,12,13,18)], []),
 'bolt': ([(14,2,5,14,11,14,10,22,19,10,13,10,14,2)], []),
 'lock': ([(5,10,19,10,19,21,5,21,5,10),(8,10,8,5,10,3,14,3,16,5,16,10),(12,14,12,17)], []),
}
ALIASES = {'slotR':'slotL','slot_camera':'camera','sunSemi_active':'weather','bellOff':'bellOn','status_logo':'claw','status_wifi':'wifi'}

def raster(name, size, color=INK):
    name = ALIASES.get(name, name)
    lines, ellipses = GLYPHS[name]
    scale = size * 4 / 24
    im = Image.new('RGBA', (size*4, size*4))
    draw = ImageDraw.Draw(im)
    width = max(1, round(1.65*scale))
    for points in lines:
        xy = [(round(points[i]*scale), round(points[i+1]*scale)) for i in range(0,len(points),2)]
        draw.line(xy, fill=color, width=width, joint='curve')
        for x,y in (xy[0],xy[-1]):
            r=width/2
            draw.ellipse((x-r,y-r,x+r,y+r),fill=color)
    for box in ellipses:
        draw.ellipse(tuple(round(v*scale) for v in box),outline=color,width=width)
    return im.resize((size,size),Image.Resampling.LANCZOS)

def generate():
    for folder in ('icons','control_center'):
        (ROOT/folder).mkdir(parents=True,exist_ok=True)
    for name in GLYPHS.keys() | ALIASES.keys():
        for folder in ('icons','control_center'):
            size = 40 if name.startswith('status_') else 48
            raster(name,size).save(ROOT/folder/(name+'.png'))
            raster(name,size,DARK).save(ROOT/folder/(name+'_dark.png'))
    for level in range(5):
        im=raster('wifi',40, SUB if level==0 else INK)
        if level < 4:
            # Keep the original five signal levels rather than faking full bars.
            d=ImageDraw.Draw(im)
            for segment in range(3-level):
                d.rectangle((0,0,40,round((8+segment*4)*40/24)),fill=(0,0,0,0))
        im.save(ROOT/'icons'/f'status_wifi_{level}.png')
    # Rounded mask for the native rectangular vertical progress control.
    mask=Image.new('RGBA',(72*4,152*4),'#1C2226')
    ImageDraw.Draw(mask).rounded_rectangle((0,0,72*4-1,152*4-1),radius=19*4,fill=(0,0,0,0))
    mask.resize((72,152),Image.Resampling.LANCZOS).save(ROOT/'control_center'/'capsule_mask_72.png')
