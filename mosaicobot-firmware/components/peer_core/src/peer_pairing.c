#include <string.h>

#include "peer_core.h"

static bool cooling(const peer_cooldown_t *list, const char *id, int64_t now_ms)
{
    for (size_t i = 0; i < PEER_MAX_COOLDOWNS; i++) {
        if (list[i].id[0] != '\0' && strcmp(list[i].id, id) == 0 && now_ms < list[i].until_ms) {
            return true;
        }
    }
    return false;
}

static void cool(peer_cooldown_t *list, const char *id, int64_t now_ms)
{
    peer_cooldown_t *slot = NULL;
    for (size_t i = 0; i < PEER_MAX_COOLDOWNS && slot == NULL; i++) {
        if (strcmp(list[i].id, id) == 0) {
            slot = &list[i];
        }
    }
    for (size_t i = 0; i < PEER_MAX_COOLDOWNS && slot == NULL; i++) {
        if (list[i].id[0] == '\0' || list[i].until_ms <= now_ms) {
            slot = &list[i];
        }
    }
    if (slot == NULL) {
        slot = &list[0];
        for (size_t i = 1; i < PEER_MAX_COOLDOWNS; i++) {
            if (list[i].until_ms < slot->until_ms) {
                slot = &list[i];
            }
        }
    }
    peer_copy(slot->id, sizeof(slot->id), id);
    slot->until_ms = now_ms + PEER_PAIR_COOLDOWN_MS;
}

void peer_pairing_init(peer_pairing_t *p)
{
    memset(p, 0, sizeof(*p));
}

peer_pair_status_t peer_pairing_start_out(peer_pairing_t *p, const char *peer_id, const char *code, int64_t now_ms)
{
    if (p->out_active || p->in_active) {
        return PEER_PAIR_BUSY;
    }
    if (cooling(p->out_cool, peer_id, now_ms)) {
        return PEER_PAIR_COOLDOWN;
    }
    p->out_active = true;
    peer_copy(p->out_peer_id, sizeof(p->out_peer_id), peer_id);
    peer_copy(p->out_code, sizeof(p->out_code), code);
    p->out_started_ms = now_ms;
    return PEER_PAIR_PENDING;
}

bool peer_pairing_finish_out(peer_pairing_t *p, const char *from_id, const char *code, bool accepted, int64_t now_ms)
{
    if (!p->out_active || from_id == NULL || code == NULL ||
            strcmp(p->out_peer_id, from_id) != 0 || strcmp(p->out_code, code) != 0) {
        return false;
    }
    if (now_ms - p->out_started_ms >= PEER_PAIR_TIMEOUT_MS) {
        peer_pairing_fail_out(p, now_ms);
        return false;
    }
    p->out_active = false;
    if (!accepted) {
        cool(p->out_cool, from_id, now_ms);
    }
    return true;
}

void peer_pairing_fail_out(peer_pairing_t *p, int64_t now_ms)
{
    if (p->out_active) {
        cool(p->out_cool, p->out_peer_id, now_ms);
    }
    p->out_active = false;
}

bool peer_pairing_expire_out(peer_pairing_t *p, int64_t now_ms)
{
    if (!p->out_active || now_ms - p->out_started_ms < PEER_PAIR_TIMEOUT_MS) {
        return false;
    }
    peer_pairing_fail_out(p, now_ms);
    return true;
}

peer_pair_status_t peer_pairing_receive(peer_pairing_t *p, const char *from_id, const char *from_name,
                                        const char *from_kind, const char *code, int64_t now_ms)
{
    if (cooling(p->in_cool, from_id, now_ms)) {
        return PEER_PAIR_COOLDOWN;
    }
    if (p->in_active || p->out_active) {
        return PEER_PAIR_BUSY;
    }
    p->in_active = true;
    peer_copy(p->in_from_id, sizeof(p->in_from_id), from_id);
    peer_copy(p->in_from_name, sizeof(p->in_from_name), from_name);
    peer_copy(p->in_from_kind, sizeof(p->in_from_kind), from_kind);
    peer_copy(p->in_code, sizeof(p->in_code), code);
    p->in_received_ms = now_ms;
    return PEER_PAIR_PENDING;
}

void peer_pairing_resolve_in(peer_pairing_t *p, bool accepted, int64_t now_ms)
{
    if (!p->in_active) {
        return;
    }
    if (!accepted) {
        cool(p->in_cool, p->in_from_id, now_ms);
    }
    p->in_active = false;
}

bool peer_pairing_expire_in(peer_pairing_t *p, int64_t now_ms)
{
    if (!p->in_active || now_ms - p->in_received_ms < PEER_PAIR_TIMEOUT_MS) {
        return false;
    }
    peer_pairing_resolve_in(p, false, now_ms);
    return true;
}

const char *peer_pair_status_str(peer_pair_status_t status)
{
    switch (status) {
    case PEER_PAIR_PENDING: return "pending";
    case PEER_PAIR_BUSY: return "busy";
    case PEER_PAIR_COOLDOWN: return "cooldown";
    default: return "busy";
    }
}
