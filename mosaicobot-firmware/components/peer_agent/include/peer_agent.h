/* Partner-agent integration for Mosaico: discovery, pairing, commands, chat and performances. */
#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "esp_err.h"
#include "peer_core.h"

#ifdef __cplusplus
extern "C" {
#endif

#define PEER_CHAT_LINES 16
#define PEER_LINE_TEXT_LEN PEER_MANUAL_TEXT_LEN
#define PEER_MESSAGE_LEN 96

typedef enum {
    PEER_NOTICE_ONLINE = 0,
    PEER_NOTICE_COMMAND,
    PEER_NOTICE_FAILED,
    PEER_NOTICE_REQUEST,
} peer_notice_t;

typedef enum {
    PEER_PROMPT_NONE = 0,
    PEER_PROMPT_PAIR,
    PEER_PROMPT_SENSITIVE,
} peer_prompt_kind_t;

typedef struct {
    char id[PEER_ID_LEN];
    char name[PEER_NAME_LEN];
    char kind[PEER_KIND_LEN];
    bool online;
    bool is_friend;
    bool needs_repair;
    bool compatible;
    int64_t last_seen_ms;
    peer_cap_t caps[PEER_MAX_CAPS];
    uint8_t cap_count;
} peer_view_t;

typedef struct {
    bool from_self;
    char text[PEER_LINE_TEXT_LEN];
} peer_chat_line_t;

typedef struct {
    peer_view_t peers[PEER_MAX_PEERS];
    uint8_t peer_count;
    bool network_ready;
    bool service_ready;
    esp_err_t service_error;
    esp_err_t startup_error;
    int64_t scan_started_ms;
    peer_prompt_kind_t prompt;
    char prompt_from_name[PEER_NAME_LEN];
    char prompt_code[PEER_CODE_LEN];
    char prompt_title[PEER_CAP_TITLE_LEN];
    int64_t prompt_deadline_ms;
    bool pairing_out;
    char pairing_peer_name[PEER_NAME_LEN];
    char pairing_code[PEER_CODE_LEN];
    int64_t pairing_deadline_ms;
    bool chat_active;
    char chat_peer_id[PEER_ID_LEN];
    uint32_t chat_revision;
    uint32_t chat_received_seq;
    int manual_send_status; /* 0 idle, 1 pending, 2 delivered, -1 failed */
    char chat_peer_name[PEER_NAME_LEN];
    int chat_turn;
    peer_chat_line_t chat[PEER_CHAT_LINES];
    uint8_t chat_count;
    bool perform_pending;
    char perform_show[PEER_SHOW_LEN];
    char perform_peer_name[PEER_NAME_LEN];
    int64_t perform_start_ms;
    int64_t perform_end_ms;
    char message[PEER_MESSAGE_LEN];
    uint32_t message_seq;
    int64_t now_ms;
} peer_agent_snapshot_t;

/** Device actions implemented by the UI layer. Callbacks may run on any task. */
typedef struct {
    esp_err_t (*take_photo)(char *saved, size_t saved_size);
    esp_err_t (*play_music)(const char *name, char *playing, size_t playing_size);
    esp_err_t (*stop_music)(void);
    esp_err_t (*show_emoji)(const char *name);
    esp_err_t (*show_text)(const char *text);
    void (*notify)(peer_notice_t notice);
    void (*open_app)(void);
} peer_agent_ui_ops_t;

esp_err_t peer_agent_register_capabilities(void);
void peer_agent_set_ui_ops(const peer_agent_ui_ops_t *ops);
esp_err_t peer_agent_start(void);
void peer_agent_get_snapshot(peer_agent_snapshot_t *out);
int64_t peer_agent_now_ms(void);

/* Asynchronous commands; results arrive through snapshot.message. */
esp_err_t peer_agent_meet(const char *peer_id);
void peer_agent_cancel_meet(void);
void peer_agent_answer_prompt(bool accept);
esp_err_t peer_agent_invoke(const char *peer_id, const char *tool, const char *args_json);
esp_err_t peer_agent_start_chat(const char *peer_id);
esp_err_t peer_agent_open_chat(const char *peer_id);
esp_err_t peer_agent_send_message(const char *peer_id, const char *text, const char *message_id);
void peer_agent_stop_chat(void);
esp_err_t peer_agent_perform(const char *peer_id, const char *show);
esp_err_t peer_agent_unpair(const char *peer_id);
void peer_agent_perform_finished(void);

#ifdef __cplusplus
}
#endif
