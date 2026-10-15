/* Partner app: the round-screen face of peer_agent. */
#include "mosaic_app_catalog.h"
#include "mosaic_hub_actions.h"
#include "peer_actions.h"
#include "peer_binds.h"
#include "peer_objects.h"

#if !defined(ESP_PLATFORM)

const mosaic_app_descriptor_t mosaic_peer_app = {
    .id = 52,
    .launch_action = GSP_ACT_ID_APP_PEER,
    .back_action = MOSAIC_APP_SHELL_BACK_ACTION,
    .name = "peer",
    .title = "Partner",
    .directory = &gsp_obj_directory_peer,
};

#else

#pragma GCC diagnostic ignored "-Wformat-truncation"

#include <stdio.h>
#include <string.h>

#include "esp_attr.h"
#include "freertos/FreeRTOS.h"
#include "mosaic_runtime.h"
#include "mosaic_loader.h"
#include "mosaic_top_notice.h"
#include "peer_agent.h"
#include "peer_app.h"
#include "peer_screen.h"
#include "peer_voice.h"

#define TEXT_SLOTS 80
#define VISIBLE_SLOTS 96
#define SHOW_MS 5000

static EXT_RAM_BSS_ATTR struct { bool used; uint16_t bind; char text[96]; } s_text_cache[TEXT_SLOTS];
static struct { bool used; uint16_t bind; bool visible; } s_visible_cache[VISIBLE_SLOTS];
static portMUX_TYPE s_show_lock = portMUX_INITIALIZER_UNLOCKED;
static struct { bool pending; bool is_text; int64_t until_ms; char value[192]; } s_show;
static EXT_RAM_BSS_ATTR peer_agent_snapshot_t s_snap;
static peer_screen_t s_screen;
static peer_screen_t s_user_screen;
static uint8_t s_index;
static uint8_t s_caps_page;
static int s_chat_offset;
static int s_chat_fragments;
static int s_chat_last_turn = -1;
static uint32_t s_chat_received;
static bool s_voice_pointer, s_voice_cancel;
static bool s_chat_was_active;
static bool s_unpair_confirmation;
static bool s_pointer_down;
static int s_pointer_x, s_pointer_y;
static uint32_t s_seen_seq;
static bool s_eq_phase;

static const mosaic_top_notice_config_t s_notice = {
    .visible_bind = GSP_BIND_PEER_NOTICE_VISIBLE,
    .title_bind = GSP_BIND_PEER_NOTICE_TITLE,
    .message_bind = GSP_BIND_PEER_NOTICE_MESSAGE,
};

static const uint16_t s_dot_visible[8] = {
    GSP_BIND_PEER_DOT_0_VISIBLE, GSP_BIND_PEER_DOT_1_VISIBLE, GSP_BIND_PEER_DOT_2_VISIBLE, GSP_BIND_PEER_DOT_3_VISIBLE,
    GSP_BIND_PEER_DOT_4_VISIBLE, GSP_BIND_PEER_DOT_5_VISIBLE, GSP_BIND_PEER_DOT_6_VISIBLE, GSP_BIND_PEER_DOT_7_VISIBLE,
};
static const uint16_t s_dot_selected[8] = {
    GSP_BIND_PEER_DOT_0_SELECTED, GSP_BIND_PEER_DOT_1_SELECTED, GSP_BIND_PEER_DOT_2_SELECTED, GSP_BIND_PEER_DOT_3_SELECTED,
    GSP_BIND_PEER_DOT_4_SELECTED, GSP_BIND_PEER_DOT_5_SELECTED, GSP_BIND_PEER_DOT_6_SELECTED, GSP_BIND_PEER_DOT_7_SELECTED,
};
static const uint16_t s_cap_text[6] = {
    GSP_BIND_PEER_CAP_0_TEXT, GSP_BIND_PEER_CAP_1_TEXT, GSP_BIND_PEER_CAP_2_TEXT,
    GSP_BIND_PEER_CAP_3_TEXT, GSP_BIND_PEER_CAP_4_TEXT, GSP_BIND_PEER_CAP_5_TEXT,
};
static const uint16_t s_cap_visible[6] = {
    GSP_BIND_PEER_CAP_0_VISIBLE, GSP_BIND_PEER_CAP_1_VISIBLE, GSP_BIND_PEER_CAP_2_VISIBLE,
    GSP_BIND_PEER_CAP_3_VISIBLE, GSP_BIND_PEER_CAP_4_VISIBLE, GSP_BIND_PEER_CAP_5_VISIBLE,
};
static const uint16_t s_chat_self_visible[4] = {
    GSP_BIND_PEER_CHAT_0_SELF_VISIBLE, GSP_BIND_PEER_CHAT_1_SELF_VISIBLE,
    GSP_BIND_PEER_CHAT_2_SELF_VISIBLE, GSP_BIND_PEER_CHAT_3_SELF_VISIBLE,
};
static const uint16_t s_chat_peer_visible[4] = {
    GSP_BIND_PEER_CHAT_0_PEER_VISIBLE, GSP_BIND_PEER_CHAT_1_PEER_VISIBLE,
    GSP_BIND_PEER_CHAT_2_PEER_VISIBLE, GSP_BIND_PEER_CHAT_3_PEER_VISIBLE,
};
static const uint16_t s_chat_self_a[4] = {
    GSP_BIND_PEER_CHAT_0_SELF_A, GSP_BIND_PEER_CHAT_1_SELF_A, GSP_BIND_PEER_CHAT_2_SELF_A, GSP_BIND_PEER_CHAT_3_SELF_A,
};
static const uint16_t s_chat_self_b[4] = {
    GSP_BIND_PEER_CHAT_0_SELF_B, GSP_BIND_PEER_CHAT_1_SELF_B, GSP_BIND_PEER_CHAT_2_SELF_B, GSP_BIND_PEER_CHAT_3_SELF_B,
};
static const uint16_t s_chat_peer_a[4] = {
    GSP_BIND_PEER_CHAT_0_PEER_A, GSP_BIND_PEER_CHAT_1_PEER_A, GSP_BIND_PEER_CHAT_2_PEER_A, GSP_BIND_PEER_CHAT_3_PEER_A,
};
static const uint16_t s_chat_peer_b[4] = {
    GSP_BIND_PEER_CHAT_0_PEER_B, GSP_BIND_PEER_CHAT_1_PEER_B, GSP_BIND_PEER_CHAT_2_PEER_B, GSP_BIND_PEER_CHAT_3_PEER_B,
};
static const uint16_t s_eq_tall[5] = {
    GSP_BIND_PEER_EQ_0_TALL_VISIBLE, GSP_BIND_PEER_EQ_1_TALL_VISIBLE, GSP_BIND_PEER_EQ_2_TALL_VISIBLE,
    GSP_BIND_PEER_EQ_3_TALL_VISIBLE, GSP_BIND_PEER_EQ_4_TALL_VISIBLE,
};
static const uint16_t s_eq_short[5] = {
    GSP_BIND_PEER_EQ_0_SHORT_VISIBLE, GSP_BIND_PEER_EQ_1_SHORT_VISIBLE, GSP_BIND_PEER_EQ_2_SHORT_VISIBLE,
    GSP_BIND_PEER_EQ_3_SHORT_VISIBLE, GSP_BIND_PEER_EQ_4_SHORT_VISIBLE,
};
static const uint16_t s_show_line[3] = {
    GSP_BIND_PEER_SHOW_LINE_0, GSP_BIND_PEER_SHOW_LINE_1, GSP_BIND_PEER_SHOW_LINE_2,
};

void peer_app_request_show(bool is_text, const char *value)
{
    portENTER_CRITICAL(&s_show_lock);
    s_show.pending = true;
    s_show.is_text = is_text;
    s_show.until_ms = 0;
    peer_copy(s_show.value, sizeof(s_show.value), value);
    portEXIT_CRITICAL(&s_show_lock);
}

static void set_text(esp_gsp_handle_t ui, uint16_t bind, const char *text)
{
    for (size_t i = 0; i < TEXT_SLOTS; i++) {
        if (s_text_cache[i].used && s_text_cache[i].bind == bind) {
            if (strcmp(s_text_cache[i].text, text) == 0) {
                return;
            }
            peer_copy(s_text_cache[i].text, sizeof(s_text_cache[i].text), text);
            (void)esp_gsp_set_text(ui, bind, text);
            return;
        }
        if (!s_text_cache[i].used) {
            s_text_cache[i].used = true;
            s_text_cache[i].bind = bind;
            peer_copy(s_text_cache[i].text, sizeof(s_text_cache[i].text), text);
            (void)esp_gsp_set_text(ui, bind, text);
            return;
        }
    }
    (void)esp_gsp_set_text(ui, bind, text);
}

static void set_visible(esp_gsp_handle_t ui, uint16_t bind, bool visible)
{
    for (size_t i = 0; i < VISIBLE_SLOTS; i++) {
        if (s_visible_cache[i].used && s_visible_cache[i].bind == bind) {
            if (s_visible_cache[i].visible == visible) {
                return;
            }
            s_visible_cache[i].visible = visible;
            (void)esp_gsp_set_visible(ui, bind, visible);
            return;
        }
        if (!s_visible_cache[i].used) {
            s_visible_cache[i].used = true;
            s_visible_cache[i].bind = bind;
            s_visible_cache[i].visible = visible;
            (void)esp_gsp_set_visible(ui, bind, visible);
            return;
        }
    }
    (void)esp_gsp_set_visible(ui, bind, visible);
}

static const peer_view_t *current_peer(void)
{
    if (s_snap.peer_count == 0) {
        return NULL;
    }
    if (s_index >= s_snap.peer_count) {
        s_index = 0;
    }
    return &s_snap.peers[s_index];
}

static void show_screen(esp_gsp_handle_t ui, peer_screen_t screen)
{
    set_visible(ui, GSP_BIND_PEER_HOME_VISIBLE, screen == VIEW_HOME);
    set_visible(ui, GSP_BIND_PEER_CAPS_VISIBLE, screen == VIEW_CAPS);
    set_visible(ui, GSP_BIND_PEER_CHAT_VISIBLE, screen == VIEW_CHAT);
    set_visible(ui, GSP_BIND_PEER_MODAL_VISIBLE, screen == VIEW_MODAL);
    set_visible(ui, GSP_BIND_PEER_SHOW_VISIBLE, screen == VIEW_SHOW);
    set_visible(ui, GSP_BIND_PEER_PERFORM_VISIBLE, screen == VIEW_PERFORM);
}

static void render_home(esp_gsp_handle_t ui)
{
    const peer_view_t *p = current_peer();
    set_visible(ui, GSP_BIND_PEER_EMPTY_VISIBLE, p == NULL);
    set_visible(ui, GSP_BIND_PEER_CARD_VISIBLE, p != NULL);
    if (p == NULL) {
        bool waiting = s_snap.now_ms - s_snap.scan_started_ms < 20000;
        bool activity = s_snap.network_ready && s_snap.startup_error == ESP_OK && (!s_snap.service_ready || waiting);
        int phase = (int)(s_snap.now_ms / 800 % 3);
        set_visible(ui, GSP_BIND_PEER_SCAN_ACTIVITY_VISIBLE, activity);
        set_visible(ui, GSP_BIND_PEER_SCAN_DOT_0_VISIBLE, phase == 0);
        set_visible(ui, GSP_BIND_PEER_SCAN_DOT_1_VISIBLE, phase == 1);
        set_visible(ui, GSP_BIND_PEER_SCAN_DOT_2_VISIBLE, phase == 2);
        set_visible(ui, GSP_BIND_PEER_NETWORK_SETTINGS_VISIBLE, !s_snap.network_ready || (s_snap.service_ready && !waiting));
        set_text(ui, GSP_BIND_PEER_NETWORK_STATUS, s_snap.network_ready ? "Wi-Fi 已连接" : "Wi-Fi 未连接");
        set_text(ui, GSP_BIND_PEER_NETWORK_SETTINGS_TEXT, s_snap.network_ready ? "查看网络" : "打开设置");
        if (!s_snap.network_ready) {
            set_text(ui, GSP_BIND_PEER_EMPTY_TITLE, "先连上 Wi-Fi 吧");
            set_text(ui, GSP_BIND_PEER_EMPTY_SUB, "和 Amoo 在同一个网络相见");
            set_text(ui, GSP_BIND_PEER_EMPTY_HINT, "连接后会自动寻找");
        } else if (s_snap.startup_error != ESP_OK) {
            set_text(ui, GSP_BIND_PEER_EMPTY_TITLE, "还没准备好");
            set_text(ui, GSP_BIND_PEER_EMPTY_SUB, "伙伴任务启动失败，请重启");
            set_text(ui, GSP_BIND_PEER_EMPTY_HINT, esp_err_to_name(s_snap.startup_error));
        } else if (!s_snap.service_ready) {
            bool retrying = s_snap.service_error != ESP_OK;
            set_text(ui, GSP_BIND_PEER_EMPTY_TITLE, retrying ? "见面服务恢复中" : "正在准备见面");
            set_text(ui, GSP_BIND_PEER_EMPTY_SUB, "已经联网，无需重新配网");
            set_text(ui, GSP_BIND_PEER_EMPTY_HINT, retrying ? esp_err_to_name(s_snap.service_error) : "稍等一下，很快就好");
        } else if (waiting) {
            set_text(ui, GSP_BIND_PEER_EMPTY_TITLE, "寻找小伙伴");
            set_text(ui, GSP_BIND_PEER_EMPTY_SUB, "让 Amoo 连上同一个 Wi-Fi");
            set_text(ui, GSP_BIND_PEER_EMPTY_HINT, "发现后会自动出现");
        } else {
            set_text(ui, GSP_BIND_PEER_EMPTY_TITLE, "小伙伴还没出现");
            set_text(ui, GSP_BIND_PEER_EMPTY_SUB, "在 Amoo 乐园点「扫描」试试");
            set_text(ui, GSP_BIND_PEER_EMPTY_HINT, "这里会继续自动寻找");
        }
        return;
    }
    bool amoo = strcmp(p->kind, "stackchan") == 0;
    set_visible(ui, GSP_BIND_PEER_AVATAR_AMOO_VISIBLE, amoo);
    set_visible(ui, GSP_BIND_PEER_AVATAR_MO_VISIBLE, !amoo);
    set_visible(ui, GSP_BIND_PEER_NEW_VISIBLE, p->online && !p->is_friend);
    char display_name[64];
    bool duplicate = false;
    for (size_t i = 0; i < s_snap.peer_count; i++) {
        if (&s_snap.peers[i] != p && strcmp(s_snap.peers[i].name, p->name) == 0) duplicate = true;
    }
    if (duplicate) snprintf(display_name, sizeof(display_name), "%s · %.4s", p->name, p->id + (strlen(p->id) > 4 ? strlen(p->id) - 4 : 0));
    else peer_copy(display_name, sizeof(display_name), p->name);
    set_text(ui, GSP_BIND_PEER_NAME, display_name);
    const char *status = !p->compatible ? "版本不兼容"
                       : p->needs_repair ? "需要重新认识"
                       : !p->online ? "离线"
                       : p->is_friend ? "在线 · 好友" : "在线 · 新发现";
    set_text(ui, GSP_BIND_PEER_STATUS, status);
    bool ready = p->online && p->compatible;
    set_text(ui, GSP_BIND_PEER_BTN0_TEXT, !p->compatible ? "需升级" : !p->online ? "离线" : p->is_friend ? "打招呼" : "认识");
    set_visible(ui, GSP_BIND_PEER_FRIEND_BTNS_VISIBLE, ready && p->is_friend);
    set_visible(ui, GSP_BIND_PEER_NAV_VISIBLE, s_snap.peer_count > 1);
    for (size_t i = 0; i < 8; i++) {
        set_visible(ui, s_dot_visible[i], s_snap.peer_count > 1 && i < s_snap.peer_count);
        set_visible(ui, s_dot_selected[i], i == s_index);
    }
    char index[16];
    snprintf(index, sizeof(index), "%u/%u", (unsigned)s_index + 1U, (unsigned)s_snap.peer_count);
    set_text(ui, GSP_BIND_PEER_INDEX, index);
    set_visible(ui, GSP_BIND_PEER_CAPS_HINT_VISIBLE, p->is_friend);
}

static void render_caps(esp_gsp_handle_t ui)
{
    const peer_view_t *p = current_peer();
    if (p == NULL) {
        s_user_screen = VIEW_HOME;
        return;
    }
    char title[64];
    unsigned pages = (p->cap_count + 2U) / 3U;
    if (pages == 0) pages = 1;
    if (s_caps_page >= pages) s_caps_page = 0;
    snprintf(title, sizeof(title), "%s 会这些 %u/%u", p->name, (unsigned)s_caps_page + 1, pages);
    set_text(ui, GSP_BIND_PEER_CAPS_TITLE, title);
    for (size_t i = 0; i < 6; i++) {
        size_t index = s_caps_page * 3U + i;
        bool visible = i < 3 && index < p->cap_count;
        set_visible(ui, s_cap_visible[i], visible);
        if (visible) {
            const peer_cap_t *cap = &p->caps[index];
            char line[96];
            snprintf(line, sizeof(line), "%s%s%s", cap->title, cap->sensitive ? " · 需同意" : "",
                     cap->available ? "" : " · 不可用");
            set_text(ui, s_cap_text[i], line);
        }
    }
}

static void render_chat(esp_gsp_handle_t ui)
{
    /* Each bubble holds two lines. Split long messages into consecutive bubbles;
       six turns remain available, and a vertical swipe moves the viewport. */
    enum { WRAP_LINES = 12 };
    static EXT_RAM_BSS_ATTR char wrapped[PEER_CHAT_LINES][WRAP_LINES][PEER_LINE_LEN];
    uint8_t owner[PEER_CHAT_LINES * WRAP_LINES];
    uint8_t row[PEER_CHAT_LINES * WRAP_LINES];
    int total = 0;
    for (size_t m = 0; m < s_snap.chat_count; m++) {
        size_t count = peer_utf8_lines(s_snap.chat[m].text, 13, wrapped[m], WRAP_LINES);
        for (size_t line = 0; line < count; line += 2) {
            owner[total] = m;
            row[total++] = line;
        }
    }
    s_chat_fragments = total;
    int last = total > 4 ? total - 4 : 0;
    if (s_chat_last_turn != (int)s_snap.chat_revision) {
        s_chat_offset = last;
        s_chat_last_turn = (int)s_snap.chat_revision;
    }
    if (s_chat_offset > last) s_chat_offset = last;
    char title[96];
    if (s_snap.chat_active) snprintf(title, sizeof(title), "%s · %d/%d · 上下滑动", s_snap.chat_peer_name, s_snap.chat_turn, PEER_CHAT_MAX_TURNS);
    else snprintf(title, sizeof(title), "%s · 聊天 · 上下滑动", s_snap.chat_peer_name);
    set_text(ui, GSP_BIND_PEER_CHAT_TITLE, title);
    set_text(ui, GSP_BIND_PEER_CHAT_STOP_TEXT, s_snap.chat_active ? "打断" : "返回");
    set_text(ui, GSP_BIND_PEER_CHAT_AUTO_TEXT, "自主聊聊");
    set_text(ui, GSP_BIND_PEER_CHAT_VOICE_TEXT, peer_voice_label(s_voice_cancel));
    for (int i = 0; i < 4; i++) {
        int index = s_chat_offset + i;
        bool used = index < total;
        bool self = used && s_snap.chat[owner[index]].from_self;
        set_visible(ui, s_chat_self_visible[i], used && self);
        set_visible(ui, s_chat_peer_visible[i], used && !self);
        if (!used) continue;
        size_t m = owner[index], line = row[index];
        set_text(ui, self ? s_chat_self_a[i] : s_chat_peer_a[i], wrapped[m][line]);
        const char *second = line + 1 < WRAP_LINES ? wrapped[m][line + 1] : "";
        set_text(ui, self ? s_chat_self_b[i] : s_chat_peer_b[i], second[0] != '\0' ? second : " ");
    }
}

static void render_modal(esp_gsp_handle_t ui)
{
    char title[96];
    int64_t deadline = 0;
    if (s_unpair_confirmation && s_snap.prompt == PEER_PROMPT_NONE) {
        const peer_view_t *p = current_peer();
        snprintf(title, sizeof(title), "解除与 %s 的好友？", p != NULL ? p->name : "伙伴");
        set_text(ui, GSP_BIND_PEER_MODAL_CODE, " ");
        set_text(ui, GSP_BIND_PEER_MODAL_SUB, "再次互动时需要重新认识");
        set_text(ui, GSP_BIND_PEER_MODAL_REJECT_TEXT, "取消");
        set_text(ui, GSP_BIND_PEER_MODAL_ACCEPT_TEXT, "解除");
        set_visible(ui, GSP_BIND_PEER_MODAL_ACCEPT_VISIBLE, true);
        set_text(ui, GSP_BIND_PEER_MODAL_TITLE, title);
        set_text(ui, GSP_BIND_PEER_MODAL_TIMER, " ");
        return;
    }
    if (s_snap.prompt == PEER_PROMPT_PAIR) {
        snprintf(title, sizeof(title), "%s 想认识你", s_snap.prompt_from_name);
        set_text(ui, GSP_BIND_PEER_MODAL_CODE, s_snap.prompt_code);
        set_text(ui, GSP_BIND_PEER_MODAL_SUB, "和手机上的数字一样吗？");
        set_text(ui, GSP_BIND_PEER_MODAL_REJECT_TEXT, "拒绝");
        set_text(ui, GSP_BIND_PEER_MODAL_ACCEPT_TEXT, "同意");
        set_visible(ui, GSP_BIND_PEER_MODAL_ACCEPT_VISIBLE, true);
        deadline = s_snap.prompt_deadline_ms;
    } else if (s_snap.prompt == PEER_PROMPT_SENSITIVE) {
        snprintf(title, sizeof(title), "%s 想让我：%s", s_snap.prompt_from_name, s_snap.prompt_title);
        set_text(ui, GSP_BIND_PEER_MODAL_CODE, " ");
        set_text(ui, GSP_BIND_PEER_MODAL_SUB, "只允许这一次");
        set_text(ui, GSP_BIND_PEER_MODAL_REJECT_TEXT, "拒绝");
        set_text(ui, GSP_BIND_PEER_MODAL_ACCEPT_TEXT, "允许");
        set_visible(ui, GSP_BIND_PEER_MODAL_ACCEPT_VISIBLE, true);
        deadline = s_snap.prompt_deadline_ms;
    } else {
        snprintf(title, sizeof(title), "正在请求认识 %s", s_snap.pairing_peer_name);
        set_text(ui, GSP_BIND_PEER_MODAL_CODE, s_snap.pairing_code);
        set_text(ui, GSP_BIND_PEER_MODAL_SUB, "请在手机上核对并同意");
        set_text(ui, GSP_BIND_PEER_MODAL_REJECT_TEXT, "取消");
        set_visible(ui, GSP_BIND_PEER_MODAL_ACCEPT_VISIBLE, false);
        deadline = s_snap.pairing_deadline_ms;
    }
    set_text(ui, GSP_BIND_PEER_MODAL_TITLE, title);
    int64_t remaining = deadline - s_snap.now_ms;
    char timer[16];
    snprintf(timer, sizeof(timer), "%ds", remaining > 0 ? (int)((remaining + 999) / 1000) : 0);
    set_text(ui, GSP_BIND_PEER_MODAL_TIMER, timer);
}

static void render_show(esp_gsp_handle_t ui, bool is_text, const char *value)
{
    set_visible(ui, GSP_BIND_PEER_FACE_VISIBLE, !is_text);
    set_visible(ui, GSP_BIND_PEER_TEXT_VISIBLE, is_text);
    if (is_text) {
        char lines[6][PEER_LINE_LEN] = {0};
        size_t count = peer_utf8_lines(value, 10, lines, 6);
        size_t offset = count > 3 && s_show.until_ms - s_snap.now_ms < 2500 ? 3 : 0;
        for (size_t i = 0; i < 3; i++) {
            set_text(ui, s_show_line[i], lines[offset + i][0] != '\0' ? lines[offset + i] : " ");
        }
        return;
    }
    bool wink = strcmp(value, "wink") == 0;
    bool surprise = strcmp(value, "surprise") == 0;
    bool wave = strcmp(value, "wave") == 0;
    set_visible(ui, GSP_BIND_PEER_EYE_L_OPEN_VISIBLE, !wink);
    set_visible(ui, GSP_BIND_PEER_EYE_L_WINK_VISIBLE, wink);
    set_visible(ui, GSP_BIND_PEER_MOUTH_SMILE_VISIBLE, !surprise);
    set_visible(ui, GSP_BIND_PEER_MOUTH_O_VISIBLE, surprise);
    set_visible(ui, GSP_BIND_PEER_HAND_VISIBLE, wave);
    set_text(ui, GSP_BIND_PEER_SHOW_CAPTION, wink ? "眨眼" : surprise ? "惊喜" : wave ? "挥手" : "笑脸");
}

static void render_perform(esp_gsp_handle_t ui)
{
    const char *title = strcmp(s_snap.perform_show, "dance_party") == 0 ? "派对" : "打招呼";
    char caption[96];
    if (s_snap.now_ms < s_snap.perform_start_ms) {
        char count[8];
        snprintf(count, sizeof(count), "%d", (int)((s_snap.perform_start_ms - s_snap.now_ms + 999) / 1000));
        set_text(ui, GSP_BIND_PEER_PERFORM_COUNT, count);
        set_visible(ui, GSP_BIND_PEER_PERFORM_COUNT_VISIBLE, true);
        set_visible(ui, GSP_BIND_PEER_EQ_VISIBLE, false);
        snprintf(caption, sizeof(caption), "和 %s 的%s马上开始", s_snap.perform_peer_name, title);
    } else {
        set_visible(ui, GSP_BIND_PEER_PERFORM_COUNT_VISIBLE, false);
        set_visible(ui, GSP_BIND_PEER_EQ_VISIBLE, true);
        s_eq_phase = !s_eq_phase;
        for (size_t i = 0; i < 5; i++) {
            bool tall = ((i + (s_eq_phase ? 1U : 0U)) % 2U) == 0U;
            set_visible(ui, s_eq_tall[i], tall);
            set_visible(ui, s_eq_short[i], !tall);
        }
        snprintf(caption, sizeof(caption), "%s中 · %s", title, s_snap.perform_peer_name);
    }
    set_text(ui, GSP_BIND_PEER_PERFORM_CAPTION, caption);
}

static void peer_tick(esp_gsp_handle_t ui, void *user_ctx)
{
    (void)user_ctx;
    peer_agent_get_snapshot(&s_snap);
    int64_t now = s_snap.now_ms;
    peer_voice_tick(s_snap.manual_send_status, now);
    char value[192];
    portENTER_CRITICAL(&s_show_lock);
    if (s_show.pending && s_show.until_ms == 0) {
        s_show.until_ms = now + SHOW_MS;
    }
    if (s_show.pending && now > s_show.until_ms) {
        s_show.pending = false;
    }
    bool show_active = s_show.pending;
    bool is_text = s_show.is_text;
    memcpy(value, s_show.value, sizeof(value));
    portEXIT_CRITICAL(&s_show_lock);

    if ((s_snap.chat_active && !s_chat_was_active) || s_snap.chat_received_seq != s_chat_received) {
        s_chat_received = s_snap.chat_received_seq;
        /* A message from the current partner must not interrupt our own recording/retry. */
        const peer_view_t *selected = current_peer();
        if (s_snap.chat_active || selected == NULL || strcmp(selected->id, s_snap.chat_peer_id) != 0) {
            peer_voice_cancel();
            s_voice_pointer = false;
        }
        for (uint8_t i = 0; i < s_snap.peer_count; ++i) {
            if (strcmp(s_snap.peers[i].id, s_snap.chat_peer_id) == 0) s_index = i;
        }
        s_user_screen = VIEW_CHAT;
        s_chat_last_turn = -1;
        s_chat_offset = 0;
    }
    s_chat_was_active = s_snap.chat_active;
    peer_screen_t screen = peer_screen_select(s_user_screen,
        s_snap.prompt != PEER_PROMPT_NONE || s_snap.pairing_out || s_unpair_confirmation,
        show_active, s_snap.perform_pending, s_snap.chat_active);
    s_screen = screen;
    if (screen != VIEW_CHAT) { peer_voice_cancel(); s_voice_pointer = false; }
    show_screen(ui, screen);
    switch (screen) {
    case VIEW_HOME: render_home(ui); break;
    case VIEW_CAPS: render_caps(ui); break;
    case VIEW_CHAT: render_chat(ui); break;
    case VIEW_MODAL: render_modal(ui); break;
    case VIEW_SHOW: render_show(ui, is_text, value); break;
    case VIEW_PERFORM: render_perform(ui); break;
    }
    if (s_snap.message_seq != s_seen_seq) {
        s_seen_seq = s_snap.message_seq;
        if (s_snap.message[0] != '\0') {
            (void)mosaic_top_notice_show(ui, &s_notice, "伙伴", s_snap.message, 3000);
        }
    }
}

static void invoke_cap(const peer_view_t *p, size_t index)
{
    if (p == NULL || index >= p->cap_count || !p->caps[index].available) {
        return;
    }
    const char *name = p->caps[index].name;
    const char *args = "{}";
    if (strcmp(name, "robot_expression") == 0 || strcmp(name, "robot_dance") == 0) {
        args = "{\"name\":\"happy\"}";
    } else if (strcmp(name, "robot_say") == 0) {
        args = "{\"text\":\"你好，我是 Mosaico\"}";
    }
    (void)peer_agent_invoke(p->id, name, args);
}

static void peer_event(esp_gsp_handle_t ui, const struct mosaic_event *event)
{
    (void)ui;
    if (event->type == MOSAIC_EVENT_POINTER) {
        if (event->data.pointer.pressed && !s_pointer_down) {
            s_pointer_x = event->data.pointer.x;
            s_pointer_y = event->data.pointer.y;
            s_voice_pointer = s_screen == VIEW_CHAT && s_pointer_x >= 104 && s_pointer_x <= 376 && s_pointer_y >= 350 && s_pointer_y <= 398;
            s_voice_cancel = false;
            if (s_voice_pointer) peer_voice_begin(s_snap.chat_peer_id);
        } else if (!event->data.pointer.pressed && s_pointer_down && s_screen == VIEW_CHAT) {
            if (s_voice_pointer) {
                s_voice_cancel = event->data.pointer.y < s_pointer_y - 48;
                peer_voice_finish(!s_voice_cancel);
                s_voice_pointer = false; s_voice_cancel = false; s_pointer_down = false;
                return;
            }
            int dy = event->data.pointer.y - s_pointer_y;
            if (dy < -40 && s_chat_offset + 4 < s_chat_fragments) s_chat_offset++;
            else if (dy > 40 && s_chat_offset > 0) s_chat_offset--;
        } else if (!event->data.pointer.pressed && s_pointer_down && s_screen == VIEW_HOME) {
            int dx = event->data.pointer.x - s_pointer_x;
            int dy = event->data.pointer.y - s_pointer_y;
            const peer_view_t *selected = current_peer();
            if (dy < -60 && s_pointer_y < 400 && selected != NULL && selected->is_friend) {
                s_user_screen = VIEW_CAPS;
                s_caps_page = 0;
            } else if ((dx > 60 || dx < -60) && s_snap.peer_count > 1) {
                s_index = (uint8_t)((s_index + s_snap.peer_count + (dx < 0 ? 1 : -1)) % s_snap.peer_count);
            }
        }
        if (s_voice_pointer && event->data.pointer.pressed) s_voice_cancel = event->data.pointer.y < s_pointer_y - 48;
        s_pointer_down = event->data.pointer.pressed;
        return;
    }
    if (event->type != MOSAIC_EVENT_UI_CALL) {
        return;
    }
    const peer_view_t *p = current_peer();
    bool ready_friend = p != NULL && p->is_friend && p->online && p->compatible;
    switch (event->data.call.action_id) {
    case GSP_ACT_ID_PEER_NETWORK_SETTINGS: {
        const mosaic_app_descriptor_t *settings = mosaic_app_descriptor_for_name("settings");
        if (settings != NULL && s_screen == VIEW_HOME && p == NULL) (void)mosaic_loader_request(settings);
        return;
    }
    case GSP_ACT_ID_PEER_PREV:
        if (s_screen == VIEW_CAPS) { if (s_caps_page > 0) s_caps_page--; return; }
        if (s_snap.peer_count > 0) {
            s_index = (uint8_t)((s_index + s_snap.peer_count - 1U) % s_snap.peer_count);
        }
        return;
    case GSP_ACT_ID_PEER_NEXT:
        if (s_screen == VIEW_CAPS) { if (p != NULL && (s_caps_page + 1U) * 3U < p->cap_count) s_caps_page++; return; }
        if (s_snap.peer_count > 0) {
            s_index = (uint8_t)((s_index + 1U) % s_snap.peer_count);
        }
        return;
    case GSP_ACT_ID_PEER_BTN0:
        if (p == NULL || !p->online || !p->compatible) {
            return;
        }
        if (p->is_friend) {
            (void)peer_agent_perform(p->id, "hello_wave");
        } else {
            (void)peer_agent_meet(p->id);
        }
        return;
    case GSP_ACT_ID_PEER_BTN1:
        if (ready_friend) {
            if (peer_agent_open_chat(p->id) == ESP_OK) s_user_screen = VIEW_CHAT;
        }
        return;
    case GSP_ACT_ID_PEER_BTN2:
        if (ready_friend) {
            (void)peer_agent_perform(p->id, "dance_party");
        }
        return;
    case GSP_ACT_ID_PEER_OPEN_CAPS:
        if (p != NULL && p->is_friend) {
            s_caps_page = 0;
            s_user_screen = VIEW_CAPS;
        }
        return;
    case GSP_ACT_ID_PEER_CAPS_BACK: s_user_screen = VIEW_HOME; return;
    case GSP_ACT_ID_PEER_CAP_0: invoke_cap(p, s_caps_page * 3U + 0); return;
    case GSP_ACT_ID_PEER_CAP_1: invoke_cap(p, s_caps_page * 3U + 1); return;
    case GSP_ACT_ID_PEER_CAP_2: invoke_cap(p, s_caps_page * 3U + 2); return;
    case GSP_ACT_ID_PEER_CAP_3: invoke_cap(p, 3); return;
    case GSP_ACT_ID_PEER_CAP_4: invoke_cap(p, 4); return;
    case GSP_ACT_ID_PEER_CAP_5: invoke_cap(p, 5); return;
    case GSP_ACT_ID_PEER_UNPAIR:
        s_unpair_confirmation = p != NULL && p->is_friend;
        return;
    case GSP_ACT_ID_PEER_MODAL_REJECT:
        if (s_unpair_confirmation && s_snap.prompt == PEER_PROMPT_NONE) {
            s_unpair_confirmation = false;
        } else if (s_snap.prompt != PEER_PROMPT_NONE) {
            peer_agent_answer_prompt(false);
        } else {
            peer_agent_cancel_meet();
        }
        return;
    case GSP_ACT_ID_PEER_MODAL_ACCEPT:
        if (s_unpair_confirmation && s_snap.prompt == PEER_PROMPT_NONE) {
            s_unpair_confirmation = false;
            s_user_screen = VIEW_HOME;
            if (p != NULL) (void)peer_agent_unpair(p->id);
        } else if (s_snap.prompt != PEER_PROMPT_NONE) {
            peer_agent_answer_prompt(true);
        }
        return;
    case GSP_ACT_ID_PEER_CHAT_STOP:
        peer_voice_cancel();
        if (s_snap.chat_active) peer_agent_stop_chat();
        else s_user_screen = VIEW_HOME;
        return;
    case GSP_ACT_ID_PEER_CHAT_AUTO:
        peer_voice_cancel();
        (void)peer_agent_start_chat(s_snap.chat_peer_id);
        return;
    case GSP_ACT_ID_PEER_PERFORM_STOP: peer_agent_perform_finished(); return;
    case GSP_ACT_ID_PEER_SHOW_CLOSE:
        portENTER_CRITICAL(&s_show_lock);
        s_show.pending = false;
        portEXIT_CRITICAL(&s_show_lock);
        return;
    default:
        return;
    }
}

static bool peer_back(esp_gsp_handle_t ui, int64_t timestamp_us)
{
    peer_voice_cancel();
    (void)ui;
    (void)timestamp_us;
    if (s_screen == VIEW_CAPS || (s_screen == VIEW_CHAT && !s_snap.chat_active)) {
        s_user_screen = VIEW_HOME;
        return true;
    }
    if (s_screen == VIEW_SHOW) {
        portENTER_CRITICAL(&s_show_lock);
        s_show.pending = false;
        portEXIT_CRITICAL(&s_show_lock);
        return true;
    }
    return false;
}

static void peer_started(esp_gsp_handle_t ui)
{
    memset(s_text_cache, 0, sizeof(s_text_cache));
    memset(s_visible_cache, 0, sizeof(s_visible_cache));
    s_user_screen = VIEW_HOME;
    s_screen = VIEW_HOME;
    s_index = 0;
    peer_agent_get_snapshot(&s_snap);
    s_seen_seq = s_snap.message_seq;
    set_text(ui, GSP_BIND_PEER_BTN1_TEXT, "聊聊");
    set_text(ui, GSP_BIND_PEER_BTN2_TEXT, "表演");
    set_text(ui, GSP_BIND_PEER_CAPS_HINT, "看看它会什么");
    set_text(ui, GSP_BIND_PEER_CAPS_BACK_TEXT, "返回");
    set_text(ui, GSP_BIND_PEER_UNPAIR_TEXT, "解除好友");
    s_unpair_confirmation = false;
    s_pointer_down = false;
    s_caps_page = 0;
    s_chat_was_active = false;
    s_chat_last_turn = -1;
    s_chat_offset = 0;
    set_text(ui, GSP_BIND_PEER_CHAT_STOP_TEXT, "打断");
    set_text(ui, GSP_BIND_PEER_PERFORM_STOP_TEXT, "结束");
    peer_tick(ui, NULL);
    (void)esp_gsp_timer_create(ui, 250, peer_tick, NULL);
}

static void peer_stopping(esp_gsp_handle_t ui)
{
    peer_voice_cancel();
    mosaic_top_notice_detach(ui);
}

const mosaic_app_descriptor_t mosaic_peer_app = {
    .id = 52,
    .launch_action = GSP_ACT_ID_APP_PEER,
    .back_action = MOSAIC_APP_SHELL_BACK_ACTION,
    .name = "peer",
    .title = "Partner",
    .directory = &gsp_obj_directory_peer,
    .disable_swipe = true,
    .on_started = peer_started,
    .on_stopping = peer_stopping,
    .on_event = peer_event,
    .on_back = peer_back,
};

#endif
