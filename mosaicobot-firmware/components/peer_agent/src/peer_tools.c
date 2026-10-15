#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "cJSON.h"
#include "cap_mcp_server.h"
#include "claw_cap.h"
#include "esp_log.h"
#include "esp_mcp_data.h"
#include "freertos/task.h"
#include "peer_internal.h"
#include "weather_service.h"

static const char *TAG = "peer_tools";

typedef struct {
    const char *name;
    const char *title;
    bool sensitive;
    const char *params[2];
    size_t param_count;
} exposed_t;

static const exposed_t s_exposed[] = {
    {"take_photo", "拍照", true, {NULL}, 0},
    {"play_music", "放音乐", false, {"name"}, 1},
    {"stop_music", "停止音乐", false, {NULL}, 0},
    {"show_emoji", "显示表情", false, {"name"}, 1},
    {"show_text", "屏幕写字", false, {"text"}, 1},
    {"get_weather", "查天气", false, {NULL}, 0},
    {"vibrate", "震一下", false, {"pattern"}, 1},
};

static const exposed_t *find_exposed(const char *name)
{
    for (size_t i = 0; i < sizeof(s_exposed) / sizeof(s_exposed[0]); i++) {
        if (strcmp(s_exposed[i].name, name) == 0) {
            return &s_exposed[i];
        }
    }
    return NULL;
}

static const char *prop(const esp_mcp_property_list_t *p, const char *name)
{
    const char *v = esp_mcp_property_list_get_property_string(p, name);
    return v != NULL ? v : "";
}

static esp_mcp_value_t reply(const char *text) { return esp_mcp_value_create_string(text); }
static esp_mcp_value_t reply_ok(void) { return reply("{\"ok\":true}"); }

static esp_mcp_value_t reply_error(const char *kind, const char *detail)
{
    char buf[192];
    peer_reply_error(buf, sizeof(buf), kind, detail);
    return reply(buf);
}

static esp_mcp_value_t reply_json(cJSON *root)
{
    char *text = cJSON_PrintUnformatted(root);
    cJSON_Delete(root);
    esp_mcp_value_t value = reply(text != NULL ? text : "{\"ok\":true}");
    free(text);
    return value;
}

static bool authorize(const esp_mcp_property_list_t *p, char *name, size_t name_size)
{
    const char *id = prop(p, "peer_id");
    peer_lock();
    bool ok = peer_registry_check_token(&g_peer.registry, id, prop(p, "peer_token"));
    if (ok && name != NULL) {
        peer_entry_t *e = peer_registry_find(&g_peer.registry, id);
        peer_copy(name, name_size, e != NULL ? e->ep.name : "伙伴");
    }
    peer_unlock();
    return ok;
}

static bool ask_sensitive(const char *from_name, const char *title)
{
    (void)xSemaphoreTake(g_peer.prompt_done, 0);
    peer_lock();
    if (g_peer.prompt != PEER_PROMPT_NONE || g_peer.pairing.out_active) {
        peer_unlock();
        return false;
    }
    g_peer.prompt = PEER_PROMPT_SENSITIVE;
    g_peer.prompt_accepted = false;
    peer_copy(g_peer.prompt_from_name, sizeof(g_peer.prompt_from_name), from_name);
    peer_copy(g_peer.prompt_title, sizeof(g_peer.prompt_title), title);
    g_peer.prompt_deadline_ms = peer_now_ms() + PEER_SENSITIVE_TIMEOUT_MS;
    peer_unlock();
    peer_notify(PEER_NOTICE_REQUEST);
    peer_open_app();
    bool answered = xSemaphoreTake(g_peer.prompt_done, pdMS_TO_TICKS(PEER_SENSITIVE_TIMEOUT_MS)) == pdTRUE;
    peer_lock();
    bool accepted = answered && g_peer.prompt_accepted;
    g_peer.prompt = PEER_PROMPT_NONE;
    peer_unlock();
    return accepted;
}

static esp_err_t vibrate_once(uint32_t duration_ms)
{
    char input[64];
    char output[128];
    claw_cap_call_context_t ctx = {0};
    snprintf(input, sizeof(input), "{\"operation\":\"vibrate\",\"duration_ms\":%u}", (unsigned)duration_ms);
    return claw_cap_call("system_control", input, &ctx, output, sizeof(output));
}

esp_err_t peer_vibrate(const char *pattern)
{
    if (pattern != NULL && strcmp(pattern, "long") == 0) {
        return vibrate_once(400);
    }
    if (pattern != NULL && strcmp(pattern, "double") == 0) {
        esp_err_t err = vibrate_once(80);
        vTaskDelay(pdMS_TO_TICKS(160));
        return err == ESP_OK ? vibrate_once(80) : err;
    }
    return vibrate_once(80);
}

static esp_mcp_value_t weather_reply(void)
{
    weather_service_snapshot_t snap = {0};
    if (weather_service_get_snapshot(&snap) != ESP_OK || !snap.weather_valid) {
        return reply_error("failed", "天气还没有更新");
    }
    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject(root, "ok", true);
    cJSON_AddStringToObject(root, "city", snap.city);
    cJSON_AddNumberToObject(root, "temperature_c", snap.temperature_c);
    cJSON_AddStringToObject(root, "condition", snap.condition);
    return reply_json(root);
}

static bool valid_emoji(const char *name)
{
    return strcmp(name, "smile") == 0 || strcmp(name, "wave") == 0 ||
           strcmp(name, "surprise") == 0 || strcmp(name, "wink") == 0;
}

static esp_mcp_value_t run_local_action(const char *tool, const char *arg, const char *name)
{
    const exposed_t *x = find_exposed(tool);
    if (x == NULL) return reply_error("unknown_tool", "");
    if (x->sensitive && !ask_sensitive(name, x->title)) {
        return reply_error("denied", "");
    }
    peer_set_message("%s 让我：%s", name, x->title);
    bool shows_screen = strcmp(tool, "show_emoji") == 0 || strcmp(tool, "show_text") == 0 || strcmp(tool, "take_photo") == 0;
    if (!shows_screen) {
        peer_notify(PEER_NOTICE_COMMAND);
    }
    const peer_agent_ui_ops_t *ui = g_peer.ui;
    esp_err_t err = ESP_ERR_NOT_SUPPORTED;
    if (strcmp(tool, "take_photo") == 0) {
        char saved[96] = {0};
        if (ui != NULL && ui->take_photo != NULL) {
            err = ui->take_photo(saved, sizeof(saved));
        }
        if (err == ESP_OK) {
            cJSON *root = cJSON_CreateObject();
            cJSON_AddBoolToObject(root, "ok", true);
            cJSON_AddStringToObject(root, "saved", saved);
            return reply_json(root);
        }
    } else if (strcmp(tool, "play_music") == 0) {
        char playing[64] = {0};
        if (ui != NULL && ui->play_music != NULL) {
            err = ui->play_music(arg, playing, sizeof(playing));
        }
        if (err == ESP_OK) {
            cJSON *root = cJSON_CreateObject();
            cJSON_AddBoolToObject(root, "ok", true);
            cJSON_AddStringToObject(root, "playing", playing);
            return reply_json(root);
        }
    } else if (strcmp(tool, "stop_music") == 0) {
        err = (ui != NULL && ui->stop_music != NULL) ? ui->stop_music() : ESP_ERR_NOT_SUPPORTED;
    } else if (strcmp(tool, "show_emoji") == 0) {
        const char *emoji = arg;
        if (!valid_emoji(emoji)) {
            return reply_error("bad_args", "name 取 smile/wave/surprise/wink");
        }
        err = (ui != NULL && ui->show_emoji != NULL) ? ui->show_emoji(emoji) : ESP_ERR_NOT_SUPPORTED;
    } else if (strcmp(tool, "show_text") == 0) {
        char text[181];
        peer_copy(text, sizeof(text), arg);
        if (text[0] == '\0') {
            return reply_error("bad_args", "text 为空");
        }
        err = (ui != NULL && ui->show_text != NULL) ? ui->show_text(text) : ESP_ERR_NOT_SUPPORTED;
    } else if (strcmp(tool, "get_weather") == 0) {
        return weather_reply();
    } else if (strcmp(tool, "vibrate") == 0) {
        err = peer_vibrate(arg);
    }
    if (err == ESP_OK) {
        return reply_ok();
    }
    ESP_LOGW(TAG, "%s failed: %s", tool, esp_err_to_name(err));
    peer_notify(PEER_NOTICE_FAILED);
    peer_set_message("没能%s：%s", x->title, esp_err_to_name(err));
    return reply_error("failed", esp_err_to_name(err));
}

static esp_mcp_value_t run_exposed(const char *tool, const esp_mcp_property_list_t *p)
{
    char name[PEER_NAME_LEN];
    if (!authorize(p, name, sizeof(name))) return reply_error("unauthorized", "");
    const exposed_t *x = find_exposed(tool);
    return run_local_action(tool, x && x->param_count ? prop(p, x->params[0]) : "", name);
}

static esp_err_t local_agent_action(const char *input, const claw_cap_call_context_t *ctx, char *out, size_t size)
{
    char name[PEER_NAME_LEN] = {0};
    if (g_peer.lock && ctx && ctx->session_id && strncmp(ctx->session_id, "peer:", 5) == 0) {
        peer_lock();
        peer_entry_t *e = peer_registry_find(&g_peer.registry, ctx->session_id + 5);
        if (e && e->is_friend) peer_copy(name, sizeof(name), e->ep.name);
        peer_unlock();
    }
    if (!name[0]) { peer_reply_error(out, size, "unauthorized", "伙伴会话已失效"); return ESP_OK; }
    cJSON *json = cJSON_Parse(input ? input : "{}");
    const cJSON *action = cJSON_GetObjectItemCaseSensitive(json, "action");
    const cJSON *arg = cJSON_GetObjectItemCaseSensitive(json, "arg");
    esp_mcp_value_t result = cJSON_IsString(action) ?
        run_local_action(action->valuestring, cJSON_IsString(arg) ? arg->valuestring : "", name) : reply_error("bad_args", "action");
    snprintf(out, size, "%s", result.type == ESP_MCP_VALUE_TYPE_STRING ? result.data.string_value : "{\"ok\":false}");
    esp_mcp_value_destroy(&result);
    cJSON_Delete(json);
    return ESP_OK;
}

esp_err_t peer_local_tools_register(void)
{
    static const claw_cap_descriptor_t tool = {
        .id = "partner_action", .name = "partner_action", .family = "peer_local",
        .description = "执行本机已授权给伙伴的能力。action: take_photo(需本机同意), play_music, stop_music, show_emoji, show_text, get_weather, vibrate。arg:音乐名/表情smile,wave,surprise,wink/文字/震动short,long,double。不能调用其他设备。",
        .kind = CLAW_CAP_KIND_CALLABLE, .cap_flags = CLAW_CAP_FLAG_CALLABLE_BY_LLM,
        .input_schema_json = "{\"type\":\"object\",\"properties\":{\"action\":{\"type\":\"string\"},\"arg\":{\"type\":\"string\"}},\"required\":[\"action\"]}",
        .execute = local_agent_action,
    };
    static const claw_cap_group_t group = { .group_id = "peer_local", .descriptors = &tool, .descriptor_count = 1 };
    return claw_cap_group_exists(group.group_id) ? ESP_OK : claw_cap_register_group(&group);
}

#define EXPOSED_CB(fn, tool) \
    static esp_mcp_value_t fn(const esp_mcp_property_list_t *p) { return run_exposed(tool, p); }

EXPOSED_CB(cb_take_photo, "take_photo")
EXPOSED_CB(cb_play_music, "play_music")
EXPOSED_CB(cb_stop_music, "stop_music")
EXPOSED_CB(cb_show_emoji, "show_emoji")
EXPOSED_CB(cb_show_text, "show_text")
EXPOSED_CB(cb_get_weather, "get_weather")
EXPOSED_CB(cb_vibrate, "vibrate")

static esp_mcp_value_t cb_hello(const esp_mcp_property_list_t *p)
{
    (void)p;
    cJSON *root = cJSON_CreateObject();
    cJSON_AddStringToObject(root, "id", g_peer.self_id);
    cJSON_AddStringToObject(root, "name", g_peer.self_name);
    cJSON_AddStringToObject(root, "kind", "mosaico");
    cJSON_AddStringToObject(root, "v", PEER_PROTOCOL_VERSION);
    cJSON *caps = cJSON_AddArrayToObject(root, "caps");
    for (size_t i = 0; i < sizeof(s_exposed) / sizeof(s_exposed[0]); i++) {
        cJSON *cap = cJSON_CreateObject();
        cJSON_AddStringToObject(cap, "name", s_exposed[i].name);
        cJSON_AddStringToObject(cap, "title", s_exposed[i].title);
        cJSON_AddBoolToObject(cap, "sensitive", s_exposed[i].sensitive);
        cJSON_AddBoolToObject(cap, "available", true);
        cJSON *params = cJSON_AddArrayToObject(cap, "params");
        for (size_t j = 0; j < s_exposed[i].param_count; j++) {
            cJSON_AddItemToArray(params, cJSON_CreateString(s_exposed[i].params[j]));
        }
        cJSON_AddItemToArray(caps, cap);
    }
    return reply_json(root);
}

static esp_mcp_value_t cb_pair_request(const esp_mcp_property_list_t *p)
{
    const char *from_id = prop(p, "from_id");
    const char *from_name = prop(p, "from_name");
    const char *code = prop(p, "code");
    if (from_id[0] == '\0' || strlen(from_id) >= PEER_ID_LEN || strlen(code) != 4 || strspn(code, "0123456789") != 4) {
        return reply_error("bad_args", "from_id/code");
    }
    peer_lock();
    if (g_peer.prompt != PEER_PROMPT_NONE) {
        peer_unlock();
        return reply("{\"status\":\"busy\"}");
    }
    peer_pair_status_t status = peer_pairing_receive(&g_peer.pairing, from_id, from_name[0] != '\0' ? from_name : from_id,
                                                     prop(p, "from_kind"), code, peer_now_ms());
    if (status == PEER_PAIR_PENDING) {
        g_peer.prompt = PEER_PROMPT_PAIR;
    }
    peer_unlock();
    if (status == PEER_PAIR_PENDING) {
        peer_notify(PEER_NOTICE_REQUEST);
        peer_open_app();
    }
    char buf[48];
    snprintf(buf, sizeof(buf), "{\"status\":\"%s\"}", peer_pair_status_str(status));
    return reply(buf);
}

static esp_mcp_value_t cb_pair_result(const esp_mcp_property_list_t *p)
{
    const char *from_id = prop(p, "from_id");
    const char *token = prop(p, "token");
    bool accepted = strcmp(prop(p, "status"), "accepted") == 0;
    if ((!accepted && strcmp(prop(p, "status"), "rejected") != 0) ||
        (accepted && (strlen(token) != 32 || strspn(token, "0123456789abcdef") != 32))) {
        return reply_error("bad_args", "status/token");
    }
    char name[PEER_NAME_LEN];
    char kind[PEER_KIND_LEN];
    peer_lock();
    bool matched = peer_pairing_finish_out(&g_peer.pairing, from_id, prop(p, "code"), accepted, peer_now_ms());
    peer_entry_t *e = peer_registry_find(&g_peer.registry, from_id);
    peer_copy(name, sizeof(name), e != NULL ? e->ep.name : "伙伴");
    peer_copy(kind, sizeof(kind), e != NULL ? e->ep.kind : "other");
    if (matched && accepted) {
        peer_registry_add_friend(&g_peer.registry, from_id, name, kind, token);
    }
    peer_unlock();
    if (!matched) {
        return reply_error("bad_args", "no matching request");
    }
    if (accepted) {
        peer_save_friends();
        peer_set_message("已和 %s 成为好友", name);
    } else {
        peer_set_message("%s 没有同意", name);
    }
    return reply_ok();
}

static esp_mcp_value_t cb_unpair(const esp_mcp_property_list_t *p)
{
    char name[PEER_NAME_LEN];
    if (!authorize(p, name, sizeof(name))) {
        return reply_error("unauthorized", "");
    }
    peer_lock();
    peer_registry_remove_friend(&g_peer.registry, prop(p, "peer_id"));
    peer_unlock();
    peer_save_friends();
    peer_set_message("%s 解除了好友", name);
    return reply_ok();
}

static esp_mcp_value_t cb_say(const esp_mcp_property_list_t *p)
{
    if (!authorize(p, NULL, 0)) {
        return reply_error("unauthorized", "");
    }
    peer_chat_msg_t msg = {.opening = false, .turn = atoi(prop(p, "turn"))};
    peer_copy(msg.conv, sizeof(msg.conv), prop(p, "conv_id"));
    peer_copy(msg.peer_id, sizeof(msg.peer_id), prop(p, "peer_id"));
    peer_copy(msg.text, sizeof(msg.text), prop(p, "text"));
    if (msg.turn < 1 || msg.turn > PEER_CHAT_MAX_TURNS || strlen(prop(p, "conv_id")) != 8 ||
        strspn(prop(p, "conv_id"), "0123456789abcdef") != 8 || msg.text[0] == '\0') {
        return reply_error("bad_args", "turn/conv_id");
    }
    return peer_post_chat(&msg) == ESP_OK ? reply_ok() : reply_error("busy", "chat queue full");
}

static esp_mcp_value_t cb_event(const esp_mcp_property_list_t *p)
{
    char name[PEER_NAME_LEN];
    if (!authorize(p, name, sizeof(name))) {
        return reply_error("unauthorized", "");
    }
    const char *type = prop(p, "type");
    if (strcmp(type, "agent_request") == 0 || strcmp(type, "agent_reply") == 0) {
        char out[192];
        peer_rpc_receive(prop(p, "peer_id"), name, prop(p, "payload"), strcmp(type, "agent_reply") == 0, out, sizeof(out));
        return reply(out);
    }
    if (strcmp(type, "chat_message") == 0) {
        cJSON *payload = cJSON_Parse(prop(p, "payload"));
        cJSON *id = cJSON_GetObjectItemCaseSensitive(payload, "message_id");
        cJSON *text = cJSON_GetObjectItemCaseSensitive(payload, "text");
        if (!cJSON_IsString(id) || !cJSON_IsString(text) || !peer_manual_valid(id->valuestring, text->valuestring)) {
            cJSON_Delete(payload);
            return reply_error("bad_args", "chat_message");
        }
        char key[PEER_ID_LEN * 2];
        snprintf(key, sizeof(key), "%s/%s", prop(p, "peer_id"), id->valuestring);
        peer_lock();
        for (size_t i = 0; i < 64; ++i) {
            if (strcmp(g_peer.received_ids[i], key) == 0) {
                peer_unlock(); cJSON_Delete(payload); return reply_ok();
            }
        }
        peer_copy(g_peer.received_ids[g_peer.received_next++ % 64], sizeof(key), key);
        peer_manual_append_locked(prop(p, "peer_id"), name, text->valuestring, false);
        peer_unlock();
        cJSON_Delete(payload);
        peer_open_app();
        return reply_ok();
    }
    if (strcmp(type, "chat_end") == 0) {
        peer_lock();
        cJSON *payload = cJSON_Parse(prop(p, "payload"));
        cJSON *conv = cJSON_GetObjectItem(payload, "conv_id");
        bool was_active = g_peer.chat_active && cJSON_IsString(conv) &&
            strcmp(conv->valuestring, g_peer.chat_conv) == 0 &&
            strcmp(prop(p, "peer_id"), g_peer.chat_peer_id) == 0;
        if (was_active) g_peer.chat_active = false;
        cJSON_Delete(payload);
        peer_unlock();
        if (was_active) {
            peer_set_message("%s 结束了聊天", name);
        }
        return reply_ok();
    }
    if (strcmp(type, "perform") == 0) {
        char show[PEER_SHOW_LEN];
        int64_t start = 0;
        if (!peer_perform_parse(prop(p, "payload"), show, &start)) {
            return reply_error("bad_args", "payload");
        }
        peer_schedule_perform(name, show, start);
        return reply_ok();
    }
    return reply_error("bad_args", type);
}

esp_err_t peer_tools_register(void)
{
    static const cap_mcp_server_tool_def_t defs[] = {
        {"peer_hello", "Return partner identity and capabilities", cb_hello, {NULL}, 0},
        {"peer_pair_request", "Ask to become partners", cb_pair_request, {"from_id", "from_name", "from_kind", "code"}, 4},
        {"peer_pair_result", "Deliver the pairing decision", cb_pair_result, {"from_id", "code", "status", "token"}, 4},
        {"peer_unpair", "Remove the partnership", cb_unpair, {"peer_id", "peer_token"}, 2},
        {"peer_say", "Partner chat line", cb_say, {"peer_id", "peer_token", "conv_id", "turn", "text"}, 5},
        {"peer_event", "Partner event: perform, chat_end or chat_message", cb_event, {"peer_id", "peer_token", "type", "payload"}, 4},
        {"take_photo", "Take one photo into the Mosaico album", cb_take_photo, {"peer_id", "peer_token"}, 2},
        {"play_music", "Play music; name filters file names", cb_play_music, {"peer_id", "peer_token", "name"}, 3},
        {"stop_music", "Stop music", cb_stop_music, {"peer_id", "peer_token"}, 2},
        {"show_emoji", "Show an emoji face: smile/wave/surprise/wink", cb_show_emoji, {"peer_id", "peer_token", "name"}, 3},
        {"show_text", "Show text on the round screen", cb_show_text, {"peer_id", "peer_token", "text"}, 3},
        {"get_weather", "Current weather", cb_get_weather, {"peer_id", "peer_token"}, 2},
        {"vibrate", "Vibrate: short/long/double", cb_vibrate, {"peer_id", "peer_token", "pattern"}, 3},
    };
    return cap_mcp_server_add_tool(defs, sizeof(defs) / sizeof(defs[0]));
}
