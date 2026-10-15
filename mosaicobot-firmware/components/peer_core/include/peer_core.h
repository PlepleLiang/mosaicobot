/* Pure partner-agent logic shared by the ESP integration and host tests. */
#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define PEER_PROTOCOL_VERSION "1"
#define PEER_ENDPOINT "mcp"
#define PEER_PORT 18791
#define PEER_ID_LEN 33
#define PEER_NAME_LEN 25
#define PEER_KIND_LEN 12
#define PEER_TOKEN_LEN 33
#define PEER_CODE_LEN 5
#define PEER_HOST_LEN 40
#define PEER_PATH_LEN 16
#define PEER_VERSION_LEN 8
#define PEER_MAX_PEERS 8
#define PEER_MAX_CAPS 10
#define PEER_CAP_NAME_LEN 24
#define PEER_CAP_TITLE_LEN 32
#define PEER_CAP_PARAMS_LEN 48
#define PEER_MAX_COOLDOWNS 4
#define PEER_CONV_ID_LEN 9
#define PEER_SHOW_LEN 16
#define PEER_LINE_LEN 64
#define PEER_ONLINE_TIMEOUT_MS 30000
#define PEER_PAIR_TIMEOUT_MS 30000
#define PEER_PAIR_COOLDOWN_MS 60000
#define PEER_SENSITIVE_TIMEOUT_MS 5000
#define PEER_CHAT_MAX_TURNS 6
#define PEER_MANUAL_TEXT_LEN 481
bool peer_manual_valid(const char *message_id, const char *text);

typedef uint32_t (*peer_rng_fn)(void);

typedef struct {
    const char *key;
    const char *value;
} peer_txt_item_t;

typedef struct {
    char id[PEER_ID_LEN];
    char name[PEER_NAME_LEN];
    char kind[PEER_KIND_LEN];
    char version[PEER_VERSION_LEN];
    char host[PEER_HOST_LEN];
    uint16_t port;
    char path[PEER_PATH_LEN];
} peer_endpoint_t;

typedef struct {
    char name[PEER_CAP_NAME_LEN];
    char title[PEER_CAP_TITLE_LEN];
    char params[PEER_CAP_PARAMS_LEN]; /* comma separated */
    bool sensitive;
    bool available;
} peer_cap_t;

typedef struct {
    peer_endpoint_t ep;
    bool in_use;
    bool online;
    bool is_friend;
    bool needs_repair;
    bool caps_loaded;
    int64_t last_seen_ms;
    char token[PEER_TOKEN_LEN];
    peer_cap_t caps[PEER_MAX_CAPS];
    uint8_t cap_count;
} peer_entry_t;

typedef struct {
    char self_id[PEER_ID_LEN];
    peer_entry_t entries[PEER_MAX_PEERS];
} peer_registry_t;

typedef enum {
    PEER_PAIR_PENDING = 0,
    PEER_PAIR_BUSY,
    PEER_PAIR_COOLDOWN,
} peer_pair_status_t;

typedef struct {
    char id[PEER_ID_LEN];
    int64_t until_ms;
} peer_cooldown_t;

typedef struct {
    bool out_active;
    char out_peer_id[PEER_ID_LEN];
    char out_code[PEER_CODE_LEN];
    int64_t out_started_ms;
    bool in_active;
    char in_from_id[PEER_ID_LEN];
    char in_from_name[PEER_NAME_LEN];
    char in_from_kind[PEER_KIND_LEN];
    char in_code[PEER_CODE_LEN];
    int64_t in_received_ms;
    peer_cooldown_t out_cool[PEER_MAX_COOLDOWNS];
    peer_cooldown_t in_cool[PEER_MAX_COOLDOWNS];
} peer_pairing_t;

/* ---- text helpers ---- */
void peer_utf8_truncate(char *text, size_t max_bytes);
/** NULL-safe copy that never splits a UTF-8 character. */
void peer_copy(char *dst, size_t dst_size, const char *src);
/** Splits text into at most max_lines lines of chars_per_line characters. Returns line count. */
size_t peer_utf8_lines(const char *text, size_t chars_per_line, char lines[][PEER_LINE_LEN], size_t max_lines);

/* ---- TXT ---- */
/** Strings are referenced, not copied: id/name/kind must outlive out. */
size_t peer_txt_build(const char *id, const char *name, const char *kind, peer_txt_item_t out[6]);
bool peer_txt_parse(const peer_txt_item_t *items, size_t count, const char *host, uint16_t port, peer_endpoint_t *out);
bool peer_version_compatible(const char *version);

/* ---- random ---- */
void peer_random_hex(char *out, size_t hex_chars, peer_rng_fn rng);
void peer_random_code(char out[PEER_CODE_LEN], peer_rng_fn rng);

/* ---- JSON-RPC and replies ---- */
/** Builds a tools/call body; caller frees with free(). */
char *peer_json_call_body(int id, const char *tool, const char *const *keys, const char *const *values, size_t count);
/** Joins result.content[].text; false on RPC error or malformed body. */
bool peer_json_tool_text(const char *body, char *out, size_t out_size);
int peer_reply_error(char *out, size_t size, const char *kind, const char *detail);
/** Non-JSON text counts as success. err_kind may be NULL. */
bool peer_reply_is_ok(const char *text, char *err_kind, size_t err_size);

/* ---- chat and perform ---- */
bool peer_chat_should_reply(int received_turn);
/** Returns true when the reply carried [END]; out receives the trimmed text. */
bool peer_chat_clean_reply(const char *raw, char *out, size_t out_size);
void peer_chat_opening_prompt(const char *peer_name, char *out, size_t size);
void peer_chat_incoming_prompt(const char *peer_name, const char *text, char *out, size_t size);
bool peer_perform_parse(const char *payload, char show[PEER_SHOW_LEN], int64_t *start_at_ms);
int peer_perform_payload(const char *show, int64_t start_at_ms, char *out, size_t size);

/* ---- registry ---- */
void peer_registry_init(peer_registry_t *r, const char *self_id);
peer_entry_t *peer_registry_seen(peer_registry_t *r, const peer_endpoint_t *ep, int64_t now_ms, bool *out_new);
int peer_registry_tick(peer_registry_t *r, int64_t now_ms);
peer_entry_t *peer_registry_find(peer_registry_t *r, const char *id);
peer_entry_t *peer_registry_find_name(peer_registry_t *r, const char *name);
bool peer_registry_add_friend(peer_registry_t *r, const char *id, const char *name, const char *kind, const char *token);
bool peer_registry_remove_friend(peer_registry_t *r, const char *id);
bool peer_registry_check_token(const peer_registry_t *r, const char *id, const char *token);
char *peer_registry_friends_json(const peer_registry_t *r);
size_t peer_registry_load_friends_json(peer_registry_t *r, const char *json);
bool peer_registry_set_caps_from_hello(peer_entry_t *e, const char *hello_json);
bool peer_cap_allowed(const char *kind, const char *tool);

/* ---- pairing ---- */
void peer_pairing_init(peer_pairing_t *p);
peer_pair_status_t peer_pairing_start_out(peer_pairing_t *p, const char *peer_id, const char *code, int64_t now_ms);
bool peer_pairing_finish_out(peer_pairing_t *p, const char *from_id, const char *code, bool accepted, int64_t now_ms);
void peer_pairing_fail_out(peer_pairing_t *p, int64_t now_ms);
bool peer_pairing_expire_out(peer_pairing_t *p, int64_t now_ms);
peer_pair_status_t peer_pairing_receive(peer_pairing_t *p, const char *from_id, const char *from_name,
                                        const char *from_kind, const char *code, int64_t now_ms);
void peer_pairing_resolve_in(peer_pairing_t *p, bool accepted, int64_t now_ms);
bool peer_pairing_expire_in(peer_pairing_t *p, int64_t now_ms);
const char *peer_pair_status_str(peer_pair_status_t status);

#ifdef __cplusplus
}
#endif
