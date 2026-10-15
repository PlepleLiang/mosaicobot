#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
cd "$(dirname "$0")"
"${PYTHON:-python3}" gen_scene.py
scene_build_dir=$(mktemp -d)
trap 'rm -rf "$scene_build_dir"' EXIT
generated_dir="${MOSAIC_GENERATED_DIR:-../generated}"
mkdir -p "$generated_dir"
# Let build preserve source-font metadata required by the shared font linker.
"${GSPC:?set GSPC}" build peer_480.json --scene-id 0 --profile "${MOSAIC_SCENE_PROFILE:-../../../common/mosaic_rgb565_auto.yaml}" -o "$scene_build_dir" --bundle "$generated_dir/peer.gspb"
cp "$scene_build_dir"/*_binds.h "$scene_build_dir"/*_actions.h "$scene_build_dir"/*_objects.h "$generated_dir/"
