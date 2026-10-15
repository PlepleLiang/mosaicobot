#include "peer_voice.h"
#include <string.h>
#include "freertos/FreeRTOS.h"
#include "mosaic_ai_create_runtime.h"
#include "peer_agent.h"
#include "esp_random.h"

static ai_create_voice_port_handle_t s_port;
static uint32_t s_operation = 0x80000000U;
static char s_peer[PEER_ID_LEN], s_text[PEER_MANUAL_TEXT_LEN], s_id[33];
static int s_phase; /* 0 idle, 1 connecting, 2 holding, 3 transcribing, 4 sending, 5 retry */
static int64_t s_started;
static const char *s_error;
static portMUX_TYPE s_event_lock = portMUX_INITIALIZER_UNLOCKED;
static struct { uint32_t operation; bool started, done, failed; char text[PEER_MANUAL_TEXT_LEN]; } s_event;

static void voice_event(ai_create_voice_port_handle_t port, const ai_create_voice_event_t *event, void *ctx)
{
    (void)port; (void)ctx;
    /* Callback holds the session lock: only copy, never call port APIs here. */
    portENTER_CRITICAL(&s_event_lock);
    if (event->operation_id == s_event.operation) {
        if (event->type == AI_CREATE_VOICE_EVENT_STARTED) s_event.started = true;
        if (event->type == AI_CREATE_VOICE_EVENT_ERROR) s_event.failed = true;
        if (event->type == AI_CREATE_VOICE_EVENT_COMPLETED) {
            s_event.done = true;
            if (event->status.error != ESP_OK) s_event.failed = true;
            if (event->send && event->text) {
                if (strlen(event->text) >= sizeof(s_event.text)) s_event.failed = true;
                else peer_copy(s_event.text, sizeof(s_event.text), event->text);
            }
        }
    }
    portEXIT_CRITICAL(&s_event_lock);
}

void peer_voice_cancel(void)
{
    portENTER_CRITICAL(&s_event_lock);
    s_event.operation = 0;
    portEXIT_CRITICAL(&s_event_lock);
    if (s_port) mosaic_ai_create_runtime_release_voice(s_port, s_operation);
    s_port = NULL;
    s_phase = 0;
    s_error = NULL;
    s_text[0] = 0;
}

static void send_text(void)
{
    if (peer_agent_send_message(s_peer, s_text, s_id) == ESP_OK) s_phase = 4;
    else { s_phase = 5; s_error = "发送失败 · 点按重试"; }
}

void peer_voice_begin(const char *peer_id)
{
    if (s_phase == 5 && strcmp(s_peer, peer_id) == 0) { send_text(); return; }
    if (s_phase != 0 || !peer_id || !peer_id[0]) return;
    peer_copy(s_peer, sizeof(s_peer), peer_id);
    s_error = NULL;
    ++s_operation;
    portENTER_CRITICAL(&s_event_lock);
    memset(&s_event, 0, sizeof(s_event));
    s_event.operation = s_operation;
    portEXIT_CRITICAL(&s_event_lock);
    if (mosaic_ai_create_runtime_acquire_voice(voice_event, NULL, &s_port) != ESP_OK) {
        s_error = "语音未就绪或正忙"; return;
    }
    if (ai_create_voice_port_begin(s_port, s_operation) != ESP_OK) {
        peer_voice_cancel(); s_error = "语音启动失败，请重试"; return;
    }
    s_phase = 1;
    s_started = peer_agent_now_ms();
}

void peer_voice_finish(bool send)
{
    if (!s_port || (s_phase != 1 && s_phase != 2)) return;
    if (!send || peer_agent_now_ms() - s_started < 300) { peer_voice_cancel(); return; }
    s_phase = 3;
    if (ai_create_voice_port_finish(s_port, s_operation, true) != ESP_OK) {
        peer_voice_cancel(); s_error = "识别失败，请重试";
    }
}

void peer_voice_tick(int send_status, int64_t now_ms)
{
    if (s_phase == 4) {
        if (send_status == 2) { s_phase = 0; s_text[0] = 0; s_error = NULL; }
        else if (send_status == -1) { s_phase = 5; s_error = "发送失败 · 点按重试"; }
        return;
    }
    if (!s_port) return;
    if (now_ms - s_started > (s_phase == 3 ? 55000 : 30000)) {
        peer_voice_cancel(); s_error = "录音或识别超时，请重试"; return;
    }
    bool started, done, failed;
    portENTER_CRITICAL(&s_event_lock);
    started = s_event.started; done = s_event.done; failed = s_event.failed;
    if (done) peer_copy(s_text, sizeof(s_text), s_event.text);
    portEXIT_CRITICAL(&s_event_lock);
    if (started && s_phase == 1) s_phase = 2;
    if (!done && !failed) return;
    mosaic_ai_create_runtime_release_voice(s_port, s_operation);
    s_port = NULL;
    s_phase = 0;
    peer_random_hex(s_id, 32, esp_random);
    if (failed || !peer_manual_valid(s_id, s_text)) { s_error = "未听清或超过120字，请重试"; return; }
    send_text();
}

const char *peer_voice_label(bool cancel_gesture)
{
    if (cancel_gesture && (s_phase == 1 || s_phase == 2)) return "松开取消";
    if (s_error && (s_phase == 0 || s_phase == 5)) return s_error;
    switch (s_phase) {
    case 1: return "连接中 · 松开发送";
    case 2: return "松开发送 · 上滑取消";
    case 3: return "正在识别…";
    case 4: return "正在发送…";
    default: return "按住说话";
    }
}
