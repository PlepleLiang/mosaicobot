#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "claw_cap.h"
#include "peer_internal.h"

static esp_err_t peer_list_execute(const char *input_json, const claw_cap_call_context_t *ctx,
                                   char *output, size_t output_size)
{
    (void)input_json;
    (void)ctx;
    if (g_peer.lock == NULL) {
        snprintf(output, output_size, "{\"peers\":[],\"hint\":\"伙伴功能还没启动\"}");
        return ESP_OK;
    }
    cJSON *root = cJSON_CreateObject();
    cJSON *peers = cJSON_AddArrayToObject(root, "peers");
    peer_lock();
    for (size_t i = 0; i < PEER_MAX_PEERS; i++) {
        const peer_entry_t *e = &g_peer.registry.entries[i];
        if (!e->in_use || !e->online || !e->is_friend || !peer_version_compatible(e->ep.version)) {
            continue;
        }
        cJSON *peer = cJSON_CreateObject();
        cJSON_AddStringToObject(peer, "peer", e->ep.id);
        cJSON_AddStringToObject(peer, "name", e->ep.name);
        cJSON_AddStringToObject(peer, "kind", e->ep.kind);
        cJSON *tools = cJSON_AddArrayToObject(peer, "tools");
        for (size_t j = 0; j < e->cap_count; j++) {
            if (!e->caps[j].available) {
                continue;
            }
            cJSON *tool = cJSON_CreateObject();
            cJSON_AddStringToObject(tool, "name", e->caps[j].name);
            cJSON_AddStringToObject(tool, "title", e->caps[j].title);
            cJSON_AddStringToObject(tool, "params", e->caps[j].params);
            cJSON_AddBoolToObject(tool, "needs_consent", e->caps[j].sensitive);
            cJSON_AddItemToArray(tools, tool);
        }
        cJSON *chat = cJSON_CreateObject();
        cJSON_AddStringToObject(chat, "name", "peer_chat");
        cJSON_AddStringToObject(chat, "title", "和伙伴打招呼、聊天（最多6轮）");
        cJSON_AddItemToArray(tools, chat);
        cJSON_AddItemToArray(peers, peer);
    }
    peer_unlock();
    if (cJSON_GetArraySize(peers) == 0) {
        cJSON_AddStringToObject(root, "hint", "没有在线的伙伴好友");
    }
    char *text = cJSON_PrintUnformatted(root);
    cJSON_Delete(root);
    snprintf(output, output_size, "%s", text != NULL ? text : "{}");
    free(text);
    return ESP_OK;
}

static esp_err_t peer_call_execute(const char *input_json, const claw_cap_call_context_t *ctx,
                                   char *output, size_t output_size)
{
    (void)ctx;
    cJSON *in = cJSON_Parse(input_json != NULL ? input_json : "{}");
    cJSON *peer = cJSON_GetObjectItem(in, "peer");
    cJSON *tool = cJSON_GetObjectItem(in, "tool");
    if (g_peer.lock == NULL || !cJSON_IsString(peer) || !cJSON_IsString(tool)) {
        cJSON_Delete(in);
        peer_reply_error(output, output_size, "bad_args", "需要 peer 和 tool");
        return ESP_OK;
    }
    char peer_id[PEER_ID_LEN] = {0};
    peer_lock();
    peer_entry_t *e = peer_registry_find(&g_peer.registry, peer->valuestring);
    if (e == NULL) e = peer_registry_find_name(&g_peer.registry, peer->valuestring);
    if (e != NULL && e->is_friend) {
        peer_copy(peer_id, sizeof(peer_id), e->ep.id);
    }
    peer_unlock();
    if (peer_id[0] == '\0') {
        cJSON_Delete(in);
        peer_reply_error(output, output_size, "unauthorized", "不是好友或不在线，先调用 peer_list");
        return ESP_OK;
    }
    if (strcmp(tool->valuestring, "peer_chat") == 0) {
        esp_err_t err = peer_agent_start_chat(peer_id);
        cJSON_Delete(in);
        if (err == ESP_OK) snprintf(output, output_size, "{\"ok\":true}");
        else peer_reply_error(output, output_size, "busy", esp_err_to_name(err));
        return ESP_OK;
    }
    cJSON *args = cJSON_GetObjectItem(in, "args");
    cJSON *parsed = NULL;
    if (cJSON_IsString(args)) {
        parsed = cJSON_Parse(args->valuestring);
        args = parsed;
    }
    const char *keys[PEER_MAX_CALL_ARGS];
    const char *vals[PEER_MAX_CALL_ARGS];
    char numbers[PEER_MAX_CALL_ARGS][24];
    size_t n = peer_args_from_json(args, keys, vals, numbers, PEER_MAX_CALL_ARGS);
    (void)peer_call_friend(peer_id, tool->valuestring, keys, vals, n, output, output_size, PEER_CALL_TIMEOUT_MS);
    cJSON_Delete(parsed);
    cJSON_Delete(in);
    return ESP_OK;
}

static const claw_cap_descriptor_t s_peer_descriptors[] = {
    {
        .id = "peer_list",
        .name = "peer_list",
        .family = "peer",
        .description = "列出同一 Wi-Fi 下在线的伙伴好友（例如 StackChan 机器人）以及它们能执行的指令。让伙伴做事前先调用。",
        .kind = CLAW_CAP_KIND_CALLABLE,
        .cap_flags = CLAW_CAP_FLAG_CALLABLE_BY_LLM,
        .input_schema_json = "{\"type\":\"object\",\"properties\":{}}",
        .execute = peer_list_execute,
    },
    {
        .id = "peer_call",
        .name = "peer_call",
        .family = "peer",
        .description = "让一位在线的伙伴好友执行它的某个指令，例如点头、跳舞、说话。peer 填伙伴名字，tool 填 peer_list 返回的指令名，args 为参数对象。",
        .kind = CLAW_CAP_KIND_CALLABLE,
        .cap_flags = CLAW_CAP_FLAG_CALLABLE_BY_LLM,
        .input_schema_json = "{\"type\":\"object\",\"properties\":{\"peer\":{\"type\":\"string\"},"
                             "\"tool\":{\"type\":\"string\"},\"args\":{\"type\":\"object\"}},\"required\":[\"peer\",\"tool\"]}",
        .execute = peer_call_execute,
    },
};

static const claw_cap_group_t s_peer_group = {
    .group_id = "peer_agent",
    .descriptors = s_peer_descriptors,
    .descriptor_count = sizeof(s_peer_descriptors) / sizeof(s_peer_descriptors[0]),
};

esp_err_t peer_agent_register_capabilities(void)
{
    esp_err_t local = peer_local_tools_register();
    if (local != ESP_OK) return local;
    if (claw_cap_group_exists(s_peer_group.group_id)) {
        return ESP_OK;
    }
    return claw_cap_register_group(&s_peer_group);
}
