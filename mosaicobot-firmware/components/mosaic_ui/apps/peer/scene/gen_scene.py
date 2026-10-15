#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Partner app scene. All CJK text is set at runtime; labels here carry ASCII placeholders."""
from pathlib import Path
import sys
import math

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parents[2] / "common"))
from font_paths import DEJAVU_SANS
from scene_common import ASCII_PRINTABLE, button, container, image, label, layer, scene_out_path, top_notice, write_scene

BG = "#0B0F14"
CARD = "#161D26"
LINE = "#243040"
ACCENT = "#6CC8FF"
DIM = "#8AA3BB"
INK = "#08121C"


def add_layer(objs, parent, x, y, w, h, bind, hidden=True):
    index = len(objs)
    objs.append(layer(parent, x, y, w, h, name=bind, bind=bind, bind_target="visible", hidden=hidden))
    return index


def visible_box(parent, x, y, w, h, bind, **kwargs):
    obj = container(parent, x, y, w, h, **kwargs)
    obj.update(name=bind, bind=bind, bind_target="visible", hidden=True)
    return obj


def text(parent, x, y, w, h, bind, size=18, color="#EAF4FF", align="center", placeholder="-"):
    return label(parent, x, y, w, h, placeholder, size=size, color=color, align=align, bind=bind)


def pill_button(objs, parent, x, y, w, h, text_bind, callback, bg=CARD, fg="#CFE3F5", border=LINE):
    objs.append(container(parent, x, y, w, h, bg=bg, radius=h // 2, border=border, border_w=2))
    objs.append(text(parent, x, y + h // 2 - 12, w, 24, text_bind, size=18, color=fg))
    objs.append(button(parent, x, y, w, h, "", radius=h // 2, opacity=0, callback=callback))


def mosaico_face(objs, parent, x, y, size):
    objs.append(container(parent, x, y, size, size, bg=BG, radius=size // 2, border=ACCENT, border_w=4))
    eye_w, eye_h = size // 9, size // 4
    for left in (32, 60):
        objs.append(container(parent, x + size * left // 100, y + size * 30 // 100, eye_w, eye_h, bg=ACCENT, radius=eye_w // 2))


def build_home(objs):
    home = add_layer(objs, 0, 0, 0, 480, 480, "peer_home_visible", hidden=False)

    empty = add_layer(objs, home, 0, 0, 480, 480, "peer_empty_visible")
    objs.append(container(empty, 146, 62, 188, 34, bg=CARD, radius=17, border=LINE, border_w=1))
    objs.append(text(empty, 150, 68, 180, 24, "peer_network_status", size=16, color=DIM))
    objs.append(container(empty, 184, 124, 112, 88, bg="#102A3D", radius=30, border=ACCENT, border_w=2))
    for x in (217, 251):
        objs.append(container(empty, x, 150, 12, 30, bg=ACCENT, radius=6))
    for x, y, size in ((153, 178, 8), (320, 130, 6)):
        objs.append(container(empty, x, y, size, size, bg=DIM, radius=size // 2))
    objs.append(text(empty, 58, 234, 364, 42, "peer_empty_title", size=30))
    objs.append(text(empty, 48, 284, 384, 28, "peer_empty_sub", size=20, color="#CFE3F5"))
    objs.append(text(empty, 64, 320, 352, 28, "peer_empty_hint", size=18, color=DIM))
    activity = add_layer(objs, empty, 0, 0, 480, 480, "peer_scan_activity_visible")
    for i, x in enumerate((216, 236, 256)):
        objs.append(container(activity, x, 382, 8, 8, bg=LINE, radius=4))
        objs.append(visible_box(activity, x, 382, 8, 8, f"peer_scan_dot_{i}_visible", bg=ACCENT, radius=4))
    settings = add_layer(objs, empty, 148, 366, 184, 64, "peer_network_settings_visible")
    pill_button(objs, settings, 0, 0, 184, 64, "peer_network_settings_text", "peer_network_settings")

    card = add_layer(objs, home, 0, 0, 480, 480, "peer_card_visible")
    for i in range(8):
        angle = math.radians(-90 + i * 45)
        x, y = round(234 + 220 * math.cos(angle)), round(234 + 220 * math.sin(angle))
        dot = add_layer(objs, card, x, y, 12, 12, f"peer_dot_{i}_visible")
        objs.append(container(dot, 0, 0, 12, 12, bg=DIM, radius=6))
        objs.append(visible_box(dot, 0, 0, 12, 12, f"peer_dot_{i}_selected", bg=ACCENT, radius=6))
    amoo = add_layer(objs, card, 170, 96, 140, 140, "peer_avatar_amoo_visible")
    objs.append(image(amoo, "../../../common/assets/peer/amoo.png", 0, 0, 140, 140, name="amoo_portrait"))
    mo = add_layer(objs, card, 170, 96, 140, 140, "peer_avatar_mo_visible")
    mosaico_face(objs, mo, 0, 0, 140)
    objs.append(visible_box(card, 292, 100, 22, 22, "peer_new_visible", bg="#FF9A3D", radius=11))
    objs.append(text(card, 70, 244, 340, 40, "peer_name", size=30))
    objs.append(text(card, 70, 288, 340, 26, "peer_status", size=18, color=DIM))

    pill_button(objs, card, 108, 324, 76, 76, "peer_btn0_text", "peer_btn0", bg=ACCENT, fg=INK, border=ACCENT)
    friend = add_layer(objs, card, 0, 0, 480, 480, "peer_friend_btns_visible")
    pill_button(objs, friend, 202, 324, 76, 76, "peer_btn1_text", "peer_btn1")
    pill_button(objs, friend, 296, 324, 76, 76, "peer_btn2_text", "peer_btn2")

    nav = add_layer(objs, card, 0, 0, 480, 480, "peer_nav_visible")
    objs.append(button(nav, 22, 200, 56, 64, "<", bg=CARD, radius=16, size=28, callback="peer_prev"))
    objs.append(button(nav, 402, 200, 56, 64, ">", bg=CARD, radius=16, size=28, callback="peer_next"))
    objs.append(text(nav, 200, 60, 80, 24, "peer_index", size=16, color=DIM, placeholder="1/1"))

    hint = add_layer(objs, card, 150, 408, 180, 56, "peer_caps_hint_visible")
    objs.append(container(hint, 0, 0, 180, 56, bg=CARD, radius=28, border=LINE, border_w=1))
    objs.append(text(hint, 0, 16, 180, 24, "peer_caps_hint", size=18, color="#CFE3F5"))
    objs.append(button(hint, 0, 0, 180, 56, "", radius=28, opacity=0, callback="peer_open_caps"))


def build_caps(objs):
    caps = add_layer(objs, 0, 0, 0, 480, 480, "peer_caps_visible")
    objs.append(text(caps, 90, 52, 300, 32, "peer_caps_title", size=22))
    for i in range(6):
        row = add_layer(objs, caps, 100, 96 + (i % 3) * 68, 280, 60, f"peer_cap_{i}_visible")
        objs.append(container(row, 0, 0, 280, 60, bg=CARD, radius=18))
        objs.append(text(row, 12, 18, 256, 24, f"peer_cap_{i}_text", size=17, align="left"))
        objs.append(button(row, 0, 0, 280, 60, "", radius=18, opacity=0, callback=f"peer_cap_{i}"))
    objs.append(button(caps, 150, 302, 72, 56, "<", bg=CARD, radius=22, size=24, callback="peer_prev"))
    objs.append(button(caps, 258, 302, 72, 56, ">", bg=CARD, radius=22, size=24, callback="peer_next"))
    pill_button(objs, caps, 106, 368, 120, 56, "peer_caps_back_text", "peer_caps_back")
    pill_button(objs, caps, 238, 368, 136, 56, "peer_unpair_text", "peer_unpair")


def build_chat(objs):
    chat = add_layer(objs, 0, 0, 0, 480, 480, "peer_chat_visible")
    objs.append(text(chat, 90, 52, 300, 28, "peer_chat_title", size=18, color=DIM))
    for i in range(4):
        y = 82 + i * 66
        mine = add_layer(objs, chat, 140, y, 260, 64, f"peer_chat_{i}_self_visible")
        objs.append(container(mine, 0, 0, 260, 64, bg="#16324A", radius=16))
        objs.append(text(mine, 12, 8, 236, 24, f"peer_chat_{i}_self_a", size=17, align="left", placeholder=" "))
        objs.append(text(mine, 12, 34, 236, 24, f"peer_chat_{i}_self_b", size=17, align="left", placeholder=" "))
        theirs = add_layer(objs, chat, 80, y, 260, 64, f"peer_chat_{i}_peer_visible")
        objs.append(container(theirs, 0, 0, 260, 64, bg="#3A2410", radius=16))
        objs.append(text(theirs, 12, 8, 236, 24, f"peer_chat_{i}_peer_a", size=17, align="left", placeholder=" "))
        objs.append(text(theirs, 12, 34, 236, 24, f"peer_chat_{i}_peer_b", size=17, align="left", placeholder=" "))
    # Pointer events own this area; no click action can accidentally send twice.
    objs.append(container(chat, 104, 350, 272, 48, bg="#16324A", radius=24, border=ACCENT, border_w=1))
    objs.append(text(chat, 108, 362, 264, 24, "peer_chat_voice_text", size=17))
    pill_button(objs, chat, 116, 404, 120, 48, "peer_chat_auto_text", "peer_chat_auto")
    pill_button(objs, chat, 244, 404, 120, 48, "peer_chat_stop_text", "peer_chat_stop")


def build_modal(objs):
    modal = add_layer(objs, 0, 0, 0, 480, 480, "peer_modal_visible")
    objs.append(container(modal, 0, 0, 480, 480, bg=BG, radius=240))
    objs.append(text(modal, 60, 120, 360, 34, "peer_modal_title", size=24))
    objs.append(text(modal, 90, 166, 300, 72, "peer_modal_code", size=56, color="#FFC53D", placeholder="0000"))
    objs.append(text(modal, 60, 246, 360, 28, "peer_modal_sub", size=18, color=DIM))
    objs.append(text(modal, 190, 280, 100, 24, "peer_modal_timer", size=18, color=ACCENT, placeholder="30s"))
    pill_button(objs, modal, 110, 322, 120, 56, "peer_modal_reject_text", "peer_modal_reject")
    accept = add_layer(objs, modal, 250, 322, 120, 56, "peer_modal_accept_visible")
    pill_button(objs, accept, 0, 0, 120, 56, "peer_modal_accept_text", "peer_modal_accept", bg=ACCENT, fg=INK, border=ACCENT)


def build_show(objs):
    show = add_layer(objs, 0, 0, 0, 480, 480, "peer_show_visible")
    objs.append(container(show, 0, 0, 480, 480, bg=BG, radius=240))
    face = add_layer(objs, show, 0, 0, 480, 480, "peer_face_visible")
    objs.append(container(face, 140, 90, 200, 200, bg="#FFC53D", radius=100))
    objs.append(visible_box(face, 190, 150, 22, 44, "peer_eye_l_open_visible", bg="#14100C", radius=11))
    objs.append(visible_box(face, 180, 168, 42, 10, "peer_eye_l_wink_visible", bg="#14100C", radius=5))
    objs.append(container(face, 268, 150, 22, 44, bg="#14100C", radius=11))
    objs.append(visible_box(face, 200, 226, 80, 18, "peer_mouth_smile_visible", bg="#14100C", radius=9))
    objs.append(visible_box(face, 222, 214, 36, 36, "peer_mouth_o_visible", bg="#14100C", radius=18))
    objs.append(visible_box(face, 330, 120, 46, 60, "peer_hand_visible", bg="#FFC53D", radius=20, border="#14100C", border_w=3))
    objs.append(text(face, 90, 312, 300, 40, "peer_show_caption", size=30))
    lines = add_layer(objs, show, 0, 0, 480, 480, "peer_text_visible")
    for i in range(3):
        objs.append(text(lines, 60, 160 + i * 52, 360, 44, f"peer_show_line_{i}", size=34, placeholder=" "))
    objs.append(button(show, 0, 0, 480, 480, "", opacity=0, callback="peer_show_close"))


def build_perform(objs):
    perform = add_layer(objs, 0, 0, 0, 480, 480, "peer_perform_visible")
    objs.append(container(perform, 0, 0, 480, 480, bg=BG, radius=240))
    count = add_layer(objs, perform, 0, 0, 480, 480, "peer_perform_count_visible")
    objs.append(text(count, 140, 130, 200, 110, "peer_perform_count", size=96, color=ACCENT, placeholder="3"))
    eq = add_layer(objs, perform, 0, 0, 480, 480, "peer_eq_visible")
    for i in range(5):
        x = 170 + i * 30
        objs.append(visible_box(eq, x, 140, 18, 100, f"peer_eq_{i}_tall_visible", bg=ACCENT, radius=9))
        objs.append(visible_box(eq, x, 200, 18, 40, f"peer_eq_{i}_short_visible", bg=ACCENT, radius=9))
    objs.append(text(perform, 70, 270, 340, 34, "peer_perform_caption", size=24))
    pill_button(objs, perform, 180, 330, 120, 56, "peer_perform_stop_text", "peer_perform_stop")


def main():
    objs = [container(-1, 0, 0, 480, 480, name="root")]
    objs.append(container(0, 6, 6, 468, 468, bg=BG, radius=234, border=LINE, border_w=2))
    build_home(objs)
    build_caps(objs)
    build_chat(objs)
    build_modal(objs)
    build_show(objs)
    build_perform(objs)
    top_notice(objs, 0, name="peer_notice", font=DEJAVU_SANS, title="Partner", message="-")
    for obj in objs:
        if obj["type"] in ("label", "button"):
            if obj.get("bind") in ("peer_perform_count", "peer_modal_code"):
                obj["font_charset"] = " 0123456789"
            else:
                obj.setdefault("font_charset", ASCII_PRINTABLE)
    write_scene(scene_out_path(HERE, "peer_480.json"), "peer", objs, font=DEJAVU_SANS)


if __name__ == "__main__":
    main()
