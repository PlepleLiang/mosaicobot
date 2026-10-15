#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "cJSON.h"
#include "peer_core.h"

bool peer_manual_valid(const char *id, const char *text)
{
    if (!id || strlen(id) != 32 || strspn(id, "0123456789abcdef") != 32 || !text) return false;
    size_t bytes = strlen(text), chars = 0;
    bool nonblank = false;
    if (bytes == 0 || bytes >= PEER_MANUAL_TEXT_LEN) return false;
    for (size_t i = 0; i < bytes; ) {
        unsigned char c = (unsigned char)text[i];
        size_t n = c < 0x80 ? 1 : c >= 0xc2 && c <= 0xdf ? 2 : c >= 0xe0 && c <= 0xef ? 3 : c >= 0xf0 && c <= 0xf4 ? 4 : 0;
        if (!n || i + n > bytes || (c < 0x20 && c != '\n' && c != '\r' && c != '\t')) return false;
        for (size_t j = 1; j < n; ++j) if (((unsigned char)text[i+j] & 0xc0) != 0x80) return false;
        if ((n == 3 && ((c == 0xe0 && (unsigned char)text[i+1] < 0xa0) || (c == 0xed && (unsigned char)text[i+1] >= 0xa0))) ||
            (n == 4 && ((c == 0xf0 && (unsigned char)text[i+1] < 0x90) || (c == 0xf4 && (unsigned char)text[i+1] >= 0x90)))) return false;
        if (c > 0x20 && !(n == 3 && c == 0xe3 && (unsigned char)text[i+1] == 0x80 && (unsigned char)text[i+2] == 0x80)) nonblank = true;
        ++chars;
        i += n;
    }
    return nonblank && chars <= 120;
}

void peer_utf8_truncate(char *text, size_t max_bytes)
{
    if (text == NULL || strlen(text) <= max_bytes) {
        return;
    }
    size_t cut = max_bytes;
    while (cut > 0 && ((unsigned char)text[cut] & 0xC0) == 0x80) {
        cut--;
    }
    text[cut] = '\0';
}

void peer_copy(char *dst, size_t dst_size, const char *src)
{
    if (dst == NULL || dst_size == 0) {
        return;
    }
    if (src == NULL) {
        dst[0] = '\0';
        return;
    }
    size_t n = strlen(src);
    if (n < dst_size) {
        memmove(dst, src, n + 1);
        return;
    }
    n = dst_size - 1;
    while (n > 0 && ((unsigned char)src[n] & 0xC0) == 0x80) {
        n--;
    }
    memmove(dst, src, n);
    dst[n] = '\0';
}

static size_t utf8_char_len(unsigned char c)
{
    if (c < 0x80) return 1;
    if ((c >> 5) == 0x6) return 2;
    if ((c >> 4) == 0xE) return 3;
    if ((c >> 3) == 0x1E) return 4;
    return 1;
}

size_t peer_utf8_lines(const char *text, size_t chars_per_line, char lines[][PEER_LINE_LEN], size_t max_lines)
{
    for (size_t i = 0; i < max_lines; i++) {
        lines[i][0] = '\0';
    }
    if (text == NULL || max_lines == 0 || chars_per_line == 0) {
        return 0;
    }
    size_t line = 0, chars = 0, used = 0;
    const unsigned char *p = (const unsigned char *)text;
    while (*p != '\0' && line < max_lines) {
        if (*p == '\n') {
            p++;
            line++;
            chars = 0;
            used = 0;
            continue;
        }
        size_t n = utf8_char_len(*p);
        if (chars == chars_per_line || used + n >= PEER_LINE_LEN) {
            line++;
            chars = 0;
            used = 0;
            if (line >= max_lines) {
                break;
            }
        }
        memcpy(&lines[line][used], p, n);
        used += n;
        lines[line][used] = '\0';
        chars++;
        p += n;
    }
    if (line >= max_lines) {
        return max_lines;
    }
    return line + (used > 0 ? 1 : 0);
}

size_t peer_txt_build(const char *id, const char *name, const char *kind, peer_txt_item_t out[6])
{
    out[0] = (peer_txt_item_t){"agent", "1"};
    out[1] = (peer_txt_item_t){"id", id};
    out[2] = (peer_txt_item_t){"name", name};
    out[3] = (peer_txt_item_t){"kind", kind};
    out[4] = (peer_txt_item_t){"v", PEER_PROTOCOL_VERSION};
    out[5] = (peer_txt_item_t){"ep", PEER_ENDPOINT};
    return 6;
}

static const char *txt_get(const peer_txt_item_t *items, size_t count, const char *key)
{
    for (size_t i = 0; i < count; i++) {
        if (items[i].key != NULL && strcmp(items[i].key, key) == 0) {
            return items[i].value != NULL ? items[i].value : "";
        }
    }
    return NULL;
}

bool peer_txt_parse(const peer_txt_item_t *items, size_t count, const char *host, uint16_t port, peer_endpoint_t *out)
{
    if (out == NULL || host == NULL || host[0] == '\0') {
        return false;
    }
    const char *agent = txt_get(items, count, "agent");
    const char *id = txt_get(items, count, "id");
    if (agent == NULL || strcmp(agent, "1") != 0 || id == NULL || id[0] == '\0') {
        return false;
    }
    memset(out, 0, sizeof(*out));
    peer_copy(out->id, sizeof(out->id), id);
    const char *name = txt_get(items, count, "name");
    if (name != NULL && name[0] != '\0') {
        peer_copy(out->name, sizeof(out->name), name);
    } else {
        size_t n = strlen(out->id);
        peer_copy(out->name, sizeof(out->name), n > 4 ? out->id + n - 4 : out->id);
    }
    const char *kind = txt_get(items, count, "kind");
    peer_copy(out->kind, sizeof(out->kind), (kind != NULL && kind[0] != '\0') ? kind : "other");
    peer_copy(out->version, sizeof(out->version), txt_get(items, count, "v"));
    const char *ep = txt_get(items, count, "ep");
    if (ep == NULL || ep[0] == '\0') {
        ep = PEER_ENDPOINT;
    }
    while (*ep == '/') {
        ep++;
    }
    peer_copy(out->path, sizeof(out->path), ep);
    peer_copy(out->host, sizeof(out->host), host);
    out->port = port;
    return true;
}

bool peer_version_compatible(const char *version)
{
    if (version == NULL || version[0] == '\0') {
        return false;
    }
    size_t major = strcspn(version, ".");
    return major == strlen(PEER_PROTOCOL_VERSION) && strncmp(version, PEER_PROTOCOL_VERSION, major) == 0;
}

void peer_random_hex(char *out, size_t hex_chars, peer_rng_fn rng)
{
    static const char digits[] = "0123456789abcdef";
    for (size_t i = 0; i < hex_chars; i++) {
        out[i] = digits[rng() & 0x0F];
    }
    out[hex_chars] = '\0';
}

void peer_random_code(char out[PEER_CODE_LEN], peer_rng_fn rng)
{
    snprintf(out, PEER_CODE_LEN, "%04u", (unsigned)(1000U + rng() % 9000U));
}

char *peer_json_call_body(int id, const char *tool, const char *const *keys, const char *const *values, size_t count)
{
    cJSON *root = cJSON_CreateObject();
    cJSON *params = cJSON_CreateObject();
    cJSON *args = cJSON_CreateObject();
    if (root == NULL || params == NULL || args == NULL) {
        cJSON_Delete(root);
        cJSON_Delete(params);
        cJSON_Delete(args);
        return NULL;
    }
    cJSON_AddStringToObject(root, "jsonrpc", "2.0");
    cJSON_AddNumberToObject(root, "id", id);
    cJSON_AddStringToObject(root, "method", "tools/call");
    cJSON_AddStringToObject(params, "name", tool);
    for (size_t i = 0; i < count; i++) {
        cJSON_AddStringToObject(args, keys[i], values[i] != NULL ? values[i] : "");
    }
    cJSON_AddItemToObject(params, "arguments", args);
    cJSON_AddItemToObject(root, "params", params);
    char *out = cJSON_PrintUnformatted(root);
    cJSON_Delete(root);
    return out;
}

bool peer_json_tool_text(const char *body, char *out, size_t out_size)
{
    if (body == NULL || out == NULL || out_size == 0) {
        return false;
    }
    out[0] = '\0';
    cJSON *root = cJSON_Parse(body);
    if (root == NULL) {
        return false;
    }
    bool ok = false;
    cJSON *result = cJSON_GetObjectItem(root, "result");
    cJSON *content = result != NULL ? cJSON_GetObjectItem(result, "content") : NULL;
    if (cJSON_IsArray(content)) {
        ok = true;
        size_t used = 0;
        cJSON *item = NULL;
        cJSON_ArrayForEach(item, content) {
            cJSON *type = cJSON_GetObjectItem(item, "type");
            cJSON *text = cJSON_GetObjectItem(item, "text");
            if (cJSON_IsString(type) && strcmp(type->valuestring, "text") == 0 && cJSON_IsString(text)) {
                size_t n = strlen(text->valuestring);
                if (used + n >= out_size) {
                    n = out_size - 1 - used;
                }
                memcpy(out + used, text->valuestring, n);
                used += n;
                out[used] = '\0';
            }
        }
    }
    cJSON_Delete(root);
    return ok;
}

int peer_reply_error(char *out, size_t size, const char *kind, const char *detail)
{
    cJSON *root = cJSON_CreateObject();
    cJSON_AddBoolToObject(root, "ok", false);
    cJSON_AddStringToObject(root, "error", kind != NULL ? kind : "failed");
    cJSON_AddStringToObject(root, "detail", detail != NULL ? detail : "");
    char *text = cJSON_PrintUnformatted(root);
    cJSON_Delete(root);
    int n = snprintf(out, size, "%s", text != NULL ? text : "{\"ok\":false,\"error\":\"failed\"}");
    free(text);
    return n;
}

bool peer_reply_is_ok(const char *text, char *err_kind, size_t err_size)
{
    if (err_kind != NULL && err_size > 0) {
        err_kind[0] = '\0';
    }
    cJSON *root = cJSON_Parse(text != NULL ? text : "");
    if (root == NULL) {
        return true;
    }
    cJSON *ok = cJSON_GetObjectItem(root, "ok");
    bool result = !cJSON_IsBool(ok) || cJSON_IsTrue(ok);
    if (!result && err_kind != NULL) {
        cJSON *error = cJSON_GetObjectItem(root, "error");
        peer_copy(err_kind, err_size, cJSON_IsString(error) ? error->valuestring : "failed");
    }
    cJSON_Delete(root);
    return result;
}

bool peer_chat_should_reply(int received_turn)
{
    return received_turn >= 1 && received_turn < PEER_CHAT_MAX_TURNS;
}

static bool is_space(char c)
{
    return c == ' ' || c == '\n' || c == '\r' || c == '\t';
}

bool peer_chat_clean_reply(const char *raw, char *out, size_t out_size)
{
    char buf[512];
    peer_copy(buf, sizeof(buf), raw);
    bool ended = false;
    char *mark = strstr(buf, "[END]");
    if (mark != NULL) {
        ended = true;
        memmove(mark, mark + 5, strlen(mark + 5) + 1);
    }
    char *start = buf;
    while (is_space(*start)) {
        start++;
    }
    size_t n = strlen(start);
    while (n > 0 && is_space(start[n - 1])) {
        start[--n] = '\0';
    }
    peer_copy(out, out_size, start);
    return ended;
}

void peer_chat_opening_prompt(const char *peer_name, char *out, size_t size)
{
    snprintf(out, size,
             "你在和另一台设备上的伙伴「%s」闲聊。请先用一句话（不超过40字）向它打招呼并开启话题。"
             "只输出这句话，不要调用任何工具。",
             peer_name);
}

void peer_chat_incoming_prompt(const char *peer_name, const char *text, char *out, size_t size)
{
    snprintf(out, size,
             "伙伴「%s」对你说：%s\n请用一句话（不超过40字）自然地回应。只输出这句话，不要调用任何工具；"
             "想结束聊天时在句末加 [END]。",
             peer_name, text);
}

bool peer_perform_parse(const char *payload, char show[PEER_SHOW_LEN], int64_t *start_at_ms)
{
    cJSON *root = cJSON_Parse(payload != NULL ? payload : "");
    if (root == NULL) {
        return false;
    }
    cJSON *s = cJSON_GetObjectItem(root, "show");
    cJSON *t = cJSON_GetObjectItem(root, "start_at");
    bool ok = cJSON_IsString(s) && cJSON_IsString(t) &&
              (strcmp(s->valuestring, "dance_party") == 0 || strcmp(s->valuestring, "hello_wave") == 0);
    if (ok) {
        peer_copy(show, PEER_SHOW_LEN, s->valuestring);
        *start_at_ms = strtoll(t->valuestring, NULL, 10);
        ok = *start_at_ms > 0;
    }
    cJSON_Delete(root);
    return ok;
}

int peer_perform_payload(const char *show, int64_t start_at_ms, char *out, size_t size)
{
    char escaped[PEER_SHOW_LEN * 2];
    size_t j = 0;
    if (show == NULL) {
        show = "";
    }
    for (size_t i = 0; show[i] != '\0' && j + 2 < sizeof(escaped); i++) {
        if (show[i] == '"' || show[i] == '\\') {
            escaped[j++] = '\\';
        }
        escaped[j++] = show[i];
    }
    escaped[j] = '\0';
    return snprintf(out, size, "{\"show\":\"%s\",\"start_at\":\"%lld\",\"bpm\":\"120\"}", escaped, (long long)start_at_ms);
}
