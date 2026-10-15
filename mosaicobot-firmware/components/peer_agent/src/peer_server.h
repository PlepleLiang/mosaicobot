#pragma once

#include "esp_err.h"

/* Called only by peer_net. A failed attempt rolls back before it can be retried. */
esp_err_t peer_server_start(const char *self_id, const char *self_name);
