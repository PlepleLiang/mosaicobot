#include <stdio.h>

#include "cap_mcp_server.h"
#include "esp_log.h"
#include "mcp_mdns.h"
#include "mdns.h"
#include "peer_core.h"
#include "peer_server.h"

static const char *TAG = "peer_server";
static bool s_ready;

esp_err_t peer_tools_register(void);

esp_err_t peer_server_start(const char *self_id, const char *self_name)
{
    if (s_ready) return ESP_OK;

    char instance[PEER_NAME_LEN + 8];
    snprintf(instance, sizeof(instance), "%s-%.4s", self_name, self_id);
    mcp_mdns_config_t cfg = {
        .hostname = "",
        .instance_name = instance,
        .endpoint = PEER_ENDPOINT,
        .server_port = PEER_PORT,
    };
    const char *stage = "mdns config";
    esp_err_t err = mcp_mdns_set_config(&cfg);
    if (err != ESP_OK) goto failed;
    stage = "mcp init";
    err = cap_mcp_server_init();
    if (err != ESP_OK) goto rollback;
    stage = "tools";
    err = peer_tools_register();
    if (err != ESP_OK) goto rollback;
    stage = "mcp start";
    err = cap_mcp_server_start();
    if (err != ESP_OK) goto rollback;

    /* All six identity fields are required for the phone to accept the service. */
    peer_txt_item_t items[6];
    size_t count = peer_txt_build(self_id, self_name, "mosaico", items);
    for (size_t i = 0; i < count; i++) {
        stage = items[i].key;
        err = mdns_service_txt_item_set(MCP_MDNS_SERVICE_TYPE, MCP_MDNS_SERVICE_PROTO,
                                       items[i].key, items[i].value);
        if (err != ESP_OK) goto rollback;
    }
    s_ready = true;
    ESP_LOGI(TAG, "partner MCP server on :%d/%s as %s", PEER_PORT, PEER_ENDPOINT, instance);
    return ESP_OK;

rollback:
    /* Destroys the engine and its tools, including partial registrations. */
    {
        esp_err_t cleanup = cap_mcp_server_deinit();
        if (cleanup != ESP_OK) ESP_LOGW(TAG, "rollback: %s", esp_err_to_name(cleanup));
    }
failed:
    ESP_LOGE(TAG, "%s failed: %s", stage, esp_err_to_name(err));
    return err;
}
