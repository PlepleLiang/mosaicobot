#include "peer_rpc_archive.h"
#include "claw_paths.h"
#include "peer_core.h"
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#ifdef _WIN32
#include <direct.h>
#include <io.h>
#define make_dir(path) _mkdir(path)
#define sync_file(file) _commit(_fileno(file))
#else
#include <unistd.h>
#define make_dir(path) mkdir(path, 0700)
#define sync_file(file) fsync(fileno(file))
#endif

static esp_err_t archive_path(const char *peer, const char *id, char *path, size_t size)
{
    if (!peer_manual_valid(peer, "x") || !peer_manual_valid(id, "x")) return ESP_ERR_INVALID_ARG;
    const char *root = claw_paths_get(CLAW_PATH_DATA);
    struct stat info;
    if (!root || stat(root, &info) != 0 || !(info.st_mode & S_IFDIR)) return ESP_ERR_INVALID_STATE;
    char dir[256];
    int n = snprintf(dir, sizeof(dir), "%s/peer-inbox", root);
    if (n < 0 || n >= (int)sizeof(dir)) return ESP_ERR_INVALID_SIZE;
    if (make_dir(dir) != 0 && errno != EEXIST) return ESP_FAIL;
    n = snprintf(dir, sizeof(dir), "%s/peer-inbox/%s", root, peer);
    if (n < 0 || n >= (int)sizeof(dir)) return ESP_ERR_INVALID_SIZE;
    if (make_dir(dir) != 0 && errno != EEXIST) return ESP_FAIL;
    n = snprintf(path, size, "%s/%s.json", dir, id);
    return n >= 0 && n < (int)size ? ESP_OK : ESP_ERR_INVALID_SIZE;
}

esp_err_t peer_rpc_archive_read(const char *peer, const char *id, char *out, size_t size)
{
    char path[320];
    esp_err_t err = archive_path(peer, id, path, sizeof(path));
    if (err != ESP_OK || size < 2) return err != ESP_OK ? err : ESP_ERR_INVALID_SIZE;
    FILE *file = fopen(path, "rb");
    if (!file) return errno == ENOENT ? ESP_ERR_NOT_FOUND : ESP_FAIL;
    size_t n = fread(out, 1, size - 1, file);
    bool ok = !ferror(file) && fgetc(file) == EOF;
    fclose(file); out[n] = 0;
    return ok ? ESP_OK : ESP_ERR_INVALID_SIZE;
}

esp_err_t peer_rpc_archive_write(const char *peer, const char *id, const char *json)
{
    size_t len = strlen(json);
    char *existing = malloc(len + 2);
    if (!existing) return ESP_ERR_NO_MEM;
    esp_err_t previous = peer_rpc_archive_read(peer, id, existing, len + 2);
    bool same = previous == ESP_OK && !strcmp(existing, json);
    free(existing);
    if (previous != ESP_ERR_NOT_FOUND) return previous == ESP_OK ? (same ? ESP_OK : ESP_FAIL) : previous;
    char path[320], temp[328];
    esp_err_t err = archive_path(peer, id, path, sizeof(path));
    if (err != ESP_OK) return err;
    snprintf(temp, sizeof(temp), "%s.tmp", path);
    FILE *file = fopen(temp, "wb");
    if (!file) return ESP_FAIL;
    bool ok = fwrite(json, 1, len, file) == len && fflush(file) == 0 && sync_file(file) == 0;
    if (fclose(file) != 0) ok = false;
    if (!ok) return ESP_FAIL;
    /* NVS still owns the original until this succeeds. A torn temp file is
       never used as a completed result; the next archive attempt replaces it. */
    return rename(temp, path) == 0 ? ESP_OK : ESP_FAIL;
}
