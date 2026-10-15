/* Exercise production inbox code with only NVS, HTTP and RTOS calls replaced. */
#include <assert.h>
#include <stdio.h>
#include "../src/peer_rpc.c"

peer_state_t g_peer;
static char storage[4][3072], wire[2048];
static struct { char peer[33], id[33], json[3072]; } archives[64];
static int archived;
static bool storage_fail, locked;
static int queued, appended, opened, delivered;
static peer_chat_msg_t last;
static peer_entry_t friend_entry;
static const char *PEER = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
static const char *ID = "11111111111111111111111111111111";

esp_err_t peer_rpc_archive_read(const char *peer, const char *id, char *out, size_t size) {
    for (int i = 0; i < archived; ++i) if (!strcmp(archives[i].peer, peer) && !strcmp(archives[i].id, id)) {
        snprintf(out, size, "%s", archives[i].json); return ESP_OK;
    }
    return ESP_ERR_NOT_FOUND;
}
esp_err_t peer_rpc_archive_write(const char *peer, const char *id, const char *json) {
    if (storage_fail) return ESP_FAIL;
    int slot = archived;
    for (int i = 0; i < archived; ++i) if (!strcmp(archives[i].peer, peer) && !strcmp(archives[i].id, id)) { slot = i; break; }
    assert(slot < 64);
    snprintf(archives[slot].peer, 33, "%s", peer); snprintf(archives[slot].id, 33, "%s", id);
    snprintf(archives[slot].json, 3072, "%s", json);
    if (slot == archived) ++archived;
    return ESP_OK;
}

void peer_lock(void) { assert(!locked); locked = true; }
void peer_unlock(void) { assert(locked); locked = false; }
unsigned uxQueueSpacesAvailable(QueueHandle_t q) { (void)q; return 4 - queued; }
int xQueueSend(QueueHandle_t q, const void *item, unsigned timeout) {
    (void)q; (void)timeout; if (queued == 4) return 0; last = *(const peer_chat_msg_t *)item; ++queued; return pdTRUE;
}
void vTaskDelay(unsigned ticks) { (void)ticks; }
esp_err_t settings_store_set_string(const char *key, const char *value) {
    if (storage_fail) return ESP_ERR_NO_MEM;
    snprintf(storage[atoi(key + 8)], 3072, "%s", value); return ESP_OK;
}
esp_err_t settings_store_commit(void) { return ESP_OK; }
esp_err_t settings_store_get_string(const char *key, char *buf, size_t size, const char *fallback) {
    (void)fallback; snprintf(buf, size, "%s", storage[atoi(key + 8)]); return ESP_OK;
}
void peer_manual_append_locked(const char *peer, const char *name, const char *text, bool self) {
    (void)peer; (void)name; (void)text; (void)self; assert(locked); ++appended;
}
void peer_open_app(void) { ++opened; }
void peer_set_message(const char *fmt, ...) { (void)fmt; }
peer_entry_t *peer_registry_find(peer_registry_t *registry, const char *id) { (void)registry; (void)id; return &friend_entry; }
esp_err_t peer_call_friend(const char *peer, const char *tool, const char *const *keys, const char *const *values,
    size_t count, char *out, size_t size, int timeout) {
    (void)peer; (void)tool; (void)keys; (void)count; (void)timeout;
    assert(!strcmp(values[0], "agent_reply")); snprintf(wire, sizeof(wire), "%s", values[1]);
    ++delivered; snprintf(out, size, "{\"ok\":true}"); return ESP_OK;
}
static void reset(bool keep_storage) {
    memset(s_inbox, 0, sizeof(s_inbox)); memset(s_outgoing, 0, sizeof(s_outgoing));
    memset(&g_peer, 0, sizeof(g_peer)); memset(&friend_entry, 0, sizeof(friend_entry));
    friend_entry.is_friend = true; strcpy(friend_entry.ep.name, "Amoo");
    if (!keep_storage) { memset(storage, 0, sizeof(storage)); archived = 0; }
    s_storage_ready = true;
    queued = appended = opened = delivered = s_next = s_out_next = 0; storage_fail = locked = false;
}
static bool receive(const char *peer, const char *id, const char *text, bool reply, const char *to) {
    cJSON *obj = cJSON_CreateObject();
    cJSON_AddStringToObject(obj, "message_id", id); cJSON_AddStringToObject(obj, "text", text);
    if (reply) { cJSON_AddStringToObject(obj, "reply_to", to); cJSON_AddStringToObject(obj, "status", "ok"); }
    char *payload = cJSON_PrintUnformatted(obj); char out[192];
    peer_rpc_receive(peer, "Amoo", payload, reply, out, sizeof(out));
    free(payload); cJSON_Delete(obj); return peer_reply_is_ok(out, NULL, 0);
}
int main(void) {
    reset(false);
    assert(!receive(PEER, "bad", "hello", false, "")); assert(queued == 0);
    assert(receive(PEER, ID, "hello", false, "")); assert(queued == 1 && appended == 1 && last.agent_request);
    assert(receive(PEER, ID, "hello", false, "")); assert(queued == 1 && appended == 1);
    assert(!receive(PEER, ID, "changed", false, "")); assert(queued == 1);
    peer_chat_msg_t original = last; --queued;
    peer_rpc_complete(&original, "real generated answer", true); peer_rpc_deliver(&original);
    assert(delivered == 1 && strstr(wire, "reply_to") && strstr(wire, "real generated answer"));
    assert(receive(PEER, ID, "hello", false, "")); assert(last.reply_only && appended == 2);
    assert(receive("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", ID, "different friend", false, "")); assert(!last.reply_only);
    reset(true); peer_rpc_init();
    assert(receive("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", ID, "different friend", false, ""));
    assert(last.reply_only); peer_rpc_deliver(&last); assert(strstr(wire, "error"));
    reset(false); storage_fail = true;
    assert(!receive(PEER, ID, "hello", false, "")); assert(queued == 0 && appended == 0);
    storage_fail = false; assert(receive(PEER, ID, "hello", false, ""));
    reset(false);
    for (int i = 1; i <= 4; ++i) { char id[33]; snprintf(id, sizeof(id), "%032d", i); assert(receive(PEER, id, "hello", false, "")); }
    assert(!receive(PEER, ID, "fifth", false, "")); assert(queued == 4);
    reset(false);
    assert(!receive(PEER, ID, "unsolicited", true, ID)); assert(appended == 0);
    peer_cmd_t cmd = {0}; strcpy(cmd.peer_id, PEER); strcpy(cmd.message_id, ID); strcpy(cmd.arg, "question");
    assert(peer_rpc_track_outgoing(&cmd, "Amoo")); assert(!peer_rpc_track_outgoing(&cmd, "Amoo")); assert(appended == 1);
    assert(!receive("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", ID, "wrong friend", true, ID));
    assert(receive(PEER, ID, "answer", true, ID)); assert(appended == 2);
    assert(receive(PEER, ID, "answer", true, ID)); assert(appended == 2);
    reset(false);
    assert(receive(PEER, ID, "first", false, "")); original = last; --queued;
    peer_rpc_complete(&original, "original answer", true);
    for (int i = 1; i <= 12; ++i) {
        char id[33]; snprintf(id, sizeof(id), "%032d", i);
        assert(receive(PEER, id, "later", false, "")); --queued;
        peer_rpc_complete(&last, "later answer", true);
    }
    assert(find_inbox(PEER, ID) < 0 && archived > 0);
    peer_rpc_deliver(&original); assert(strstr(wire, "original answer"));
    assert(receive(PEER, ID, "first", false, "")); assert(last.reply_only); --queued;
    assert(!receive(PEER, ID, "changed", false, ""));
    reset(true); peer_rpc_init();
    assert(receive(PEER, ID, "first", false, "")); assert(last.reply_only); --queued;
    peer_rpc_deliver(&last); assert(strstr(wire, "original answer"));
    storage_fail = true;
    assert(!receive(PEER, "ffffffffffffffffffffffffffffffff", "new", false, ""));
    assert(receive(PEER, ID, "first", false, "")); assert(last.reply_only);
    puts("peer RPC: validation, admission, replay, recovery, storage failure, queue bounds, correlation and duplicate reply passed");
    return 0;
}
