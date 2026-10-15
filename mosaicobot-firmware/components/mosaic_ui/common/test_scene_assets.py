#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Host regression checks for Partner artwork and exclusive camera canvases."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

from PIL import Image, ImageChops, ImageOps

HERE = Path(__file__).resolve().parent
UI = HERE.parent


def load_generator(relative_path):
    path = UI / relative_path
    spec = importlib.util.spec_from_file_location(path.parent.parent.name + "_scene", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def scene_objects(module):
    with patch.object(module, "write_scene") as write_scene:
        # Legacy camera generators also emitted a separate asset scene.
        with patch.object(module, "asset_scene", create=True):
            module.main()
        return write_scene.call_args.args[2]


class SceneAssetsTest(unittest.TestCase):
    def test_discovery_uses_compact_face_and_explicit_network_state(self):
        peer = load_generator("apps/peer/scene/gen_scene.py")
        objects = scene_objects(peer)
        binds = {obj.get("bind"): obj for obj in objects if obj.get("bind")}
        self.assertFalse(any("radar" in bind for bind in binds))
        for bind in ("peer_network_status", "peer_empty_title", "peer_empty_sub", "peer_empty_hint",
                     "peer_scan_activity_visible", "peer_scan_dot_0_visible", "peer_scan_dot_1_visible", "peer_scan_dot_2_visible"):
            self.assertIn(bind, binds)
        target = binds["peer_network_settings_visible"]
        self.assertEqual((target["w"], target["h"]), (184, 64))
        for x in (target["x"], target["x"] + target["w"]):
            for y in (target["y"], target["y"] + target["h"]):
                self.assertLess((x - 240) ** 2 + (y - 240) ** 2, 234 ** 2)
        actions = [obj for obj in objects if obj.get("callback") == "peer_network_settings"]
        self.assertEqual(len(actions), 1)
        self.assertEqual((actions[0]["w"], actions[0]["h"]), (184, 64))

    def test_camera_canvases_have_exclusive_image_resources(self):
        camera = load_generator("apps/camera/scene/gen_scene.py")
        objects = scene_objects(camera)
        images = [obj for obj in objects if obj["type"] == "image"]
        for bind, size in (("camera_canvas", 480), ("camera_album_thumbnail", 48)):
            with self.subTest(bind=bind):
                targets = [obj for obj in images if obj.get("bind") == bind]
                self.assertEqual(len(targets), 1)
                target = targets[0]
                resource = (camera.HERE / target["image"]).resolve()
                readers = [obj for obj in images
                           if (camera.HERE / obj["image"]).resolve() == resource]
                self.assertEqual(len(readers), 1, "Canvas must not share its image with a hidden seed")
                self.assertEqual((target["w"], target["h"]), (size, size))
                with Image.open(resource) as image:
                    self.assertEqual(image.size, (size, size))
                    self.assertEqual(image.convert("RGBA").getchannel("A").getextrema(), (255, 255))

    def test_peer_uses_canonical_amoo_artwork(self):
        peer = load_generator("apps/peer/scene/gen_scene.py")
        portraits = [obj for obj in scene_objects(peer) if obj.get("name") == "amoo_portrait"]
        self.assertEqual(len(portraits), 1)
        self.assertEqual((peer.HERE / portraits[0]["image"]).resolve(),
                         (HERE / "assets/peer/amoo.png").resolve())

    def test_amoo_preserves_source_silhouette_and_proportions(self):
        with Image.open(HERE / "assets/peer/amoo_source.png") as source:
            body = ImageOps.contain(source.convert("RGBA"), (140, 140), Image.Resampling.LANCZOS)
        expected = Image.new("RGBA", (140, 140))
        expected.alpha_composite(body, ((140 - body.width) // 2, (140 - body.height) // 2))
        with Image.open(HERE / "assets/peer/amoo.png") as actual:
            self.assertEqual(actual.size, expected.size)
            self.assertIsNone(ImageChops.difference(actual.convert("RGBA"), expected).getbbox(alpha_only=False))

    def test_partner_launcher_has_dedicated_icon(self):
        hub = load_generator("hub/scene/gen_scenes.py")
        icons = [src for src, _, _ in hub.HUB_IMAGES]
        self.assertIn(f"{hub.ASSETS}/icons/partner.png", icons)
        with Image.open(HERE / "assets/icons/partner.png") as partner:
            self.assertEqual(partner.size, (118, 118))
        icons = [obj for obj in hub.build_hub_objects()
                 if obj["type"] == "image" and obj.get("callback") == "app_peer"]
        self.assertEqual(len(icons), 1)
        self.assertEqual(icons[0]["image"], f"{hub.ASSETS}/icons/partner.png")


if __name__ == "__main__":
    unittest.main()
