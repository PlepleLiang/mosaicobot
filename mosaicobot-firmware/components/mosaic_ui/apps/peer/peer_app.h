#pragma once

#include <stdbool.h>

#include "mosaic_app_catalog.h"

extern const mosaic_app_descriptor_t mosaic_peer_app;

/** Thread-safe: shows an emoji face (is_text=false) or text on the Partner app for 5 s. */
void peer_app_request_show(bool is_text, const char *value);
