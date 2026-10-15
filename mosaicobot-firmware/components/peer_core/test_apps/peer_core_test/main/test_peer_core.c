#include <stdlib.h>
#include <string.h>

#include "cJSON.h"
#include "peer_core.h"
#include "unity.h"

static uint32_t s_seq;
TEST_CASE("manual chat validates ids utf8 and length without six turn semantics", "[peer]")
{
    const char *id = "0123456789abcdef0123456789abcdef";
    TEST_ASSERT_TRUE(peer_manual_valid(id, "你好，[END] 是文字"));
    TEST_ASSERT_FALSE(peer_manual_valid("1234abcd", "hello"));
    TEST_ASSERT_FALSE(peer_manual_valid(id, " \t\n"));
    TEST_ASSERT_FALSE(peer_manual_valid(id, "　"));
    TEST_ASSERT_FALSE(peer_manual_valid(id, "\xc0\x80"));
    TEST_ASSERT_FALSE(peer_manual_valid(id, "\xed\xa0\x80"));
    char text[PEER_MANUAL_TEXT_LEN];
    for (int i = 0; i < 120; ++i) memcpy(text + i * 3, "好", 3);
    text[360] = 0;
    TEST_ASSERT_TRUE(peer_manual_valid(id, text));
    memcpy(text + 360, "好", 4);
    TEST_ASSERT_FALSE(peer_manual_valid(id, text));
}
static uint32_t fake_rng(void) { return s_seq++; }

static peer_endpoint_t make_ep(const char *id, const char *name)
{
    peer_endpoint_t ep = {0};
    peer_copy(ep.id, sizeof(ep.id), id);
    peer_copy(ep.name, sizeof(ep.name), name);
    peer_copy(ep.kind, sizeof(ep.kind), "stackchan");
    peer_copy(ep.version, sizeof(ep.version), "1");
    peer_copy(ep.host, sizeof(ep.host), "10.0.0.7");
    ep.port = 18791;
    peer_copy(ep.path, sizeof(ep.path), "mcp");
    return ep;
}

TEST_CASE("txt round trip keeps identity", "[peer]")
{
    peer_txt_item_t items[6];
    size_t n = peer_txt_build("abc123", "Mosaico", "mosaico", items);
    peer_endpoint_t ep;
    TEST_ASSERT_TRUE(peer_txt_parse(items, n, "192.168.1.9", 18791, &ep));
    TEST_ASSERT_EQUAL_STRING("abc123", ep.id);
    TEST_ASSERT_EQUAL_STRING("Mosaico", ep.name);
    TEST_ASSERT_EQUAL_STRING("mosaico", ep.kind);
    TEST_ASSERT_EQUAL_STRING("mcp", ep.path);
    TEST_ASSERT_EQUAL(18791, ep.port);
}

TEST_CASE("txt ignores non partner and falls back", "[peer]")
{
    peer_endpoint_t ep;
    peer_txt_item_t plain[] = {{"endpoint", "mcp"}};
    TEST_ASSERT_FALSE(peer_txt_parse(plain, 1, "10.0.0.2", 18791, &ep));
    peer_txt_item_t bare[] = {{"agent", "1"}, {"id", "ffff0000aaaa9999"}, {"ep", "/mcp"}};
    TEST_ASSERT_TRUE(peer_txt_parse(bare, 3, "10.0.0.2", 80, &ep));
    TEST_ASSERT_EQUAL_STRING("9999", ep.name);
    TEST_ASSERT_EQUAL_STRING("other", ep.kind);
    TEST_ASSERT_EQUAL_STRING("mcp", ep.path);
}

TEST_CASE("utf8 helpers never split characters", "[peer]")
{
    char s[16] = "\xE4\xBC\x99\xE4\xBC\xB4" "ab"; /* 伙伴ab */
    peer_utf8_truncate(s, 4);
    TEST_ASSERT_EQUAL_STRING("\xE4\xBC\x99", s);
    char small[5];
    peer_copy(small, sizeof(small), "\xE4\xBC\x99\xE4\xBC\xB4");
    TEST_ASSERT_EQUAL_STRING("\xE4\xBC\x99", small);
    char lines[3][PEER_LINE_LEN];
    TEST_ASSERT_EQUAL(2, peer_utf8_lines("abcdef", 4, lines, 3));
    TEST_ASSERT_EQUAL_STRING("abcd", lines[0]);
    TEST_ASSERT_EQUAL_STRING("ef", lines[1]);
}

TEST_CASE("version compatibility uses major", "[peer]")
{
    TEST_ASSERT_TRUE(peer_version_compatible("1"));
    TEST_ASSERT_TRUE(peer_version_compatible("1.3"));
    TEST_ASSERT_FALSE(peer_version_compatible("2"));
    TEST_ASSERT_FALSE(peer_version_compatible(""));
}

TEST_CASE("call body carries tool and args", "[peer]")
{
    const char *keys[] = {"peer_id", "name"};
    const char *vals[] = {"me", "smile"};
    char *body = peer_json_call_body(5, "show_emoji", keys, vals, 2);
    cJSON *root = cJSON_Parse(body);
    TEST_ASSERT_EQUAL_STRING("tools/call", cJSON_GetObjectItem(root, "method")->valuestring);
    cJSON *params = cJSON_GetObjectItem(root, "params");
    TEST_ASSERT_EQUAL_STRING("show_emoji", cJSON_GetObjectItem(params, "name")->valuestring);
    TEST_ASSERT_EQUAL_STRING("smile", cJSON_GetObjectItem(cJSON_GetObjectItem(params, "arguments"), "name")->valuestring);
    cJSON_Delete(root);
    free(body);
}

TEST_CASE("tool text joins content and rejects errors", "[peer]")
{
    char out[64];
    TEST_ASSERT_TRUE(peer_json_tool_text(
        "{\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"ok\\\":\"},{\"type\":\"text\",\"text\":\"true}\"}]}}",
        out, sizeof(out)));
    TEST_ASSERT_EQUAL_STRING("{\"ok\":true}", out);
    TEST_ASSERT_FALSE(peer_json_tool_text("{\"error\":{\"code\":-1,\"message\":\"x\"}}", out, sizeof(out)));
    TEST_ASSERT_FALSE(peer_json_tool_text("garbage", out, sizeof(out)));
}

TEST_CASE("reply helpers", "[peer]")
{
    char buf[96];
    char kind[24];
    peer_reply_error(buf, sizeof(buf), "denied", "no");
    TEST_ASSERT_FALSE(peer_reply_is_ok(buf, kind, sizeof(kind)));
    TEST_ASSERT_EQUAL_STRING("denied", kind);
    TEST_ASSERT_TRUE(peer_reply_is_ok("{\"ok\":true}", kind, sizeof(kind)));
    TEST_ASSERT_TRUE(peer_reply_is_ok("plain text", NULL, 0));
}

TEST_CASE("registry tracks online friends and tokens", "[peer]")
{
    peer_registry_t r;
    peer_registry_init(&r, "self");
    peer_endpoint_t me = make_ep("self", "Me");
    TEST_ASSERT_NULL(peer_registry_seen(&r, &me, 0, NULL));
    peer_endpoint_t ep = make_ep("p1", "Amoo");
    bool is_new = false;
    TEST_ASSERT_NOT_NULL(peer_registry_seen(&r, &ep, 1000, &is_new));
    TEST_ASSERT_TRUE(is_new);
    TEST_ASSERT_NOT_NULL(peer_registry_seen(&r, &ep, 2000, &is_new));
    TEST_ASSERT_FALSE(is_new);
    TEST_ASSERT_TRUE(peer_registry_add_friend(&r, "p1", "Amoo", "stackchan", "tok"));
    TEST_ASSERT_TRUE(peer_registry_check_token(&r, "p1", "tok"));
    TEST_ASSERT_FALSE(peer_registry_check_token(&r, "p1", "bad"));
    TEST_ASSERT_FALSE(peer_registry_check_token(&r, "p2", "tok"));
    TEST_ASSERT_EQUAL(1, peer_registry_tick(&r, 2000 + PEER_ONLINE_TIMEOUT_MS + 1));
    peer_entry_t *e = peer_registry_find(&r, "p1");
    TEST_ASSERT_NOT_NULL(e);
    TEST_ASSERT_FALSE(e->online);
    TEST_ASSERT_EQUAL_PTR(e, peer_registry_find_name(&r, "amoo"));

    char *json = peer_registry_friends_json(&r);
    peer_registry_t loaded;
    peer_registry_init(&loaded, "self");
    TEST_ASSERT_EQUAL(1, peer_registry_load_friends_json(&loaded, json));
    TEST_ASSERT_TRUE(peer_registry_check_token(&loaded, "p1", "tok"));
    free(json);

    TEST_ASSERT_TRUE(peer_registry_remove_friend(&r, "p1"));
    TEST_ASSERT_NULL(peer_registry_find(&r, "p1"));
}

TEST_CASE("registry drops offline strangers", "[peer]")
{
    peer_registry_t r;
    peer_registry_init(&r, "self");
    peer_endpoint_t ep = make_ep("x", "X");
    peer_registry_seen(&r, &ep, 0, NULL);
    peer_registry_tick(&r, PEER_ONLINE_TIMEOUT_MS + 1);
    TEST_ASSERT_NULL(peer_registry_find(&r, "x"));
}

TEST_CASE("registry caps from hello", "[peer]")
{
    peer_registry_t r;
    peer_registry_init(&r, "self");
    peer_endpoint_t ep = make_ep("p1", "Amoo");
    peer_entry_t *e = peer_registry_seen(&r, &ep, 0, NULL);
    TEST_ASSERT_TRUE(peer_registry_set_caps_from_hello(e,
        "{\"id\":\"p1\",\"caps\":[{\"name\":\"robot_say\",\"title\":\"say\",\"params\":[\"text\"]},"
        "{\"name\":\"robot_look\",\"title\":\"look\",\"sensitive\":true,\"available\":false,\"params\":[]}]}"));
    TEST_ASSERT_EQUAL(2, e->cap_count);
    TEST_ASSERT_EQUAL_STRING("text", e->caps[0].params);
    TEST_ASSERT_TRUE(e->caps[0].available);
    TEST_ASSERT_TRUE(e->caps[1].sensitive);
    TEST_ASSERT_FALSE(e->caps[1].available);
}

TEST_CASE("capability whitelist rejects powerful or wrong-kind tools", "[peer]")
{
    TEST_ASSERT_TRUE(peer_cap_allowed("mosaico", "take_photo"));
    TEST_ASSERT_TRUE(peer_cap_allowed("stackchan", "robot_nod"));
    TEST_ASSERT_FALSE(peer_cap_allowed("mosaico", "cap_lua"));
    TEST_ASSERT_FALSE(peer_cap_allowed("mosaico", "cap_cli"));
    TEST_ASSERT_FALSE(peer_cap_allowed("stackchan", "take_photo"));
    TEST_ASSERT_FALSE(peer_cap_allowed("other", "robot_nod"));
    peer_entry_t e = {0};
    peer_copy(e.ep.kind, sizeof(e.ep.kind), "stackchan");
    TEST_ASSERT_TRUE(peer_registry_set_caps_from_hello(&e,
        "{\"caps\":[{\"name\":\"cap_system\"},{\"name\":\"peer_say\"},{\"name\":\"robot_look\",\"sensitive\":false}]}"));
    TEST_ASSERT_EQUAL(1, e.cap_count);
    TEST_ASSERT_TRUE(e.caps[0].sensitive);
}

TEST_CASE("pairing outgoing busy cooldown finish", "[peer]")
{
    peer_pairing_t p;
    peer_pairing_init(&p);
    TEST_ASSERT_EQUAL(PEER_PAIR_PENDING, peer_pairing_start_out(&p, "m1", "1234", 0));
    TEST_ASSERT_EQUAL(PEER_PAIR_BUSY, peer_pairing_start_out(&p, "m2", "5678", 0));
    TEST_ASSERT_FALSE(peer_pairing_finish_out(&p, "m1", "0000", true, 0));
    TEST_ASSERT_TRUE(peer_pairing_finish_out(&p, "m1", "1234", false, 0));
    TEST_ASSERT_EQUAL(PEER_PAIR_COOLDOWN, peer_pairing_start_out(&p, "m1", "1111", 10));
    TEST_ASSERT_EQUAL(PEER_PAIR_PENDING, peer_pairing_start_out(&p, "m1", "1111", PEER_PAIR_COOLDOWN_MS + 1));
    TEST_ASSERT_FALSE(peer_pairing_expire_out(&p, PEER_PAIR_COOLDOWN_MS + 2));
    TEST_ASSERT_TRUE(peer_pairing_expire_out(&p, PEER_PAIR_COOLDOWN_MS + 1 + PEER_PAIR_TIMEOUT_MS));
    TEST_ASSERT_FALSE(p.out_active);
}

TEST_CASE("pairing incoming busy cooldown expire", "[peer]")
{
    peer_pairing_t p;
    peer_pairing_init(&p);
    TEST_ASSERT_EQUAL(PEER_PAIR_PENDING, peer_pairing_receive(&p, "a", "A", "stackchan", "1234", 0));
    TEST_ASSERT_EQUAL(PEER_PAIR_BUSY, peer_pairing_receive(&p, "b", "B", "other", "9999", 0));
    peer_pairing_resolve_in(&p, false, 0);
    TEST_ASSERT_EQUAL(PEER_PAIR_COOLDOWN, peer_pairing_receive(&p, "a", "A", "stackchan", "1234", 5));
    TEST_ASSERT_EQUAL(PEER_PAIR_PENDING, peer_pairing_receive(&p, "c", "C", "other", "2222", 10));
    TEST_ASSERT_TRUE(peer_pairing_expire_in(&p, 10 + PEER_PAIR_TIMEOUT_MS));
    TEST_ASSERT_FALSE(p.in_active);
    TEST_ASSERT_EQUAL_STRING("pending", peer_pair_status_str(PEER_PAIR_PENDING));
}

TEST_CASE("random helpers", "[peer]")
{
    char hex[33];
    s_seq = 0;
    peer_random_hex(hex, 32, fake_rng);
    TEST_ASSERT_EQUAL(32, strlen(hex));
    TEST_ASSERT_EQUAL_STRING_LEN("0123456789abcdef", hex, 16);
    char code[PEER_CODE_LEN];
    peer_random_code(code, fake_rng);
    TEST_ASSERT_EQUAL(4, strlen(code));
}

TEST_CASE("chat and perform helpers", "[peer]")
{
    TEST_ASSERT_FALSE(peer_chat_should_reply(0));
    TEST_ASSERT_TRUE(peer_chat_should_reply(5));
    TEST_ASSERT_FALSE(peer_chat_should_reply(6));
    char out[64];
    TEST_ASSERT_TRUE(peer_chat_clean_reply("  bye [END] \n", out, sizeof(out)));
    TEST_ASSERT_EQUAL_STRING("bye", out);
    TEST_ASSERT_FALSE(peer_chat_clean_reply("hello", out, sizeof(out)));

    char payload[96];
    peer_perform_payload("dance_party", 1700000003000LL, payload, sizeof(payload));
    char show[PEER_SHOW_LEN];
    int64_t start = 0;
    TEST_ASSERT_TRUE(peer_perform_parse(payload, show, &start));
    TEST_ASSERT_EQUAL_STRING("dance_party", show);
    TEST_ASSERT_TRUE(start == 1700000003000LL);
    TEST_ASSERT_FALSE(peer_perform_parse("{\"show\":\"moonwalk\",\"start_at\":\"1\"}", show, &start));
}

TEST_CASE("pairing rejects late results and replacement requests", "[peer]")
{
    peer_pairing_t p;
    peer_pairing_init(&p);
    peer_pairing_start_out(&p, "a", "1234", 0);
    TEST_ASSERT_FALSE(peer_pairing_finish_out(&p, "a", "1234", true, PEER_PAIR_TIMEOUT_MS));
    TEST_ASSERT_EQUAL(PEER_PAIR_PENDING, peer_pairing_receive(&p, "b", "B", "stackchan", "2345", 40000));
    TEST_ASSERT_EQUAL(PEER_PAIR_BUSY, peer_pairing_receive(&p, "b", "B", "stackchan", "9876", 41000));
    TEST_ASSERT_EQUAL_STRING("2345", p.in_code);
    TEST_ASSERT_EQUAL(40000, p.in_received_ms);
    TEST_ASSERT_EQUAL(PEER_PAIR_BUSY, peer_pairing_start_out(&p, "c", "5678", 41000));
}

void app_main(void)
{
#if CONFIG_IDF_TARGET_LINUX
    UNITY_BEGIN();
    unity_run_all_tests();
    exit(UNITY_END() == 0 ? 0 : 1);
#else
    unity_run_menu();
#endif
}
