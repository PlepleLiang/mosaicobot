#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Graphite system UI. Keep Hub StackView, PageFlow, Drawer and app routes."""
from __future__ import annotations
import json
import sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent.parent / 'common'))
from font_paths import DEJAVU_SANS
from graphite_assets import generate as generate_icons
from scene_common import (auto_charset, CONTENT_H, CONTENT_W, SCREEN_H, SCREEN_W,
    button, container, image, label, layer, shared_prefix, scene_out_path,
    status_bar as legacy_status_bar, asset_scene, explicit_charset, shared_charset)

FONT = DEJAVU_SANS
BOLD = FONT
ASSETS = '../../common/assets/graphite'
BG, PANEL, TEXT, SUB, ORANGE, RULE = '#101416','#1C2226','#F1F2E9','#A6B0B5','#FF753E','#485257'
STACK_PAGE_COUNT, FLOW_PAGE_COUNT, TAB_HOME = 3, 3, 0
FONT_POLICIES = {12: shared_charset(),16: shared_charset(),18: shared_charset(),
                 28: auto_charset(),40: explicit_charset('0123456789'),104: explicit_charset(':0123456789')}
ICON_NAMES = ('camera','claw','skills','settings','imu','album','music','bricks','weather','controller','partner','module','back','arrow','bolt','lock','up_chevron')
HUB_IMAGES = [(f'{ASSETS}/icons/{n}.png',48,48) for n in ICON_NAMES]
HUB_IMAGES += [(f'{ASSETS}/icons/status_logo.png',40,40)]
HUB_IMAGES += [(f'{ASSETS}/icons/status_wifi_{n}.png',40,40) for n in range(5)]
HUB_IMAGES += [(f'{ASSETS}/control_center/capsule_mask_72.png',72,152)]

def glyph(objs,parent,icon,x,y,size=28,**kw):
    objs.append(image(parent,f'{ASSETS}/icons/{icon}.png',x,y,size,size,**kw))

def text(objs,parent,x,y,w,h,value,size=16,**kw):
    objs.append(label(parent,x,y,w,h,value,size=size,color=kw.pop('color',TEXT),**kw))

def status_bar(objs,parent,assets_prefix=None,*,time_name='status_time',bind_prefix='status',y=7):
    start=len(objs)
    legacy_status_bar(objs,parent,ASSETS,time_name=time_name,bind_prefix=bind_prefix,y=y,time_size=16)
    for obj in objs[start:]:
        if obj.get('name')==time_name:
            obj.update(x=28,y=16,w=90,h=25)
            if bind_prefix=='status': obj['hidden']=True
        if obj.get('name')==f'{bind_prefix}_agent': obj.update(x=310,y=12,w=40,h=28)
        if obj.get('image','').endswith('status_logo.png'): obj.update(w=24,h=24)
        if obj.get('name')==f'{bind_prefix}_wifi': obj.update(x=376,y=7)
        if obj.get('name','').startswith(f'{bind_prefix}_wifi_bar_'):
            obj.update(x=8,y=8,w=24,h=24)
    if bind_prefix=='status': text(objs,parent,28,16,190,25,'mosaico',18)
    objs.append(container(parent,222,5,36,3,bg='#626B6D',radius=1))

def footer(objs,parent,page):
    text(objs,parent,24,386,150,26,f'HUB / 0{page+1}',12,color=SUB)
    for i,cb in enumerate(('hub_goto_home','hub_goto_apps1','hub_goto_apps2')):
        x=354+i*34
        objs.append(button(parent,x,382,34,34,'',bg=BG,callback=cb))
        objs.append(container(parent,x+8,397,18 if i==page else 5,4,bg=ORANGE if i==page else SUB,radius=2,callback=cb))

def app_tile(objs,parent,x,y,h,icon,title,callback,index,accent=False,name=None):
    objs.append(button(parent,x,y,137,h,'',bg=ORANGE if accent else PANEL,radius=16,border=ORANGE if accent else RULE,border_w=1,callback=callback))
    glyph(objs,parent,icon+('_dark' if accent else ''),x+17,y+17,34 if h<120 else 42,name=name,callback=callback)
    text(objs,parent,x+17,y+h-35,120,28,title,18,color=BG if accent else TEXT,callback=callback)
    text(objs,parent,x+104,y+12,28,22,f'{index:02}',12,color=BG if accent else SUB,callback=callback)

def build_clock_face(objs,parent):
    text(objs,parent,28,21,265,24,'-- / --',16,color=SUB,bind='home_date',font_charset=shared_charset()['charset'])
    text(objs,parent,304,21,148,24,'LOCAL TIME',12,color=SUB,align='right')
    text(objs,parent,22,57,152,130,'00',104,bind='home_hour',font_charset='0123456789')
    text(objs,parent,162,57,50,130,':',104,color=ORANGE)
    text(objs,parent,202,57,224,130,'00',104,bind='home_minute',font_charset='0123456789')
    objs.append(container(parent,28,197,424,1,bg=RULE,callback='app_weather'))
    glyph(objs,parent,'weather',28,214,30,callback='app_weather')
    text(objs,parent,70,205,108,42,'--',28,bind='home_weather_temp',name='home_weather_temp',font_charset='-0123456789°',callback='app_weather')
    text(objs,parent,192,210,260,24,'--',16,color=SUB,align='right',bind='home_weather_desc',name='home_weather_desc',font_charset=shared_charset()['charset'],callback='app_weather')
    text(objs,parent,192,234,260,23,'--',12,color=SUB,align='right',bind='home_weather_city',name='home_weather_city',font_charset=shared_charset()['charset'],callback='app_weather')
    for i,(icon,title,cb) in enumerate((('camera','Camera','app_camera'),('claw','CLAW','app_ai_create'),('skills','Works','app_works'))):
        app_tile(objs,parent,24+i*147,272,106,icon,title,cb,i+1,i==1,f'clock_shortcut_{icon}')
    footer(objs,parent,0)

def place_app_grid(objs,parent,slots):
    more=len(slots)==2
    text(objs,parent,24,14,320,44,'Stay curious.' if more else 'Your toolkit.',28)
    text(objs,parent,365,23,90,25,f'{len(slots):02} APPS',12,color=SUB,align='right')
    for i,s in enumerate(slots):
        app_tile(objs,parent,24+(i%3)*147,72+(i//3)*155,145,s['icon'],s['text'],s['callback'],i+1,name=s['name'])
    if more:
        objs.append(container(parent,28,271,424,1,bg=RULE))
        glyph(objs,parent,'module',28,294,40)
        text(objs,parent,88,288,356,30,'A little body. More possibilities.',16)
        text(objs,parent,88,322,356,26,'INTERACT / CONNECT / EXPLORE',12,color=SUB)
    footer(objs,parent,2 if more else 1)

def build_apps1_content(objs,parent):
    place_app_grid(objs,parent,tuple({'name':cb,'icon':icon,'text':title,'callback':cb} for icon,title,cb in (
        ('settings','Settings','app_settings'),('imu','IMU','app_imu'),('album','Album','app_album'),
        ('music','Music','app_music'),('bricks','Bricks','app_breakout'),('weather','Weather','app_weather'))))

def stack_pop_btn(objs,parent,*,x=18,y=17,w=46,h=46,text=''):
    events=[{'event':'click','action':'stack_pop','target_name':'hub_stack'}]
    objs.append(button(parent,x,y,w,h,text,bg=PANEL,border=RULE,border_w=1,radius=12,events=events))
    if not text: glyph(objs,parent,'back',x+11,y+11,24,events=events)

def build_quick_content(objs,parent):
    objs.append(container(parent,0,0,480,480,bg=BG,name='quick_background'))
    status_bar(objs,parent,time_name='quick_status_time',bind_prefix='quick_status')
    for name,x,y,w,h in (('connectivity',12,54,220,286),('media',248,54,220,206),('slot',12,356,220,116),('level',248,276,220,196)):
        objs.append(container(parent,x,y,w,h,bg=PANEL,radius=20,border=RULE,border_w=1,name=f'quick_{name}_group'))
    toggles=(('wlan','wifi','Wi-Fi',34,62,True),('join','airdrop','Join',138,62,False),
             ('bluetooth','bt','Bluetooth',34,154,False),('low_power','batt','Low power',138,154,False),
             ('ringtone','bellOn','Ringtone',34,246,False),('vibration','vibOn','Vibration',138,246,True))
    for key,icon,title,x,y,supported in toggles:
        name=f'quick_{key}'; cb=name+'_toggle'
        objs.append(container(parent,x,y,72,72,bg='#2A3237' if supported else PANEL,radius=17,border=RULE,border_w=1,name=name,callback=cb,bind=name+'_color'))
        if key=='low_power':
            glyph(objs,parent,icon,x+23,y+10,26,name=name+'_icon',callback=cb)
        else:
            for on in (False,True):
                # The whole icon+label group swaps so selected orange has dark ink.
                group=len(objs)
                objs.append(layer(parent,x,y,72,72,name=name+('_on' if on else '_off'),hidden=on,bind=name+('_on_visible' if on else '_off_visible'),bind_target='visible'))
                glyph(objs,group,icon+('_dark' if on else ''),23,10,26,callback=cb)
                text(objs,group,0,43,72,22,title,12,color=BG if on else SUB,align='center',callback=cb)
            if supported:
                text(objs,parent,x,y+73,72,18,'OFF',12,color=SUB,align='center',bind=name+'_state',font_charset='ONOFF')
        if key=='low_power': text(objs,parent,x,y+43,72,22,title,12,color=SUB,align='center',callback=cb)
        if not supported: text(objs,parent,x-6,y+73,84,18,'RESERVED',12,color=SUB,align='center')
    for i,height in enumerate((14,26,46,34,20)):
        objs.append(container(parent,270+i*12,95-height//2,6,height,bg=ORANGE,radius=1))
    text(objs,parent,270,132,176,36,'Music',28)
    text(objs,parent,270,174,178,24,'YOUR SOUND',12,color=SUB)
    text(objs,parent,270,218,158,26,'Open Music',16,color=SUB)
    glyph(objs,parent,'arrow',422,217,22)
    objs.append(container(parent,248,54,220,206,bg='#00000000',radius=20,name='quick_music_launcher',callback='app_music'))
    for i,side in enumerate(('l','r')):
        x=24+i*104
        text(objs,parent,x,367,92,22,('LEFT' if i==0 else 'RIGHT')+' PORT',12,color=SUB,align='center')
        for mode,icon,title in (('empty','slotL','Empty'),('module','module','Module'),*([('camera','camera','Camera')] if i==0 else [])):
            group=len(objs)
            objs.append(layer(parent,x,393,92,70,hidden=mode!='empty',name=f'quick_slot_{side}_{mode}',bind=f'quick_slot_{side}_{mode}_visible',bind_target='visible'))
            glyph(objs,group,icon,34,0,24)
            text(objs,group,0,31,92,25,title,16,color=SUB,align='center')
    for x,key,icon in ((270,'volume','soundLow_active'),(374,'brightness','sunSemi_active')):
        bind='quick_'+key
        objs.append(container(parent,x,298,72,152,bg='#303A40',radius=19))
        objs.append({'type':'progress','parent':parent,'x':x,'y':298,'w':72,'h':152,'value':0,'min':0,'max':100,'vertical':True,'bind':bind+'_fill','name':bind+'_fill','fg_color':ORANGE if key=='volume' else TEXT,'radius':0})
        objs.append(image(parent,f'{ASSETS}/control_center/capsule_mask_72.png',x,298,72,152))
        objs.append(container(parent,x+12,307,48,23,bg=BG,radius=6))
        text(objs,parent,x+12,307,48,23,'0',12,align='center',bind=bind+'_text',font_charset='0123456789')
        objs.append(container(parent,x+19,403,34,34,bg=BG,radius=8))
        glyph(objs,parent,icon,x+25,409,22)
        objs.append({'type':'slider','parent':parent,'x':x,'y':298,'w':72,'h':152,'value':0,'min':0,'max':100,'vertical':True,'bind':bind,'name':bind+'_input','bg_color':'#00000000','fg_color':'#00000000','knob_color':'#00000000','knob':False,'radius':19,'track_size':72,'opacity':0})
    glyph(objs,parent,'up_chevron',228,450,24,name='quick_up_chevron')
    feedback=len(objs)
    objs.append(layer(parent,0,0,480,54,hidden=True,name='quick_feedback',bind='quick_feedback_visible',bind_target='visible'))
    objs.append(container(feedback,110,9,260,36,bg=PANEL,radius=10))
    text(objs,feedback,120,14,240,30,'',18,align='center',name='quick_feedback_text',bind='quick_feedback_text',font_charset='Wi-Fi OnOffVibrationNot Supported')

def build_notification_content(objs,parent):
    objs.append(container(parent,0,0,480,480,bg=BG,name='notif_bg'))
    stack_pop_btn(objs,parent)
    text(objs,parent,74,19,340,42,'Notifications',28)
    objs.append({'type':'list','parent':parent,'x':18,'y':75,'w':444,'h':352,'item_height':96,'selected':0,'name':'notif_list','bg_color':BG,'fg_color':TEXT,'items':[
        'Unexpected removal · just now','Environment Sensor · 2m','Charging · 16min','System · Setup completed · 1h','CLAW · Benchmark finished']})
    text(objs,parent,28,442,424,24,'LATEST ON TOP / SCROLL FOR OLDER',12,color=SUB)

def append_unlock_hint(objs,parent):
    glyph(objs,parent,'up_chevron',228,440,24)
    text(objs,parent,32,411,350,26,'SWIPE UP TO OPEN',16,color={'theme':'aod_hint'})

def lock_clock(objs,parent,prefix):
    text(objs,parent,27,116,430,128,'00:00',104,color='#C8D0C4' if prefix=='aod' else TEXT,bind=prefix+'_clock',name=prefix+'_clock',font_charset=':0123456789')
    text(objs,parent,32,263,130,26,'--/--',16,color=SUB,bind=prefix+'_date_prefix',name=prefix+'_date_prefix',font_charset='0123456789/ ·')
    text(objs,parent,163,263,160,26,'---',16,color=SUB,bind=prefix+'_date_day',name=prefix+'_date_day',font_charset='SunMonTueWedThuFriSat')

def build_aod_content(objs,parent):
    objs.append(container(parent,0,0,480,480,bg='#080B0C',name='aod_bg'))
    glyph(objs,parent,'lock',30,34,22)
    text(objs,parent,62,33,315,30,'MOSAICO / AMBIENT',16,color=SUB)
    lock_clock(objs,parent,'aod')
    text(objs,parent,380,411,70,26,'--%',16,color=SUB,align='right',bind='aod_battery',font_charset='-0123456789%')
    append_unlock_hint(objs,parent)
    mode=button(parent,382,12,86,44,'CHRG',bg=PANEL,radius=12,size=16,name='aod_show_charge',callback='lock_screen_charge')
    mode.update(bind='aod_mode_switch_visible',bind_target='visible');objs.append(mode)

def build_charging_content(objs,parent):
    objs.append(container(parent,0,0,480,480,bg='#080B0C',name='charge_bg'))
    glyph(objs,parent,'bolt',30,34,22)
    text(objs,parent,62,33,300,30,'POWER CONNECTED',16,color=SUB)
    lock_clock(objs,parent,'charge')
    # Reuse the ten existing percentage bands as a horizontal charge bar.
    objs.append(container(parent,28,306,424,92,bg=PANEL,radius=17,border=RULE,border_w=1))
    glyph(objs,parent,'bolt',44,331,30)
    for name,hidden in (('standard',False),('100',True)):
        group=len(objs);objs.append(layer(parent,92,313,110,53,name='charge_percent_'+name,bind='charge_percent_'+name,hidden=hidden))
        text(objs,group,0,0,110,53,'0' if name=='standard' else '100',40,bind='charge_percent' if name=='standard' else None,name='charge_percent' if name=='standard' else None,font_charset='0123456789')
    for digits in (1,2,3):
        group=len(objs);objs.append(layer(parent,205,326,25,28,name=f'charge_percent_unit_{digits}',bind=f'charge_percent_unit_{digits}',hidden=digits!=1))
        text(objs,group,0,0,25,28,'%',16,color=SUB)
    text(objs,parent,237,327,180,27,'Charging',16,color=SUB)
    objs.append(container(parent,94,377,332,4,bg=RULE))
    for n in range(1,11):
        objs.append(container(parent,94+(n-1)*33,377,33,4,bg=ORANGE,name=f'charge_fill_{n}',bind=f'charge_fill_{n}',hidden=True))
    append_unlock_hint(objs,parent)
    mode=button(parent,382,12,86,44,'AOD',bg=PANEL,radius=12,size=16,name='charge_show_aod',callback='lock_screen_aod')
    mode.update(bind='charge_mode_switch_visible',bind_target='visible');objs.append(mode)

def build_insert_content(objs,parent):
    objs.append(container(parent,0,0,480,480,bg=BG,name='insert_bg'))
    stack_pop_btn(objs,parent)
    text(objs,parent,74,19,384,42,'Module connected',28)
    text(objs,parent,28,101,420,28,'Right port',16,color=ORANGE,bind='insert_port',name='insert_port',font_charset='LeftRight port')
    objs.append(container(parent,28,150,80,80,bg=PANEL,border=RULE,border_w=1,radius=20))
    glyph(objs,parent,'module',47,169,42)
    text(objs,parent,28,252,424,48,'Environment Sensor',28,bind='insert_board_name',name='insert_board_name',font_charset='Environment SensorIMUTOFThermal CameraRelayLED MatrixInteraction')
    text(objs,parent,28,305,424,54,'Temp / Humidity / TVOC',16,color=SUB,bind='insert_board_cap',name='insert_board_cap',font_charset=shared_charset()['charset'])
    objs.append(button(parent,246,394,206,56,'Open',bg=ORANGE,fg=BG,radius=14,size=18,name='insert_open',callback='insert_open'))
    objs.append(button(parent,28,394,206,56,'Done',bg=PANEL,border=RULE,border_w=1,radius=14,size=18,name='insert_dismiss',events=[{'event':'click','action':'stack_pop','target_name':'hub_stack'}]))

def build_hub_objects():
    generate_icons()
    objs, content = shared_prefix(HUB_IMAGES, FONT_POLICIES, BOLD)
    objs[0]['bg_color'] = BG
    objs[content]['bg_color'] = BG

    stack = len(objs)
    objs.append({
        'type': 'stackview', 'parent': content,
        'x': 0, 'y': 0, 'w': CONTENT_W, 'h': CONTENT_H,
        'name': 'hub_stack', 'page_count': STACK_PAGE_COUNT,
        'initial_page': 0,
        'capacity': STACK_PAGE_COUNT, 'axis': 'horizontal',
    })

    launcher_page = len(objs)
    objs.append(layer(stack, 0, 0, CONTENT_W, CONTENT_H,
                      name='hub_stack_page0'))
    flow = len(objs)
    objs.append({
        'type': 'page_flow', 'parent': launcher_page,
        # The status bar is fixed chrome.  Its pixels deliberately sit
        # outside the snapshot-composition viewport used while dragging.
        'x': 0, 'y': 56, 'w': CONTENT_W, 'h': CONTENT_H - 56,
        'name': 'launcher_flow', 'page_count': FLOW_PAGE_COUNT,
        'selected': TAB_HOME,
        'bar_height': 0, 'axis': 'horizontal', 'cyclic': False,
    })
    # tab0: Home = Figma 时钟 (not 息屏)
    home_main = len(objs)
    objs.append(layer(flow, 0, 0, CONTENT_W, CONTENT_H,
                      name='launcher_flow_tab0'))
    build_clock_face(objs, home_main)

    # tab1: Apps1 — icon grid only (no bottom dots / Home chrome).
    apps1_main = len(objs)
    objs.append(layer(flow, CONTENT_W, 0, CONTENT_W, CONTENT_H,
                      name='launcher_flow_tab1'))
    build_apps1_content(objs, apps1_main)

    apps2_main = len(objs)
    objs.append(layer(flow, CONTENT_W * 2, 0, CONTENT_W, CONTENT_H,
                      name='launcher_flow_tab2'))
    place_app_grid(objs, apps2_main, (
        {'name': 'app_interact', 'icon': 'controller', 'text': 'Interaction',
         'callback': 'app_interact'},
        {'name': 'app_peer', 'icon': 'partner', 'text': 'Partner',
         'callback': 'app_peer'},
    ))

    # Fixed chrome is authored after PageFlow content so it remains topmost.
    status_bar(objs, launcher_page, ASSETS)

    # stack page 1: Notification Center
    notif_main = len(objs)
    objs.append(layer(stack, CONTENT_W, 0, CONTENT_W, CONTENT_H,
                      name='hub_stack_page1'))
    build_notification_content(objs, notif_main)

    # stack page 2: Insert notification
    insert_main = len(objs)
    objs.append(layer(stack, CONTENT_W * 2, 0, CONTENT_W, CONTENT_H,
                      name='hub_stack_page2'))
    build_insert_content(objs, insert_main)

    drawer = len(objs)
    objs.append({
        'type': 'drawer', 'parent': content,
        # The visual panel remains 472 px tall, but the Drawer owns the full
        # input viewport so an upward close gesture can begin at y=479.
        'x': 0, 'y': 0, 'w': CONTENT_W, 'h': CONTENT_H,
        'name': 'quick_drawer', 'edge': 'top', 'open': False,
    })
    quick_main = len(objs)
    objs.append(layer(drawer, 0, 0, CONTENT_W, CONTENT_H,
                      name='quick_main', bind='quick_main_visible'))
    build_quick_content(objs, quick_main)

    # Physical insertion prelude. Both strips are authored so the hardware
    # event can select its actual port.
    # Bounded x/y make the strips eligible for synchronized position tweens.
    for side, x_default, x_min, x_max, radius in (
            ('left', -16, -16, 0, 12),
            ('right', 480, 464, 480, 12)):
        strip = container(
            content,
            {'default': x_default, 'min': x_min, 'max': x_max},
            {'default': 80, 'min': 79, 'max': 81},
            16, 315, bg=ORANGE, radius=radius,
            name=f'insert_fx_{side}', bind=f'insert_fx_{side}_visible',
            hidden=True, opacity=204,
        )
        strip['bind_target'] = 'visible'
        objs.append(strip)

    # Lock content shares the native Drawer with quick controls. Only one
    # drawer owns the viewport, including frozen drag/settle composition.
    lock_screen = len(objs)
    objs.append(layer(
        drawer, 0, 0, CONTENT_W, CONTENT_H,
        hidden=True, name='lock_screen', bind='lock_screen_visible',
    ))
    lock_aod = len(objs)
    objs.append(layer(
        lock_screen, 0, 0, CONTENT_W, CONTENT_H,
        name='lock_screen_aod', bind='lock_screen_aod_visible',
    ))
    build_aod_content(objs, lock_aod)
    lock_charge = len(objs)
    objs.append(layer(
        lock_screen, 0, 0, CONTENT_W, CONTENT_H,
        hidden=True, name='lock_screen_charge',
        bind='lock_screen_charge_visible',
    ))
    build_charging_content(objs, lock_charge)
    return objs


def write_scene(path: Path, screen: str, objects):
    scene = {
        'screen': screen,
        'w': SCREEN_W,
        'h': SCREEN_H,
        'screen_bg': BG,
        'themes': {
            'aod_hint': {
                'type': 'color', 'default': '#D6D6DE', 'dynamic': True,
            },
        },
        'font': FONT,
        'default_font_size': 18,
        'font_link': 'auto',
        'objects': objects,
    }
    path.write_text(json.dumps(scene, ensure_ascii=False, indent=2) + '\n',
                    encoding='utf-8')
    print('wrote', path.name)


def main():
    objects = build_hub_objects()
    write_scene(scene_out_path(HERE, 'mosaic_hub_480.json'), 'mosaic_hub', objects)

    asset_scene(scene_out_path(HERE, 'mosaic_assets_480.json'), 'mosaic_assets', HUB_IMAGES,
                FONT_POLICIES, BOLD, FONT)
    print(f'generated {len(objects)} objects in one hub scene')


if __name__ == '__main__':
    main()
