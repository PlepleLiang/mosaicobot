#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include "../src/peer_rpc_archive.c"

static char data_root[256];
const char *claw_paths_get(claw_path_root_t root) { (void)root; return data_root; }

int main(void)
{
    snprintf(data_root, sizeof(data_root), "peer-archive-test-%lu-%lu", (unsigned long)time(NULL), (unsigned long)clock());
    assert(make_dir(data_root) == 0);
    const char *peer = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", *id = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    char out[128];
    assert(peer_rpc_archive_read(peer, id, out, sizeof(out)) == ESP_ERR_NOT_FOUND);
    assert(peer_rpc_archive_write(peer, id, "{\"answer\":\"real\"}") == ESP_OK);
    assert(peer_rpc_archive_read(peer, id, out, sizeof(out)) == ESP_OK);
    assert(!strcmp(out, "{\"answer\":\"real\"}"));
    assert(peer_rpc_archive_write(peer, id, out) == ESP_OK);
    assert(peer_rpc_archive_write(peer, id, "changed") != ESP_OK);
    assert(peer_rpc_archive_read(peer, id, out, 4) == ESP_ERR_INVALID_SIZE);
    assert(peer_rpc_archive_write("../outside", id, "bad") == ESP_ERR_INVALID_ARG);
    snprintf(data_root, sizeof(data_root), "absent-root-%lu", (unsigned long)time(NULL));
    assert(peer_rpc_archive_read(peer, id, out, sizeof(out)) == ESP_ERR_INVALID_STATE);
    puts("peer archive: durable round trip, missing/corrupt-size/unmounted state and path validation passed");
    return 0;
}
