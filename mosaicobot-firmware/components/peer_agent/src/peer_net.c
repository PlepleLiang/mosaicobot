#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "esp_heap_caps.h"
#include "esp_http_client.h"
#include "esp_log.h"
#include "freertos/task.h"
#include "mcp_mdns.h"
#include "mdns.h"
#include "peer_internal.h"
#include "peer_server.h"
#include "wifi_manager.h"

static const char *TAG = "peer_net";
static int s_rpc_id = 1;

static void discover_once(void)
{
    mdns_result_t *results = NULL;
    if (mdns_query_ptr(MCP_MDNS_SERVICE_TYPE, MCP_MDNS_SERVICE_PROTO, 3000, 12, &results) != ESP_OK) {
        return;
    }
    int64_t now = peer_now_ms();
    for (mdns_result_t *r = results; r != NULL; r = r->next) {
        char host[PEER_HOST_LEN] = {0};
        for (mdns_ip_addr_t *a = r->addr; a != NULL; a = a->next) {
            if (a->addr.type == ESP_IPADDR_TYPE_V4) {
                snprintf(host, sizeof(host), IPSTR, IP2STR(&a->addr.u_addr.ip4));
                break;
            }
        }
        if (host[0] == '\0' || r->txt_count == 0) {
            continue;
        }
        peer_txt_item_t items[12];
        size_t count = r->txt_count < 12 ? r->txt_count : 12;
        for (size_t i = 0; i < count; i++) {
            items[i].key = r->txt[i].key;
            items[i].value = r->txt[i].value;
        }
        peer_endpoint_t ep;
        if (!peer_txt_parse(items, count, host, r->port, &ep)) {
            continue;
        }
        bool is_new = false;
        peer_lock();
        peer_entry_t *e = peer_registry_seen(&g_peer.registry, &ep, now, &is_new);
        bool fetch = e != NULL && peer_version_compatible(e->ep.version);
        peer_unlock();
        if (e == NULL) {
            continue;
        }
        if (fetch) {
            peer_cmd_t cmd = {.type = PEER_CMD_FETCH_HELLO};
            peer_copy(cmd.peer_id, sizeof(cmd.peer_id), ep.id);
            (void)peer_post(&cmd);
        }
        if (is_new) {
            peer_set_message("%s 上线了", ep.name);
            peer_notify(PEER_NOTICE_ONLINE);
        }
    }
    mdns_query_results_free(results);
}

static void peer_net_task(void *arg)
{
    (void)arg;
    bool ready = false;
    bool was_connected = false;
    for (;;) {
        wifi_manager_status_t status = {0};
        wifi_manager_get_status(&status);
        if (!status.sta_connected) {
            was_connected = false;
            vTaskDelay(pdMS_TO_TICKS(2000));
            continue;
        }
        if (!ready) {
            esp_err_t err = peer_server_start(g_peer.self_id, g_peer.self_name);
            peer_lock();
            g_peer.service_error = err;
            g_peer.service_ready = ready = (err == ESP_OK);
            peer_unlock();
            if (!ready) {
                ESP_LOGE(TAG, "partner retry in 5s: %s, internal free=%u largest=%u",
                         esp_err_to_name(err),
                         (unsigned)heap_caps_get_free_size(MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT),
                         (unsigned)heap_caps_get_largest_free_block(MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT));
                vTaskDelay(pdMS_TO_TICKS(5000));
                continue;
            }
        }
        peer_lock();
        if (!was_connected) g_peer.scan_started_ms = peer_now_ms();
        peer_unlock();
        was_connected = true;
        discover_once();
        vTaskDelay(pdMS_TO_TICKS(PEER_SCAN_PERIOD_MS));
    }
}

esp_err_t peer_net_start(void)
{
    return xTaskCreate(peer_net_task, "peer_net", 6144, NULL, 4, NULL) == pdPASS ? ESP_OK : ESP_ERR_NO_MEM;
}

esp_err_t peer_http_call(const peer_endpoint_t *ep, const char *tool, const char *const *keys,
                         const char *const *values, size_t count, char *out, size_t out_size, int timeout_ms)
{
    char url[96];
    snprintf(url, sizeof(url), "http://%s:%u/%s", ep->host, (unsigned)ep->port, ep->path);
    char *body = peer_json_call_body(__atomic_fetch_add(&s_rpc_id, 1, __ATOMIC_RELAXED), tool, keys, values, count);
    if (body == NULL) {
        return ESP_ERR_NO_MEM;
    }
    esp_http_client_config_t cfg = {
        .url = url,
        .method = HTTP_METHOD_POST,
        .timeout_ms = timeout_ms,
        .buffer_size = 2048,
    };
    esp_http_client_handle_t client = esp_http_client_init(&cfg);
    if (client == NULL) {
        free(body);
        return ESP_ERR_NO_MEM;
    }
    esp_http_client_set_header(client, "Content-Type", "application/json");
    esp_http_client_set_header(client, "Accept", "application/json");
    size_t body_len = strlen(body);
    esp_err_t err = esp_http_client_open(client, (int)body_len);
    if (err == ESP_OK && esp_http_client_write(client, body, (int)body_len) != (int)body_len) {
        err = ESP_FAIL;
    }
    char *response = NULL;
    if (err == ESP_OK) {
        (void)esp_http_client_fetch_headers(client);
        int status = esp_http_client_get_status_code(client);
        response = calloc(1, PEER_HTTP_MAX_RESPONSE + 1);
        int total = 0;
        int read = 0;
        while (response != NULL && total < PEER_HTTP_MAX_RESPONSE &&
               (read = esp_http_client_read(client, response + total, PEER_HTTP_MAX_RESPONSE - total)) > 0) {
            total += read;
        }
        if (status < 200 || status >= 300 || response == NULL || !peer_json_tool_text(response, out, out_size)) {
            err = ESP_ERR_INVALID_RESPONSE;
        }
    }
    free(response);
    free(body);
    esp_http_client_close(client);
    esp_http_client_cleanup(client);
    return err;
}

esp_err_t peer_call_friend(const char *peer_id, const char *tool, const char *const *keys,
                           const char *const *values, size_t count, char *out, size_t out_size, int timeout_ms)
{
    peer_endpoint_t ep;
    char token[PEER_TOKEN_LEN];
    peer_lock();
    peer_entry_t *e = peer_registry_find(&g_peer.registry, peer_id);
    bool is_friend = e != NULL && e->is_friend;
    bool reachable = is_friend && e->ep.host[0] != '\0' && peer_version_compatible(e->ep.version);
    bool allowed = is_friend && (peer_cap_allowed(e->ep.kind, tool) || strcmp(tool, "peer_say") == 0 ||
                                strcmp(tool, "peer_event") == 0 || strcmp(tool, "peer_unpair") == 0);
    if (is_friend) {
        ep = e->ep;
        peer_copy(token, sizeof(token), e->token);
    }
    peer_unlock();
    if (!is_friend) {
        peer_reply_error(out, out_size, "unauthorized", "not a friend");
        return ESP_ERR_INVALID_STATE;
    }
    if (!allowed) {
        peer_reply_error(out, out_size, "unknown_tool", tool);
        return ESP_ERR_NOT_SUPPORTED;
    }
    if (!reachable) {
        peer_reply_error(out, out_size, "offline", "");
        return ESP_ERR_NOT_FOUND;
    }
    const char *k[2 + PEER_MAX_CALL_ARGS];
    const char *v[2 + PEER_MAX_CALL_ARGS];
    k[0] = "peer_id";
    v[0] = g_peer.self_id;
    k[1] = "peer_token";
    v[1] = token;
    size_t n = count < PEER_MAX_CALL_ARGS ? count : PEER_MAX_CALL_ARGS;
    for (size_t i = 0; i < n; i++) {
        k[2 + i] = keys[i];
        v[2 + i] = values[i];
    }
    esp_err_t err = peer_http_call(&ep, tool, k, v, 2 + n, out, out_size, timeout_ms);
    if (err != ESP_OK) {
        peer_reply_error(out, out_size, "offline", esp_err_to_name(err));
        return err;
    }
    char kind[24];
    if (!peer_reply_is_ok(out, kind, sizeof(kind)) && strcmp(kind, "unauthorized") == 0) {
        peer_lock();
        peer_registry_remove_friend(&g_peer.registry, peer_id);
        peer_entry_t *gone = peer_registry_find(&g_peer.registry, peer_id);
        if (gone != NULL) {
            gone->needs_repair = true;
        }
        peer_unlock();
        peer_save_friends();
    }
    return ESP_OK;
}
