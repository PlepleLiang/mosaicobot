#pragma once
#include "esp_err.h"
esp_err_t mdns_service_txt_item_set(const char *service, const char *proto,
                                    const char *key, const char *value);
