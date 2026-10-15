// SPDX-License-Identifier: Apache-2.0
#pragma once

#define GAME_NAME "snake"
enum { GAME_WIDTH = 480, GAME_HEIGHT = 480, GAME_TICK_HZ = 30 };
enum { GAME_ACTION_PAUSE = 3, GAME_ACTION_RESET = 4 };
enum {
    SNAKE_GRID = 20,
    SNAKE_CELL = 24,
    SNAKE_MAX = SNAKE_GRID * SNAKE_GRID,
    SNAKE_STEP_TICKS = 6,
    SNAKE_SWIPE_PX = 24
};
