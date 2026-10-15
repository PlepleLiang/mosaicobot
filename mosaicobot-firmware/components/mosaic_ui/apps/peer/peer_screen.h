#pragma once

#include <stdbool.h>

typedef enum { VIEW_HOME = 0, VIEW_CAPS, VIEW_CHAT, VIEW_MODAL, VIEW_SHOW, VIEW_PERFORM } peer_screen_t;

/* Shared by the UI tick and host regression tests. */
static inline peer_screen_t peer_screen_select(peer_screen_t user_screen, bool modal,
                                               bool show, bool perform, bool chat_active)
{
    if (modal) return VIEW_MODAL;
    /* Explicit device actions temporarily override chat; keep user_screen so
       finishing or closing the action returns to the same conversation. */
    if (show) return VIEW_SHOW;
    if (perform) return VIEW_PERFORM;
    if (user_screen == VIEW_CHAT) return VIEW_CHAT;
    if (chat_active && user_screen != VIEW_CAPS) return VIEW_CHAT;
    return user_screen;
}
