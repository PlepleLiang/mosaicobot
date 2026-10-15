#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "esp_attr.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "peer_internal.h"
#include "settings_store.h"
#include "peer_rpc_archive.h"

/* Bounded, persisted inbox. Pending runs become explicit errors after a reboot,
 * never silently replaying a tool that may already have executed. */
#define RPC_SLOTS 4
typedef struct {
    char peer[PEER_ID_LEN];
    char id[33];
    char request[PEER_MANUAL_TEXT_LEN];
    char answer[PEER_MANUAL_TEXT_LEN];
    bool pending;
    bool ok;
} rpc_entry_t;
static EXT_RAM_BSS_ATTR rpc_entry_t s_inbox[RPC_SLOTS];
typedef struct { char peer[PEER_ID_LEN]; char id[33]; bool replied; } outgoing_t;
static outgoing_t s_outgoing[8];
static unsigned s_next, s_out_next;
static bool s_storage_ready = true;

static const char *str(const cJSON *obj, const char *key)
{
    const cJSON *v = cJSON_GetObjectItemCaseSensitive(obj, key);
    return cJSON_IsString(v) ? v->valuestring : "";
}

static int find_inbox(const char *peer, const char *id)
{
    for (int i = 0; i < RPC_SLOTS; ++i)
        if (!strcmp(s_inbox[i].peer, peer) && !strcmp(s_inbox[i].id, id)) return i;
    return -1;
}

static char *entry_json(const rpc_entry_t *e)
{
    cJSON *obj = cJSON_CreateObject();
    if (!obj) return NULL;
    cJSON_AddStringToObject(obj, "peer", e->peer);
    cJSON_AddStringToObject(obj, "id", e->id);
    cJSON_AddStringToObject(obj, "request", e->request);
    cJSON_AddStringToObject(obj, "answer", e->answer);
    cJSON_AddBoolToObject(obj, "pending", e->pending);
    cJSON_AddBoolToObject(obj, "ok", e->ok);
    char *json = cJSON_PrintUnformatted(obj);
    cJSON_Delete(obj);
    return json;
}

static bool save(int slot)
{
    char *json = entry_json(&s_inbox[slot]);
    char key[16]; snprintf(key, sizeof(key), "peer_rpc%d", slot);
    bool ok = json && settings_store_set_string(key, json) == ESP_OK && settings_store_commit() == ESP_OK;
    free(json);
    return ok;
}

static bool archive(int slot)
{
    rpc_entry_t *e = &s_inbox[slot];
    if (!e->id[0]) return true;
    char *json = entry_json(e);
    bool ok = json && peer_rpc_archive_write(e->peer, e->id, json) == ESP_OK;
    free(json);
    return ok;
}

/* NULL with NOT_FOUND is new; corrupt/unavailable storage must fail closed. */
static cJSON *read_archive(const char *peer, const char *id, esp_err_t *err)
{
    char *buf = malloc(3072);
    if (!buf) { *err = ESP_ERR_NO_MEM; return NULL; }
    *err = peer_rpc_archive_read(peer, id, buf, 3072);
    cJSON *obj = *err == ESP_OK ? cJSON_Parse(buf) : NULL;
    free(buf);
    if (*err == ESP_OK && (!obj || strcmp(str(obj, "peer"), peer) || strcmp(str(obj, "id"), id) ||
        !peer_manual_valid(id, str(obj, "request")) || !peer_manual_valid(id, str(obj, "answer")) ||
        cJSON_IsTrue(cJSON_GetObjectItemCaseSensitive(obj, "pending")))) {
        cJSON_Delete(obj); obj = NULL; *err = ESP_FAIL;
    }
    return obj;
}

void peer_rpc_init(void)
{
    s_storage_ready = false;
    char *buf = malloc(3072);
    if (!buf) return;
    s_storage_ready = true;
    for (int i = 0; i < RPC_SLOTS; ++i) {
        char key[16]; snprintf(key, sizeof(key), "peer_rpc%d", i);
        if (settings_store_get_string(key, buf, 3072, "") != ESP_OK) { s_storage_ready = false; continue; }
        cJSON *obj = cJSON_Parse(buf);
        if (peer_manual_valid(str(obj, "id"), str(obj, "request"))) {
            rpc_entry_t *e = &s_inbox[i];
            peer_copy(e->peer, sizeof(e->peer), str(obj, "peer"));
            peer_copy(e->id, sizeof(e->id), str(obj, "id"));
            peer_copy(e->request, sizeof(e->request), str(obj, "request"));
            peer_copy(e->answer, sizeof(e->answer), str(obj, "answer"));
            e->ok = cJSON_IsTrue(cJSON_GetObjectItemCaseSensitive(obj, "ok"));
            if (cJSON_IsTrue(cJSON_GetObjectItemCaseSensitive(obj, "pending"))) {
                e->ok = false;
                peer_copy(e->answer, sizeof(e->answer), "上次处理因设备重启中断，请确认后发送新消息，避免重复操作。");
                (void)save(i);
            }
        } else if (buf[0] && (!obj || str(obj, "id")[0])) s_storage_ready = false;
        cJSON_Delete(obj);
    }
    free(buf);
}

void peer_rpc_receive(const char *peer_id, const char *name, const char *payload, bool reply, char *out, size_t size)
{
    cJSON *obj = cJSON_Parse(payload);
    const char *id = str(obj, "message_id"), *text = str(obj, "text"), *to = str(obj, "reply_to");
    const char *status = str(obj, "status");
    if (!peer_manual_valid(id, text) || (reply && (!peer_manual_valid(to, "x") ||
        (strcmp(status, "ok") && strcmp(status, "error")))) || (!reply && (to[0] || (status[0] && strcmp(status, "ok"))))) {
        cJSON_Delete(obj); peer_reply_error(out, size, "bad_args", "agent packet"); return;
    }
    peer_lock();
    if (reply) {
        outgoing_t *found = NULL;
        for (int i = 0; i < 8; ++i) {
            if (!strcmp(s_outgoing[i].peer, peer_id) && !strcmp(s_outgoing[i].id, to)) { found = &s_outgoing[i]; break; }
        }
        if (!found) {
            peer_unlock(); cJSON_Delete(obj); peer_reply_error(out, size, "bad_args", "unknown request"); return;
        }
        if (!found->replied) {
            found->replied = true;
            peer_manual_append_locked(peer_id, name, text, false);
        }
        peer_unlock(); cJSON_Delete(obj); peer_open_app(); snprintf(out, size, "{\"ok\":true}"); return;
    }
    int slot = find_inbox(peer_id, id);
    peer_chat_msg_t msg = { .agent_request = true };
    peer_copy(msg.peer_id, sizeof(msg.peer_id), peer_id);
    peer_copy(msg.message_id, sizeof(msg.message_id), id);
    peer_copy(msg.text, sizeof(msg.text), text);
    if (slot >= 0) {
        if (strcmp(s_inbox[slot].request, text)) {
            peer_unlock(); cJSON_Delete(obj); peer_reply_error(out, size, "bad_args", "message_id conflict"); return;
        }
        if (s_inbox[slot].pending) {
            peer_unlock(); cJSON_Delete(obj); snprintf(out, size, "{\"ok\":true}"); return;
        }
        msg.reply_only = true;
    } else {
        if (!s_storage_ready) {
            peer_unlock(); cJSON_Delete(obj); peer_reply_error(out, size, "failed", "inbox unavailable"); return;
        }
        esp_err_t archive_err;
        cJSON *cached = read_archive(peer_id, id, &archive_err);
        if (cached) {
            bool same = !strcmp(str(cached, "request"), text);
            cJSON_Delete(cached);
            if (!same) {
                peer_unlock(); cJSON_Delete(obj); peer_reply_error(out, size, "bad_args", "message_id conflict"); return;
            }
            msg.reply_only = true;
        } else if (archive_err != ESP_ERR_NOT_FOUND) {
            peer_unlock(); cJSON_Delete(obj); peer_reply_error(out, size, "failed", "archive unavailable"); return;
        }
    }
    if (slot < 0 && !msg.reply_only) {
        for (int n = 0; n < RPC_SLOTS; ++n) {
            int candidate = (s_next + n) % RPC_SLOTS;
            if (!s_inbox[candidate].pending) { slot = candidate; s_next = candidate + 1; break; }
        }
        if (slot < 0 || uxQueueSpacesAvailable(g_peer.chat_q) == 0) {
            peer_unlock(); cJSON_Delete(obj); peer_reply_error(out, size, "busy", "Agent queue full"); return;
        }
        rpc_entry_t *e = &s_inbox[slot];
        /* Archive before eviction, including responses whose ACK was lost. */
        if (!archive(slot)) {
            peer_unlock(); cJSON_Delete(obj); peer_reply_error(out, size, "failed", "archive storage"); return;
        }
        memset(e, 0, sizeof(*e));
        peer_copy(e->peer, sizeof(e->peer), peer_id); peer_copy(e->id, sizeof(e->id), id);
        peer_copy(e->request, sizeof(e->request), text); e->pending = true;
        if (!save(slot)) {
            memset(e, 0, sizeof(*e)); peer_unlock(); cJSON_Delete(obj); peer_reply_error(out, size, "failed", "inbox storage"); return;
        }
    }
    bool queued = xQueueSend(g_peer.chat_q, &msg, 0) == pdTRUE;
    if (queued && !msg.reply_only) peer_manual_append_locked(peer_id, name, text, false);
    if (!queued && !msg.reply_only) {
        memset(&s_inbox[slot], 0, sizeof(s_inbox[slot])); (void)save(slot);
    }
    peer_unlock(); cJSON_Delete(obj);
    if (queued) { peer_open_app(); snprintf(out, size, "{\"ok\":true}"); }
    else peer_reply_error(out, size, "busy", "Agent queue full");
}

bool peer_rpc_track_outgoing(const peer_cmd_t *cmd, const char *name)
{
    peer_lock();
    for (int i = 0; i < 8; ++i) {
        if (!strcmp(s_outgoing[i].peer, cmd->peer_id) && !strcmp(s_outgoing[i].id, cmd->message_id)) {
            peer_unlock(); return false;
        }
    }
    outgoing_t *e = &s_outgoing[s_out_next++ % 8];
    memset(e, 0, sizeof(*e)); peer_copy(e->peer, sizeof(e->peer), cmd->peer_id); peer_copy(e->id, sizeof(e->id), cmd->message_id);
    peer_manual_append_locked(cmd->peer_id, name, cmd->arg, true);
    peer_unlock();
    return true;
}

void peer_rpc_complete(const peer_chat_msg_t *msg, const char *text, bool ok)
{
    peer_lock();
    int slot = find_inbox(msg->peer_id, msg->message_id);
    if (slot >= 0) {
        rpc_entry_t *e = &s_inbox[slot];
        peer_copy(e->answer, sizeof(e->answer), text); e->ok = ok; e->pending = false;
        if (!save(slot)) { e->ok = false; peer_copy(e->answer, sizeof(e->answer), "Agent 已处理但结果未能保存，请先确认状态，不要重复操作。"); }
        peer_entry_t *friend = peer_registry_find(&g_peer.registry, msg->peer_id);
        if (friend && friend->is_friend) peer_manual_append_locked(msg->peer_id, friend->ep.name, e->answer, true);
    }
    peer_unlock();
}

void peer_rpc_deliver(const peer_chat_msg_t *msg)
{
    peer_lock();
    int slot = find_inbox(msg->peer_id, msg->message_id);
    if (slot >= 0 && s_inbox[slot].pending) { peer_unlock(); return; }
    esp_err_t err;
    cJSON *cached = slot < 0 ? read_archive(msg->peer_id, msg->message_id, &err) : NULL;
    if (slot < 0 && !cached) { peer_unlock(); return; }
    cJSON *obj = cJSON_CreateObject();
    if (obj) {
        cJSON_AddStringToObject(obj, "message_id", msg->message_id);
        cJSON_AddStringToObject(obj, "reply_to", msg->message_id);
        cJSON_AddStringToObject(obj, "text", slot >= 0 ? s_inbox[slot].answer : str(cached, "answer"));
        bool ok = slot >= 0 ? s_inbox[slot].ok : cJSON_IsTrue(cJSON_GetObjectItemCaseSensitive(cached, "ok"));
        cJSON_AddStringToObject(obj, "status", ok ? "ok" : "error");
    }
    cJSON_Delete(cached);
    peer_unlock();
    char *payload = obj ? cJSON_PrintUnformatted(obj) : NULL;
    cJSON_Delete(obj);
    if (!payload) return;
    const char *keys[] = {"type", "payload"};
    const char *values[] = {"agent_reply", payload};
    char out[128];
    bool delivered = false;
    for (int n = 0; n < 3; ++n) {
        if (peer_call_friend(msg->peer_id, "peer_event", keys, values, 2, out, sizeof(out), PEER_CALL_TIMEOUT_MS) == ESP_OK && peer_reply_is_ok(out, NULL, 0)) { delivered = true; break; }
        vTaskDelay(pdMS_TO_TICKS(1000));
    }
    free(payload);
    if (!delivered) peer_set_message("Agent 已回复，等待伙伴重试接收");
}
