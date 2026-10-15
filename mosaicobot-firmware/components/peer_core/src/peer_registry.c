#include <stdlib.h>
#include <string.h>
#include <strings.h>

#include "cJSON.h"
#include "peer_core.h"

void peer_registry_init(peer_registry_t *r, const char *self_id)
{
    memset(r, 0, sizeof(*r));
    peer_copy(r->self_id, sizeof(r->self_id), self_id);
}

peer_entry_t *peer_registry_find(peer_registry_t *r, const char *id)
{
    if (id == NULL || id[0] == '\0') {
        return NULL;
    }
    for (size_t i = 0; i < PEER_MAX_PEERS; i++) {
        if (r->entries[i].in_use && strcmp(r->entries[i].ep.id, id) == 0) {
            return &r->entries[i];
        }
    }
    return NULL;
}

static peer_entry_t *alloc_entry(peer_registry_t *r)
{
    peer_entry_t *victim = NULL;
    for (size_t i = 0; i < PEER_MAX_PEERS; i++) {
        peer_entry_t *e = &r->entries[i];
        if (!e->in_use) {
            return e;
        }
        if (!e->is_friend && (victim == NULL || e->last_seen_ms < victim->last_seen_ms)) {
            victim = e;
        }
    }
    return victim;
}

peer_entry_t *peer_registry_seen(peer_registry_t *r, const peer_endpoint_t *ep, int64_t now_ms, bool *out_new)
{
    if (out_new != NULL) {
        *out_new = false;
    }
    if (ep == NULL || strcmp(ep->id, r->self_id) == 0) {
        return NULL;
    }
    peer_entry_t *e = peer_registry_find(r, ep->id);
    if (e == NULL) {
        e = alloc_entry(r);
        if (e == NULL) {
            return NULL;
        }
        memset(e, 0, sizeof(*e));
        e->in_use = true;
    }
    bool was_online = e->online;
    e->ep = *ep;
    e->online = true;
    e->last_seen_ms = now_ms;
    if (out_new != NULL) {
        *out_new = !was_online;
    }
    return e;
}

int peer_registry_tick(peer_registry_t *r, int64_t now_ms)
{
    int went_offline = 0;
    for (size_t i = 0; i < PEER_MAX_PEERS; i++) {
        peer_entry_t *e = &r->entries[i];
        if (!e->in_use || !e->online || now_ms - e->last_seen_ms <= PEER_ONLINE_TIMEOUT_MS) {
            continue;
        }
        e->online = false;
        went_offline++;
        if (!e->is_friend) {
            e->in_use = false;
        }
    }
    return went_offline;
}

peer_entry_t *peer_registry_find_name(peer_registry_t *r, const char *name)
{
    if (name == NULL || name[0] == '\0') {
        return NULL;
    }
    for (size_t i = 0; i < PEER_MAX_PEERS; i++) {
        peer_entry_t *e = &r->entries[i];
        if (e->in_use && (strcasecmp(e->ep.name, name) == 0 || strcmp(e->ep.id, name) == 0)) {
            return e;
        }
    }
    for (size_t i = 0; i < PEER_MAX_PEERS; i++) {
        peer_entry_t *e = &r->entries[i];
        if (e->in_use && strstr(e->ep.name, name) != NULL) {
            return e;
        }
    }
    return NULL;
}

bool peer_registry_add_friend(peer_registry_t *r, const char *id, const char *name, const char *kind, const char *token)
{
    if (id == NULL || id[0] == '\0' || token == NULL || token[0] == '\0') {
        return false;
    }
    char name_copy[PEER_NAME_LEN];
    char kind_copy[PEER_KIND_LEN];
    peer_copy(name_copy, sizeof(name_copy), name);
    peer_copy(kind_copy, sizeof(kind_copy), kind);
    peer_entry_t *e = peer_registry_find(r, id);
    if (e == NULL) {
        e = alloc_entry(r);
        if (e == NULL) {
            return false;
        }
        memset(e, 0, sizeof(*e));
        e->in_use = true;
        peer_copy(e->ep.id, sizeof(e->ep.id), id);
    }
    if (name_copy[0] != '\0') {
        peer_copy(e->ep.name, sizeof(e->ep.name), name_copy);
    }
    if (kind_copy[0] != '\0') {
        peer_copy(e->ep.kind, sizeof(e->ep.kind), kind_copy);
    }
    peer_copy(e->token, sizeof(e->token), token);
    e->is_friend = true;
    e->needs_repair = false;
    return true;
}

bool peer_registry_remove_friend(peer_registry_t *r, const char *id)
{
    peer_entry_t *e = peer_registry_find(r, id);
    if (e == NULL || !e->is_friend) {
        return false;
    }
    e->is_friend = false;
    e->token[0] = '\0';
    if (!e->online) {
        e->in_use = false;
    }
    return true;
}

bool peer_registry_check_token(const peer_registry_t *r, const char *id, const char *token)
{
    if (id == NULL || token == NULL || token[0] == '\0') {
        return false;
    }
    for (size_t i = 0; i < PEER_MAX_PEERS; i++) {
        const peer_entry_t *e = &r->entries[i];
        if (e->in_use && e->is_friend && strcmp(e->ep.id, id) == 0) {
            return strcmp(e->token, token) == 0;
        }
    }
    return false;
}

char *peer_registry_friends_json(const peer_registry_t *r)
{
    cJSON *list = cJSON_CreateArray();
    if (list == NULL) {
        return NULL;
    }
    for (size_t i = 0; i < PEER_MAX_PEERS; i++) {
        const peer_entry_t *e = &r->entries[i];
        if (!e->in_use || !e->is_friend) {
            continue;
        }
        cJSON *item = cJSON_CreateObject();
        cJSON_AddStringToObject(item, "id", e->ep.id);
        cJSON_AddStringToObject(item, "name", e->ep.name);
        cJSON_AddStringToObject(item, "kind", e->ep.kind);
        cJSON_AddStringToObject(item, "token", e->token);
        cJSON_AddItemToArray(list, item);
    }
    char *text = cJSON_PrintUnformatted(list);
    cJSON_Delete(list);
    return text;
}

static const char *json_str(const cJSON *obj, const char *key)
{
    const cJSON *v = cJSON_GetObjectItem(obj, key);
    return cJSON_IsString(v) ? v->valuestring : "";
}

size_t peer_registry_load_friends_json(peer_registry_t *r, const char *json)
{
    cJSON *list = cJSON_Parse(json != NULL ? json : "");
    if (!cJSON_IsArray(list)) {
        cJSON_Delete(list);
        return 0;
    }
    size_t loaded = 0;
    cJSON *item = NULL;
    cJSON_ArrayForEach(item, list) {
        if (peer_registry_add_friend(r, json_str(item, "id"), json_str(item, "name"), json_str(item, "kind"),
                                     json_str(item, "token"))) {
            loaded++;
        }
    }
    cJSON_Delete(list);
    return loaded;
}

bool peer_cap_allowed(const char *kind, const char *tool)
{
    static const char *mosaico[] = {"take_photo", "play_music", "stop_music", "show_emoji", "show_text", "get_weather", "vibrate"};
    static const char *stackchan[] = {"robot_nod", "robot_shake", "robot_expression", "robot_dance", "robot_say", "robot_look"};
    const char **tools = NULL;
    size_t count = 0;
    if (strcmp(kind, "mosaico") == 0) { tools = mosaico; count = sizeof(mosaico) / sizeof(*mosaico); }
    if (strcmp(kind, "stackchan") == 0) { tools = stackchan; count = sizeof(stackchan) / sizeof(*stackchan); }
    for (size_t i = 0; i < count; i++) if (strcmp(tool, tools[i]) == 0) return true;
    return false;
}

bool peer_registry_set_caps_from_hello(peer_entry_t *e, const char *hello_json)
{
    cJSON *root = cJSON_Parse(hello_json != NULL ? hello_json : "");
    if (root == NULL) {
        return false;
    }
    cJSON *caps = cJSON_GetObjectItem(root, "caps");
    if (!cJSON_IsArray(caps)) {
        cJSON_Delete(root);
        return false;
    }
    e->cap_count = 0;
    cJSON *c = NULL;
    cJSON_ArrayForEach(c, caps) {
        if (e->cap_count >= PEER_MAX_CAPS) {
            break;
        }
        cJSON *name = cJSON_GetObjectItem(c, "name");
        if (!cJSON_IsString(name) || !peer_cap_allowed(e->ep.kind, name->valuestring)) {
            continue;
        }
        peer_cap_t *cap = &e->caps[e->cap_count++];
        memset(cap, 0, sizeof(*cap));
        peer_copy(cap->name, sizeof(cap->name), name->valuestring);
        cJSON *title = cJSON_GetObjectItem(c, "title");
        peer_copy(cap->title, sizeof(cap->title), cJSON_IsString(title) ? title->valuestring : name->valuestring);
        cap->sensitive = cJSON_IsTrue(cJSON_GetObjectItem(c, "sensitive")) ||
                         strcmp(cap->name, "take_photo") == 0 || strcmp(cap->name, "robot_look") == 0;
        cJSON *available = cJSON_GetObjectItem(c, "available");
        cap->available = !cJSON_IsBool(available) || cJSON_IsTrue(available);
        cJSON *params = cJSON_GetObjectItem(c, "params");
        cJSON *p = NULL;
        size_t used = 0;
        cJSON_ArrayForEach(p, params) {
            if (!cJSON_IsString(p)) {
                continue;
            }
            size_t n = strlen(p->valuestring);
            if (used + n + 2 >= sizeof(cap->params)) {
                break;
            }
            if (used > 0) {
                cap->params[used++] = ',';
            }
            memcpy(cap->params + used, p->valuestring, n);
            used += n;
            cap->params[used] = '\0';
        }
    }
    e->caps_loaded = true;
    cJSON_Delete(root);
    return true;
}
