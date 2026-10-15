#pragma once
#include <stdbool.h>
#include <stdint.h>
void peer_voice_begin(const char *peer_id);
void peer_voice_finish(bool send);
void peer_voice_cancel(void);
void peer_voice_tick(int send_status, int64_t now_ms);
const char *peer_voice_label(bool cancel_gesture);
