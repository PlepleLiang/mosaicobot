#include <stdio.h>

#include "../../mosaic_ui/apps/peer/peer_screen.h"

/* Report failures without abort(), so host runners get a normal exit code. */
#define CHECK(condition) do { \
    if (!(condition)) { \
        fprintf(stderr, "FAIL line %d: %s\n", __LINE__, #condition); \
        return 1; \
    } \
} while (0)

int main(void)
{
    /* An incoming manual request already navigates to chat before its tool runs.
       Both show_emoji and show_text must still reach the display page. */
    CHECK(peer_screen_select(VIEW_CHAT, false, true, false, false) == VIEW_SHOW);
    CHECK(peer_screen_select(VIEW_CHAT, false, true, false, true) == VIEW_SHOW);
    CHECK(peer_screen_select(VIEW_HOME, false, true, false, false) == VIEW_SHOW);
    CHECK(peer_screen_select(VIEW_CAPS, false, true, false, true) == VIEW_SHOW);
    /* Further chat arrivals must not hide an in-progress display action. */
    CHECK(peer_screen_select(VIEW_CHAT, false, true, true, true) == VIEW_SHOW);
    /* Closing/timing out the display returns to the existing conversation. */
    CHECK(peer_screen_select(VIEW_CHAT, false, false, false, false) == VIEW_CHAT);
    CHECK(peer_screen_select(VIEW_CHAT, false, false, false, true) == VIEW_CHAT);
    /* Explicit performances have the same precedence over an open chat. */
    CHECK(peer_screen_select(VIEW_CHAT, false, false, true, false) == VIEW_PERFORM);
    CHECK(peer_screen_select(VIEW_CHAT, false, false, true, true) == VIEW_PERFORM);
    /* Pairing, sensitive prompts and local confirmation always stay on top. */
    CHECK(peer_screen_select(VIEW_CHAT, true, true, true, true) == VIEW_MODAL);
    CHECK(peer_screen_select(VIEW_HOME, true, true, false, false) == VIEW_MODAL);
    /* Existing manual navigation and ordinary chat auto-open are unchanged. */
    CHECK(peer_screen_select(VIEW_HOME, false, false, false, true) == VIEW_CHAT);
    CHECK(peer_screen_select(VIEW_CAPS, false, false, false, true) == VIEW_CAPS);
    CHECK(peer_screen_select(VIEW_HOME, false, false, false, false) == VIEW_HOME);
    puts("Peer screen priority: 14 assertions passed");
    return 0;
}
