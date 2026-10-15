// SPDX-License-Identifier: Apache-2.0
#include <stdlib.h>
#include "game.h"
#include "game_config.h"
#include "mosaico_raylib_fast.h"

typedef enum {
    DIR_UP = 0,
    DIR_RIGHT,
    DIR_DOWN,
    DIR_LEFT,
} dir_t;

struct game_t {
    game_config_t config;
    game_snapshot_t state;
    int16_t body_x[SNAKE_MAX];
    int16_t body_y[SNAKE_MAX];
    int length;
    dir_t dir;
    dir_t queued;
    int food_x;
    int food_y;
    uint32_t rng;
    int32_t swipe_x;
    int32_t swipe_y;
};

static int clamp_coord(int value, int limit)
{
    if (value < 0) return 0;
    if (value >= limit) return limit - 1;
    return value;
}

static bool opposite(dir_t a, dir_t b)
{
    return (a + 2) % 4 == b;
}

static bool occupied(const struct game_t *game, int x, int y, int count)
{
    for (int i = 0; i < count; ++i) {
        if (game->body_x[i] == x && game->body_y[i] == y) return true;
    }
    return false;
}

static void place_food(struct game_t *game)
{
    if (game->length >= SNAKE_MAX) {
        game->food_x = -1;
        game->food_y = -1;
        return;
    }
    for (int attempt = 0; attempt < SNAKE_MAX; ++attempt) {
        game->rng = game->rng * 1664525u + 1013904223u;
        int cell = (int)((game->rng >> 16) % SNAKE_MAX);
        int x = cell % SNAKE_GRID;
        int y = cell / SNAKE_GRID;
        if (!occupied(game, x, y, game->length)) {
            game->food_x = x;
            game->food_y = y;
            return;
        }
    }
    game->food_x = -1;
    game->food_y = -1;
}

static void publish(struct game_t *game)
{
    game->state.alive = game->length > 0 && game->state.alive;
    game->state.length = game->length;
    game->state.head_x = game->body_x[0];
    game->state.head_y = game->body_y[0];
}

static void spawn(struct game_t *game)
{
    game->state = (game_snapshot_t){0};
    game->state.alive = true;
    game->length = 3;
    game->body_x[0] = 10;
    game->body_y[0] = 10;
    game->body_x[1] = 9;
    game->body_y[1] = 10;
    game->body_x[2] = 8;
    game->body_y[2] = 10;
    game->dir = DIR_RIGHT;
    game->queued = DIR_RIGHT;
    game->rng = 1;
    place_food(game);
    publish(game);
}

bool game_create(const game_config_t *config, game_handle_t *ret_handle)
{
    if (!ret_handle) return false;
    *ret_handle = NULL;
    if (!config || !config->width || !config->height) return false;
    game_handle_t game = calloc(1, sizeof(*game));
    if (!game) return false;
    game->config = *config;
    spawn(game);
    *ret_handle = game;
    return true;
}

void game_delete(game_handle_t game)
{
    free(game);
}

void game_reset(game_handle_t game)
{
    if (game) spawn(game);
}

void game_set_paused(game_handle_t game, bool paused)
{
    if (game) game->state.paused = paused;
}

static void queue_dir(struct game_t *game, dir_t next)
{
    if (!opposite(game->dir, next)) game->queued = next;
}

void game_set_pointer(game_handle_t game, int32_t x, int32_t y, bool pressed)
{
    if (!game) return;
    x = clamp_coord(x, game->config.width);
    y = clamp_coord(y, game->config.height);
    bool was_down = game->state.pointer_down;
    game->state.pointer_x = x;
    game->state.pointer_y = y;
    game->state.pointer_down = pressed;
    if (pressed && !was_down) {
        game->swipe_x = x;
        game->swipe_y = y;
        return;
    }
    if (pressed || !was_down) return;
    if (!game->state.alive) {
        spawn(game);
        return;
    }
    int dx = x - game->swipe_x;
    int dy = y - game->swipe_y;
    int adx = dx < 0 ? -dx : dx;
    int ady = dy < 0 ? -dy : dy;
    if (adx < SNAKE_SWIPE_PX && ady < SNAKE_SWIPE_PX) return;
    if (adx >= ady) queue_dir(game, dx > 0 ? DIR_RIGHT : DIR_LEFT);
    else queue_dir(game, dy > 0 ? DIR_DOWN : DIR_UP);
}

static void step(struct game_t *game)
{
    if (!opposite(game->dir, game->queued)) game->dir = game->queued;
    else game->queued = game->dir;

    static const int dxs[] = {0, 1, 0, -1};
    static const int dys[] = {-1, 0, 1, 0};
    int nx = game->body_x[0] + dxs[game->dir];
    int ny = game->body_y[0] + dys[game->dir];
    bool eat = nx == game->food_x && ny == game->food_y;
    int check = eat ? game->length : game->length - 1;
    if (nx < 0 || ny < 0 || nx >= SNAKE_GRID || ny >= SNAKE_GRID ||
        occupied(game, nx, ny, check)) {
        game->state.alive = false;
        return;
    }
    if (eat && game->length < SNAKE_MAX) {
        for (int i = game->length; i > 0; --i) {
            game->body_x[i] = game->body_x[i - 1];
            game->body_y[i] = game->body_y[i - 1];
        }
        game->length++;
        game->state.score++;
    } else {
        for (int i = game->length - 1; i > 0; --i) {
            game->body_x[i] = game->body_x[i - 1];
            game->body_y[i] = game->body_y[i - 1];
        }
    }
    game->body_x[0] = (int16_t)nx;
    game->body_y[0] = (int16_t)ny;
    if (eat) place_food(game);
}

void game_update(game_handle_t game)
{
    if (!game || game->state.paused) return;
    ++game->state.tick;
    if (!game->state.alive) return;
    if (game->state.tick % SNAKE_STEP_TICKS == 0) step(game);
    publish(game);
}

static void cell(int gx, int gy, Color color)
{
    DrawRectangle(gx * SNAKE_CELL + 1, gy * SNAKE_CELL + 1, SNAKE_CELL - 2, SNAKE_CELL - 2, color);
}

bool game_render(game_handle_t game)
{
    if (!game) return false;
    BeginDrawing();
    if (!MosaicoFastFrameAvailable()) {
        EndDrawing();
        return false;
    }
    ClearBackground(BLACK);
    if (game->food_x >= 0) cell(game->food_x, game->food_y, RED);
    for (int i = game->length - 1; i >= 0; --i) {
        cell(game->body_x[i], game->body_y[i], i == 0 ? GREEN : LIME);
    }
    DrawText(TextFormat("SCORE %d", game->state.score), 8, 8, 24, WHITE);
    if (!game->state.alive) {
        DrawText("GAME OVER", 140, 210, 32, WHITE);
        DrawText("TAP TO RESTART", 116, 252, 24, WHITE);
    }
    EndDrawing();
    return true;
}

game_snapshot_t game_read(game_handle_t game)
{
    return game ? game->state : (game_snapshot_t){0};
}
