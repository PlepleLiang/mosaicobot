#pragma once
#include "esp_err.h"
#include "cJSON.h"
esp_err_t lua_module_gamepad_register(const char *unused);
/* HTTP thread supplies a socket identity; callbacks never invoke Lua or UI. */
bool gamepad_connect(int fd);
void gamepad_disconnect(int fd);
/* Caller owns returned JSON. Invalid frames revoke that socket's input. */
cJSON *gamepad_exchange(int fd, const cJSON *packet);
