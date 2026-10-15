/* Compile the real lifecycle implementation; only the platform calls are faked. */
#include <assert.h>
#include <string.h>
#include "../src/peer_server.c"

static const char *ID = "0123456789abcdef0123456789abcdef";
static int fail_step, step, cleanups, tools, registrations, starts, txts;
static bool engine, running;

static esp_err_t next_step(void)
{
    return ++step == fail_step ? ESP_ERR_NO_MEM : ESP_OK;
}

const char *esp_err_to_name(esp_err_t err)
{
    return err == ESP_OK ? "ESP_OK" : "ESP_ERR_NO_MEM";
}

esp_err_t mcp_mdns_set_config(const mcp_mdns_config_t *cfg)
{
    assert(!running);
    assert(strcmp(cfg->instance_name, "Mosaico-0123") == 0);
    assert(strcmp(cfg->endpoint, PEER_ENDPOINT) == 0);
    assert(cfg->server_port == PEER_PORT);
    return next_step();
}

esp_err_t cap_mcp_server_init(void)
{
    assert(!engine && tools == 0);
    esp_err_t err = next_step();
    engine = err == ESP_OK;
    return err;
}

esp_err_t peer_tools_register(void)
{
    assert(engine && tools == 0); /* Never register twice into a surviving engine. */
    registrations++;
    tools = 13; /* A failed registration may already have added some tools. */
    return next_step();
}

esp_err_t cap_mcp_server_start(void)
{
    assert(engine && tools == 13 && !running);
    starts++;
    esp_err_t err = next_step();
    running = err == ESP_OK;
    return err;
}

esp_err_t mdns_service_txt_item_set(const char *service, const char *proto,
                                    const char *key, const char *value)
{
    assert(running);
    assert(strcmp(service, "_mcp") == 0 && strcmp(proto, "_tcp") == 0);
    peer_txt_item_t expected[6];
    assert(peer_txt_build(ID, "Mosaico", "mosaico", expected) == 6);
    assert(txts < 6 && strcmp(key, expected[txts].key) == 0);
    assert(strcmp(value, expected[txts].value) == 0);
    txts++;
    return next_step();
}

esp_err_t cap_mcp_server_deinit(void)
{
    cleanups++;
    engine = running = false;
    tools = 0;
    return ESP_OK;
}

static void reset(int failure)
{
    s_ready = engine = running = false;
    step = cleanups = tools = registrations = starts = txts = 0;
    fail_step = failure;
}

int main(void)
{
    /* Config, engine, partial tools, HTTP/mDNS startup, and each of six TXT writes. */
    for (int failure = 1; failure <= 10; failure++) {
        reset(failure);
        assert(peer_server_start(ID, "Mosaico") == ESP_ERR_NO_MEM);
        assert(!s_ready && !engine && !running && tools == 0);
        assert(cleanups == (failure == 1 ? 0 : 1));
        fail_step = step = txts = 0;
        assert(peer_server_start(ID, "Mosaico") == ESP_OK);
        assert(s_ready && running && tools == 13 && txts == 6);
        int old_step = step, old_registrations = registrations, old_starts = starts;
        assert(peer_server_start(ID, "Mosaico") == ESP_OK);
        assert(step == old_step && registrations == old_registrations && starts == old_starts);
    }
    reset(0);
    assert(peer_server_start(ID, "Mosaico") == ESP_OK);
    assert(cleanups == 0 && registrations == 1 && starts == 1 && txts == 6);
    puts("peer_server: 11 lifecycle cases passed (10 failures -> retry + idempotency, clean start)");
    return 0;
}
