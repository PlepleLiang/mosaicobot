#pragma once
#include <stddef.h>
#include "esp_err.h"

/* Completed requests outlive the small working inbox. No automatic deletion:
 * a full/unavailable archive refuses new work instead of replaying old tools. */
esp_err_t peer_rpc_archive_read(const char *peer, const char *id, char *out, size_t size);
esp_err_t peer_rpc_archive_write(const char *peer, const char *id, const char *json);
