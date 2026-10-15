#pragma once

#include <stdarg.h>

#include "cJSON.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"
#include "peer_agent.h"

#define PEER_SCAN_PERIOD_MS 10000
#define PEER_CALL_TIMEOUT_MS 8000
#define PEER_HTTP_MAX_RESPONSE 4096
#define PEER_REPLY_LEN 1024
#define PEER_ARG_LEN PEER_MANUAL_TEXT_LEN
#define PEER_PERFORM_LEAD_MS 3000
#define PEER_MAX_CALL_ARGS 4

typedef struct {
    SemaphoreHandle_t lock;
    peer_registry_t registry;
    peer_pairing_t pairing;
    char self_id[PEER_ID_LEN];
    char self_name[PEER_NAME_LEN];
    bool service_ready;
    esp_err_t service_error;
    int64_t scan_started_ms;
    peer_prompt_kind_t prompt;
    char prompt_from_name[PEER_NAME_LEN];
    char prompt_title[PEER_CAP_TITLE_LEN];
    int64_t prompt_deadline_ms;
    bool prompt_accepted;
    SemaphoreHandle_t prompt_done;
    bool chat_active;
    uint32_t chat_revision;
    uint32_t chat_received_seq;
    int manual_send_status;
    uint32_t manual_epoch;
    char received_ids[64][PEER_ID_LEN * 2];
    uint8_t received_next;
    char chat_conv[PEER_CONV_ID_LEN];
    char chat_peer_id[PEER_ID_LEN];
    char chat_peer_name[PEER_NAME_LEN];
    int chat_turn;
    peer_chat_line_t chat[PEER_CHAT_LINES];
    uint8_t chat_count;
    bool perform_pending;
    bool perform_started;
    char perform_show[PEER_SHOW_LEN];
    char perform_peer_name[PEER_NAME_LEN];
    int64_t perform_start_ms;
    int64_t perform_end_ms;
    char message[PEER_MESSAGE_LEN];
    uint32_t message_seq;
    const peer_agent_ui_ops_t *ui;
    QueueHandle_t work_q;
    QueueHandle_t chat_q;
} peer_state_t;

typedef enum {
    PEER_CMD_MEET = 0,
    PEER_CMD_ANSWER_PAIR,
    PEER_CMD_INVOKE,
    PEER_CMD_CHAT_START,
    PEER_CMD_CHAT_STOP,
    PEER_CMD_PERFORM,
    PEER_CMD_UNPAIR,
    PEER_CMD_FETCH_HELLO,
    PEER_CMD_MESSAGE,
} peer_cmd_type_t;

typedef struct {
    peer_cmd_type_t type;
    char peer_id[PEER_ID_LEN];
    char tool[PEER_CAP_NAME_LEN];
    char arg[PEER_ARG_LEN];
    char message_id[33];
    bool flag;
} peer_cmd_t;

typedef struct {
    bool opening;
    bool agent_request;
    bool reply_only;
    char message_id[33];
    uint32_t manual_epoch;
    char conv[PEER_CONV_ID_LEN];
    char peer_id[PEER_ID_LEN];
    int turn;
    char text[PEER_LINE_TEXT_LEN * 2];
} peer_chat_msg_t;

extern peer_state_t g_peer;

/* peer_agent.c */
void peer_lock(void);
void peer_unlock(void);
int64_t peer_now_ms(void);
uint32_t peer_rng(void);
void peer_set_message(const char *fmt, ...) __attribute__((format(printf, 1, 2)));
void peer_notify(peer_notice_t notice);
void peer_open_app(void);
void peer_save_friends(void);
void peer_describe_error(const char *peer_name, const char *kind, char *out, size_t size);
void peer_chat_append_locked(bool from_self, const char *text);
void peer_manual_append_locked(const char *peer_id, const char *name, const char *text, bool from_self);

/* peer_net.c */
esp_err_t peer_net_start(void);
esp_err_t peer_http_call(const peer_endpoint_t *ep, const char *tool, const char *const *keys,
                         const char *const *values, size_t count, char *out, size_t out_size, int timeout_ms);
esp_err_t peer_call_friend(const char *peer_id, const char *tool, const char *const *keys,
                           const char *const *values, size_t count, char *out, size_t out_size, int timeout_ms);

/* peer_tools.c */
esp_err_t peer_tools_register(void);
esp_err_t peer_local_tools_register(void);
void peer_rpc_init(void);
void peer_rpc_receive(const char *peer_id, const char *name, const char *payload, bool reply, char *out, size_t size);
bool peer_rpc_track_outgoing(const peer_cmd_t *cmd, const char *name);
void peer_rpc_complete(const peer_chat_msg_t *msg, const char *text, bool ok);
void peer_rpc_deliver(const peer_chat_msg_t *msg);
esp_err_t peer_vibrate(const char *pattern);

/* peer_worker.c */
esp_err_t peer_worker_start(void);
esp_err_t peer_post(const peer_cmd_t *cmd);
esp_err_t peer_post_chat(const peer_chat_msg_t *msg);
void peer_schedule_perform(const char *peer_name, const char *show, int64_t start_ms);
size_t peer_args_from_json(const cJSON *obj, const char **keys, const char **values,
                           char numbers[][24], size_t max);
