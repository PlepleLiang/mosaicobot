/* Phone-created Works bridge. Existing runtime owns jobs and display cleanup.
 * LAN-only development interface; NOT authenticated pairing or a Lua sandbox. */
#include "http_server_priv.h"
#include "app_registry.h"
#include "works_runtime.h"
#include "claw_paths.h"
#include <dirent.h>
#include <errno.h>
#include <sys/stat.h>
#include <stdio.h>
#include <string.h>

static bool phone_id(const char *id)
{
    return works_runtime_phone_id_is_valid(id);
}

static esp_err_t snapshot(httpd_req_t *req, const char *id, esp_err_t action_err)
{
    works_runtime_item_snapshot_t item = {0};
    esp_err_t err = works_runtime_get_by_id(id, &item);
    const char *names[] = {"stopped", "queued", "running", "stopping", "failed"};
    cJSON *root = cJSON_CreateObject();
    if (!root) return httpd_resp_send_500(req);
    bool ok = action_err == ESP_OK && (err == ESP_OK || err == ESP_ERR_NOT_FOUND);
    if (!cJSON_AddBoolToObject(root, "ok", ok) ||
        !cJSON_AddNumberToObject(root, "protocol", 1) ||
        !cJSON_AddStringToObject(root, "id", id) ||
        !cJSON_AddStringToObject(root, "state", err == ESP_OK ? names[item.state] : "not_found") ||
        !cJSON_AddStringToObject(root, "error", action_err != ESP_OK ? esp_err_to_name(action_err) : item.last_error)) {
        cJSON_Delete(root);
        return httpd_resp_send_500(req);
    }
    if (!ok) httpd_resp_set_status(req, "409 Conflict");
    return http_server_send_json_response(req, root);
}

static esp_err_t add_directory_item(cJSON *items, const char *id,
                                    const char *display_name, bool builtin,
                                    const char *fallback_state)
{
    works_runtime_item_snapshot_t snapshot = {0};
    esp_err_t err = works_runtime_get_by_id(id, &snapshot);
    const char *names[] = {"stopped", "queued", "running", "stopping", "failed"};
    if (err != ESP_OK && err != ESP_ERR_NOT_FOUND) return err;
    cJSON *item = cJSON_CreateObject();
    if (!item) return ESP_ERR_NO_MEM;
    if (!cJSON_AddStringToObject(item, "id", id) ||
        !cJSON_AddStringToObject(item, "display_name", display_name ? display_name : id) ||
        !cJSON_AddBoolToObject(item, "builtin", builtin) ||
        !cJSON_AddStringToObject(item, "state", err == ESP_OK ? names[snapshot.state] : fallback_state) ||
        !cJSON_AddItemToArray(items, item)) {
        cJSON_Delete(item);
        return ESP_ERR_NO_MEM;
    }
    return ESP_OK;
}

static esp_err_t collect_directory(const app_registry_entry_t *entry, void *user_ctx)
{
    if (!entry->visible || !entry->entry) return ESP_OK;
    return add_directory_item(user_ctx, entry->app_id, entry->display_name,
                              entry->manage_mode == APP_REGISTRY_MANAGE_MODE_READONLY,
                              "stopped");
}

static esp_err_t add_orphan_directories(cJSON *items)
{
    char root[HTTP_SERVER_PATH_MAX];
    esp_err_t err = claw_paths_join(CLAW_PATH_DATA, "apps", root, sizeof(root));
    if (err != ESP_OK) return err;
    DIR *dir = opendir(root);
    if (!dir) return errno == ENOENT ? ESP_OK : ESP_FAIL;
    struct dirent *entry;
    while ((entry = readdir(dir)) != NULL) {
        if (!phone_id(entry->d_name)) continue;
        bool found = false;
        cJSON *item;
        cJSON_ArrayForEach(item, items) {
            cJSON *id = cJSON_GetObjectItemCaseSensitive(item, "id");
            if (strcmp(id->valuestring, entry->d_name) == 0) { found = true; break; }
        }
        if (found) continue;
        char path[HTTP_SERVER_PATH_MAX];
        struct stat info;
        int length = snprintf(path, sizeof(path), "%s/%s", root, entry->d_name);
        if (length < 0 || (size_t)length >= sizeof(path)) { err = ESP_ERR_INVALID_SIZE; break; }
        if (stat(path, &info) != 0 || !S_ISDIR(info.st_mode)) continue;
        err = add_directory_item(items, entry->d_name, NULL, false, "not_found");
        if (err != ESP_OK) break;
    }
    closedir(dir);
    return err;
}

static esp_err_t works_directory(httpd_req_t *req)
{
    cJSON *root = cJSON_CreateObject();
    if (!root) return httpd_resp_send_500(req);
    cJSON *items = cJSON_AddArrayToObject(root, "items");
    if (!items || !cJSON_AddBoolToObject(root, "ok", true) ||
        !cJSON_AddNumberToObject(root, "protocol", 1) ||
        app_registry_foreach(collect_directory, items) != ESP_OK ||
        add_orphan_directories(items) != ESP_OK) {
        cJSON_Delete(root);
        return httpd_resp_send_500(req);
    }
    return http_server_send_json_response(req, root);
}

static esp_err_t works_get(httpd_req_t *req)
{
    char id[WORKS_RUNTIME_APP_ID_MAX];
    esp_err_t err = http_server_query_get(req, "id", id, sizeof(id));
    if (err == ESP_ERR_NOT_FOUND) return works_directory(req);
    if (err != ESP_OK || !phone_id(id))
        return httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "Invalid phone work id");
    return snapshot(req, id, ESP_OK);
}

static esp_err_t works_post(httpd_req_t *req)
{
    cJSON *root = NULL;
    if (req->content_len > 512 || http_server_parse_json_body(req, &root) != ESP_OK)
        return httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "Invalid command");
    cJSON *id = cJSON_GetObjectItemCaseSensitive(root, "id");
    cJSON *action = cJSON_GetObjectItemCaseSensitive(root, "action");
    if (!cJSON_IsString(id) || !phone_id(id->valuestring) || !cJSON_IsString(action)) {
        cJSON_Delete(root);
        return httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "Invalid command");
    }
    esp_err_t err;
    if (strcmp(action->valuestring, "publish") == 0) {
        works_runtime_item_snapshot_t existing;
        /* Already registered packages are immutable from this API. */
        err = works_runtime_get_by_id(id->valuestring, &existing);
        if (err == ESP_ERR_NOT_FOUND) err = app_registry_publish(id->valuestring);
    } else if (strcmp(action->valuestring, "start") == 0) {
        err = works_runtime_set_running(id->valuestring, true);
    } else if (strcmp(action->valuestring, "stop") == 0) {
        err = works_runtime_set_running(id->valuestring, false);
    } else if (strcmp(action->valuestring, "delete") == 0) {
        err = works_runtime_remove(id->valuestring);
    } else {
        cJSON_Delete(root);
        return httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "Unknown action");
    }
    esp_err_t response = snapshot(req, id->valuestring, err);
    cJSON_Delete(root);
    return response;
}

esp_err_t http_server_register_works_routes(httpd_handle_t server)
{
    /* app_claw_start has initialized app_registry by HTTP startup. */
    esp_err_t init_err = works_runtime_init(NULL);
    if (init_err != ESP_OK) return init_err;
    const httpd_uri_t handlers[] = {
        {.uri="/api/works", .method=HTTP_GET, .handler=works_get},
        {.uri="/api/works", .method=HTTP_POST, .handler=works_post},
    };
    for (size_t i = 0; i < sizeof(handlers) / sizeof(handlers[0]); ++i) {
        esp_err_t err = httpd_register_uri_handler(server, &handlers[i]);
        if (err != ESP_OK) return err;
    }
    return ESP_OK;
}
