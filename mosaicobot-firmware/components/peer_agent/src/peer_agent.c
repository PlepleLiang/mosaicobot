#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/time.h>

#include "esp_check.h"
#include "esp_attr.h"
#include "esp_heap_caps.h"
#include "esp_log.h"
#include "esp_random.h"
#include "peer_internal.h"
#include "settings_store.h"
#include "wifi_manager.h"

static const char *TAG = "peer_agent";

EXT_RAM_BSS_ATTR peer_state_t g_peer;
static bool s_started;
static esp_err_t s_startup_error;

void peer_lock(void) { xSemaphoreTake(g_peer.lock, portMAX_DELAY); }
void peer_unlock(void) { xSemaphoreGive(g_peer.lock); }

int64_t peer_now_ms(void)
{
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return (int64_t)tv.tv_sec * 1000 + tv.tv_usec / 1000;
}

int64_t peer_agent_now_ms(void) { return peer_now_ms(); }
uint32_t peer_rng(void) { return esp_random(); }

void peer_set_message(const char *fmt, ...)
{
    char text[192];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(text, sizeof(text), fmt, ap);
    va_end(ap);
    peer_lock();
    peer_copy(g_peer.message, sizeof(g_peer.message), text);
    g_peer.message_seq++;
    peer_unlock();
    ESP_LOGI(TAG, "%s", text);
}

void peer_notify(peer_notice_t notice)
{
    const peer_agent_ui_ops_t *ui = g_peer.ui;
    if (ui != NULL && ui->notify != NULL) {
        ui->notify(notice);
    }
}

void peer_open_app(void)
{
    const peer_agent_ui_ops_t *ui = g_peer.ui;
    if (ui != NULL && ui->open_app != NULL) {
        ui->open_app();
    }
}

void peer_agent_set_ui_ops(const peer_agent_ui_ops_t *ops) { g_peer.ui = ops; }

void peer_save_friends(void)
{
    peer_lock();
    char *json = peer_registry_friends_json(&g_peer.registry);
    peer_unlock();
    if (json == NULL) {
        return;
    }
    esp_err_t err = settings_store_set_string("peer_friends", json);
    if (err == ESP_OK) {
        err = settings_store_commit();
    }
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "save friends failed: %s", esp_err_to_name(err));
    }
    free(json);
}

void peer_describe_error(const char *peer_name, const char *kind, char *out, size_t size)
{
    if (strcmp(kind, "unauthorized") == 0) {
        snprintf(out, size, "%s 已解除好友，需要重新认识", peer_name);
    } else if (strcmp(kind, "denied") == 0) {
        snprintf(out, size, "%s 没有同意", peer_name);
    } else if (strcmp(kind, "body_offline") == 0) {
        snprintf(out, size, "%s 的身体没连上", peer_name);
    } else if (strcmp(kind, "offline") == 0) {
        snprintf(out, size, "%s 没有回应，稍后再试", peer_name);
    } else if (strcmp(kind, "busy") == 0) {
        snprintf(out, size, "%s 正忙，稍后再试", peer_name);
    } else {
        snprintf(out, size, "%s 没能完成", peer_name);
    }
}

void peer_chat_append_locked(bool from_self, const char *text)
{
    ++g_peer.chat_revision;
    if (g_peer.chat_count == PEER_CHAT_LINES) {
        memmove(&g_peer.chat[0], &g_peer.chat[1], sizeof(g_peer.chat[0]) * (PEER_CHAT_LINES - 1));
        g_peer.chat_count--;
    }
    peer_chat_line_t *line = &g_peer.chat[g_peer.chat_count++];
    line->from_self = from_self;
    peer_copy(line->text, sizeof(line->text), text);
}

void peer_manual_append_locked(const char *peer_id, const char *name, const char *text, bool from_self)
{
    ++g_peer.manual_epoch;
    g_peer.chat_active = false;
    if (strcmp(g_peer.chat_peer_id, peer_id) != 0) g_peer.chat_count = 0;
    peer_copy(g_peer.chat_peer_id, sizeof(g_peer.chat_peer_id), peer_id);
    peer_copy(g_peer.chat_peer_name, sizeof(g_peer.chat_peer_name), name);
    g_peer.chat_turn = 0;
    peer_chat_append_locked(from_self, text);
    if (!from_self) ++g_peer.chat_received_seq;
}

esp_err_t peer_agent_send_message(const char *peer_id, const char *text, const char *message_id)
{
    if (!peer_manual_valid(message_id, text)) return ESP_ERR_INVALID_ARG;
    peer_cmd_t cmd = {.type = PEER_CMD_MESSAGE};
    peer_copy(cmd.peer_id, sizeof(cmd.peer_id), peer_id);
    peer_copy(cmd.arg, sizeof(cmd.arg), text);
    peer_copy(cmd.message_id, sizeof(cmd.message_id), message_id);
    peer_agent_stop_chat();
    peer_lock();
    if (g_peer.manual_send_status == 1) { peer_unlock(); return ESP_ERR_INVALID_STATE; }
    g_peer.manual_send_status = 1;
    peer_unlock();
    esp_err_t err = peer_post(&cmd);
    if (err != ESP_OK) { peer_lock(); g_peer.manual_send_status = -1; peer_unlock(); }
    return err;
}

esp_err_t peer_agent_open_chat(const char *peer_id)
{
    peer_lock();
    peer_entry_t *entry = peer_registry_find(&g_peer.registry, peer_id);
    if (!entry || !entry->is_friend) { peer_unlock(); return ESP_ERR_INVALID_ARG; }
    if (strcmp(g_peer.chat_peer_id, peer_id) != 0) {
        if (g_peer.chat_active) { peer_unlock(); return ESP_ERR_INVALID_STATE; }
        g_peer.chat_count = 0; g_peer.chat_turn = 0;
        ++g_peer.chat_revision;
        peer_copy(g_peer.chat_peer_id, sizeof(g_peer.chat_peer_id), peer_id);
        peer_copy(g_peer.chat_peer_name, sizeof(g_peer.chat_peer_name), entry->ep.name);
    }
    peer_unlock();
    return ESP_OK;
}

static void load_identity_and_friends(void)
{
    char id[PEER_ID_LEN] = {0};
    if (settings_store_get_string("peer_id", id, sizeof(id), "") != ESP_OK || strlen(id) != PEER_ID_LEN - 1) {
        peer_random_hex(id, PEER_ID_LEN - 1, peer_rng);
        if (settings_store_set_string("peer_id", id) == ESP_OK) {
            (void)settings_store_commit();
        }
    }
    peer_copy(g_peer.self_id, sizeof(g_peer.self_id), id);
    peer_copy(g_peer.self_name, sizeof(g_peer.self_name), "Mosaico");
    peer_registry_init(&g_peer.registry, g_peer.self_id);
    peer_pairing_init(&g_peer.pairing);

    char *json = calloc(1, 2048);
    if (json != NULL && settings_store_get_string("peer_friends", json, 2048, "[]") == ESP_OK) {
        size_t n = peer_registry_load_friends_json(&g_peer.registry, json);
        ESP_LOGI(TAG, "loaded %u partner friends", (unsigned)n);
    }
    free(json);
}

esp_err_t peer_agent_start(void)
{
    if (s_started) {
        return ESP_OK;
    }
    if (g_peer.lock == NULL) {
        g_peer.lock = xSemaphoreCreateMutex();
    }
    if (g_peer.prompt_done == NULL) {
        g_peer.prompt_done = xSemaphoreCreateBinary();
    }
    esp_err_t err = ESP_ERR_NO_MEM;
    if (g_peer.lock == NULL || g_peer.prompt_done == NULL) goto failed;
    load_identity_and_friends();
    err = peer_worker_start();
    if (err != ESP_OK) goto failed;
    err = peer_net_start();
    if (err != ESP_OK) goto failed;
    s_started = true;
    __atomic_store_n(&s_startup_error, ESP_OK, __ATOMIC_RELAXED);
    ESP_LOGI(TAG, "started id=%s", g_peer.self_id);
    return ESP_OK;
failed:
    __atomic_store_n(&s_startup_error, err, __ATOMIC_RELAXED);
    ESP_LOGE(TAG, "startup failed: %s, internal free=%u largest=%u", esp_err_to_name(err),
             (unsigned)heap_caps_get_free_size(MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT),
             (unsigned)heap_caps_get_largest_free_block(MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT));
    return err;
}

static void fill_view(peer_view_t *v, const peer_entry_t *e)
{
    memset(v, 0, sizeof(*v));
    peer_copy(v->id, sizeof(v->id), e->ep.id);
    peer_copy(v->name, sizeof(v->name), e->ep.name);
    peer_copy(v->kind, sizeof(v->kind), e->ep.kind);
    v->online = e->online;
    v->is_friend = e->is_friend;
    v->needs_repair = e->needs_repair;
    v->compatible = !e->online || peer_version_compatible(e->ep.version);
    v->last_seen_ms = e->last_seen_ms;
    v->cap_count = e->cap_count;
    memcpy(v->caps, e->caps, sizeof(v->caps));
}

void peer_agent_get_snapshot(peer_agent_snapshot_t *out)
{
    memset(out, 0, sizeof(*out));
    out->now_ms = peer_now_ms();
    /* Wi-Fi is independent of worker/server startup, including allocation failures. */
    wifi_manager_status_t wifi = {0};
    wifi_manager_get_status(&wifi);
    out->network_ready = wifi.sta_connected;
    out->startup_error = __atomic_load_n(&s_startup_error, __ATOMIC_RELAXED);
    if (g_peer.lock == NULL) {
        return;
    }
    peer_lock();
    for (int pass = 0; pass < 3; pass++) {
        for (size_t i = 0; i < PEER_MAX_PEERS; i++) {
            const peer_entry_t *e = &g_peer.registry.entries[i];
            if (!e->in_use) {
                continue;
            }
            bool match = pass == 0 ? (e->online && !e->is_friend)
                       : pass == 1 ? (e->online && e->is_friend)
                                   : (!e->online && e->is_friend);
            if (match && out->peer_count < PEER_MAX_PEERS) {
                fill_view(&out->peers[out->peer_count++], e);
            }
        }
    }
    out->service_ready = out->network_ready && g_peer.service_ready;
    out->service_error = g_peer.service_error;
    out->scan_started_ms = g_peer.scan_started_ms;
    out->prompt = g_peer.prompt;
    if (g_peer.prompt == PEER_PROMPT_PAIR) {
        peer_copy(out->prompt_from_name, sizeof(out->prompt_from_name), g_peer.pairing.in_from_name);
        peer_copy(out->prompt_code, sizeof(out->prompt_code), g_peer.pairing.in_code);
        out->prompt_deadline_ms = g_peer.pairing.in_received_ms + PEER_PAIR_TIMEOUT_MS;
    } else if (g_peer.prompt == PEER_PROMPT_SENSITIVE) {
        peer_copy(out->prompt_from_name, sizeof(out->prompt_from_name), g_peer.prompt_from_name);
        peer_copy(out->prompt_title, sizeof(out->prompt_title), g_peer.prompt_title);
        out->prompt_deadline_ms = g_peer.prompt_deadline_ms;
    }
    if (g_peer.pairing.out_active) {
        out->pairing_out = true;
        peer_entry_t *e = peer_registry_find(&g_peer.registry, g_peer.pairing.out_peer_id);
        peer_copy(out->pairing_peer_name, sizeof(out->pairing_peer_name), e != NULL ? e->ep.name : "伙伴");
        peer_copy(out->pairing_code, sizeof(out->pairing_code), g_peer.pairing.out_code);
        out->pairing_deadline_ms = g_peer.pairing.out_started_ms + PEER_PAIR_TIMEOUT_MS;
    }
    out->chat_active = g_peer.chat_active;
    peer_copy(out->chat_peer_id, sizeof(out->chat_peer_id), g_peer.chat_peer_id);
    out->chat_revision = g_peer.chat_revision;
    out->chat_received_seq = g_peer.chat_received_seq;
    out->manual_send_status = g_peer.manual_send_status;
    peer_copy(out->chat_peer_name, sizeof(out->chat_peer_name), g_peer.chat_peer_name);
    out->chat_turn = g_peer.chat_turn;
    out->chat_count = g_peer.chat_count;
    memcpy(out->chat, g_peer.chat, sizeof(out->chat));
    out->perform_pending = g_peer.perform_pending;
    peer_copy(out->perform_show, sizeof(out->perform_show), g_peer.perform_show);
    peer_copy(out->perform_peer_name, sizeof(out->perform_peer_name), g_peer.perform_peer_name);
    out->perform_start_ms = g_peer.perform_start_ms;
    out->perform_end_ms = g_peer.perform_end_ms;
    peer_copy(out->message, sizeof(out->message), g_peer.message);
    out->message_seq = g_peer.message_seq;
    peer_unlock();
}

static esp_err_t post_simple(peer_cmd_type_t type, const char *peer_id, const char *tool, const char *arg, bool flag)
{
    if (g_peer.work_q == NULL) {
        return ESP_ERR_INVALID_STATE;
    }
    peer_cmd_t cmd = {.type = type, .flag = flag};
    peer_copy(cmd.peer_id, sizeof(cmd.peer_id), peer_id);
    peer_copy(cmd.tool, sizeof(cmd.tool), tool);
    peer_copy(cmd.arg, sizeof(cmd.arg), arg);
    return peer_post(&cmd);
}

esp_err_t peer_agent_meet(const char *peer_id) { return post_simple(PEER_CMD_MEET, peer_id, NULL, NULL, false); }

void peer_agent_cancel_meet(void)
{
    peer_lock();
    g_peer.pairing.out_active = false;
    peer_unlock();
}

void peer_agent_answer_prompt(bool accept)
{
    peer_lock();
    peer_prompt_kind_t kind = g_peer.prompt;
    if (kind == PEER_PROMPT_SENSITIVE) {
        g_peer.prompt_accepted = accept && peer_now_ms() < g_peer.prompt_deadline_ms;
        /* The waiting request owns prompt cleanup; do not admit another modal
           before it has consumed this answer. */
    }
    peer_unlock();
    if (kind == PEER_PROMPT_SENSITIVE) {
        xSemaphoreGive(g_peer.prompt_done);
    } else if (kind == PEER_PROMPT_PAIR) {
        (void)post_simple(PEER_CMD_ANSWER_PAIR, NULL, NULL, NULL, accept);
    }
}

esp_err_t peer_agent_invoke(const char *peer_id, const char *tool, const char *args_json)
{
    return post_simple(PEER_CMD_INVOKE, peer_id, tool, args_json != NULL ? args_json : "{}", false);
}

esp_err_t peer_agent_start_chat(const char *peer_id) { return post_simple(PEER_CMD_CHAT_START, peer_id, NULL, NULL, false); }
void peer_agent_stop_chat(void)
{
    char peer_id[PEER_ID_LEN], conv[PEER_CONV_ID_LEN];
    peer_lock();
    bool active = g_peer.chat_active;
    g_peer.chat_active = false;
    peer_copy(peer_id, sizeof(peer_id), g_peer.chat_peer_id);
    peer_copy(conv, sizeof(conv), g_peer.chat_conv);
    peer_unlock();
    if (active) (void)post_simple(PEER_CMD_CHAT_STOP, peer_id, NULL, conv, false);
}
esp_err_t peer_agent_perform(const char *peer_id, const char *show) { return post_simple(PEER_CMD_PERFORM, peer_id, show, NULL, false); }
esp_err_t peer_agent_unpair(const char *peer_id) { return post_simple(PEER_CMD_UNPAIR, peer_id, NULL, NULL, false); }

void peer_agent_perform_finished(void)
{
    peer_lock();
    g_peer.perform_pending = false;
    g_peer.perform_started = false;
    peer_unlock();
    const peer_agent_ui_ops_t *ui = g_peer.ui;
    if (ui != NULL && ui->stop_music != NULL) {
        (void)ui->stop_music();
    }
}
