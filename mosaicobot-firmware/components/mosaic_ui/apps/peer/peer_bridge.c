/* Device actions behind peer_agent: camera, music, screen and system notices. */
#if defined(ESP_PLATFORM)

#pragma GCC diagnostic ignored "-Wformat-truncation"

#include <dirent.h>
#include <stdio.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>

#include "../music/music_audio.h"
#include "claw_paths.h"
#include "mosaic_app_catalog.h"
#include "mosaic_app_shell.h"
#include "mosaic_loader.h"
#include "mosaic_ui.h"
#include "peer_agent.h"
#include "peer_app.h"

static music_audio_handle_t s_audio;

static void bridge_open_app(void)
{
    if (mosaic_loader_app() != &mosaic_peer_app) {
        (void)mosaic_loader_request(&mosaic_peer_app);
    }
}

static void bridge_notify(peer_notice_t notice)
{
    if (mosaic_loader_app() == &mosaic_peer_app) {
        return;
    }
    static const mosaic_system_notice_t map[] = {
        [PEER_NOTICE_ONLINE] = MOSAIC_SYSTEM_NOTICE_PEER_ONLINE,
        [PEER_NOTICE_COMMAND] = MOSAIC_SYSTEM_NOTICE_PEER_COMMAND,
        [PEER_NOTICE_FAILED] = MOSAIC_SYSTEM_NOTICE_PEER_FAILED,
        [PEER_NOTICE_REQUEST] = MOSAIC_SYSTEM_NOTICE_PEER_REQUEST,
    };
    (void)mosaic_loader_show_system_notice(map[notice], 3000);
    (void)mosaic_ui_haptic_feedback(40);
}

static esp_err_t bridge_show_emoji(const char *name)
{
    peer_app_request_show(false, name);
    bridge_open_app();
    return ESP_OK;
}

static esp_err_t bridge_show_text(const char *text)
{
    peer_app_request_show(true, text);
    bridge_open_app();
    return ESP_OK;
}

static esp_err_t bridge_take_photo(char *saved, size_t saved_size)
{
    return mosaic_camera_app_take_photo(saved, saved_size);
}

static bool is_track(const char *file)
{
    const char *dot = strrchr(file, '.');
    return dot != NULL && (strcasecmp(dot, ".mp3") == 0 || strcasecmp(dot, ".aac") == 0 ||
                           strcasecmp(dot, ".wav") == 0 || strcasecmp(dot, ".flac") == 0);
}

static esp_err_t find_track(const char *name, char *path, size_t path_size, uint64_t *bytes)
{
    char dir[128];
    esp_err_t err = claw_paths_join(CLAW_PATH_DATA, "music", dir, sizeof(dir));
    if (err != ESP_OK) {
        return err;
    }
    DIR *d = opendir(dir);
    if (d == NULL) {
        return ESP_ERR_NOT_FOUND;
    }
    char first[128] = {0};
    bool found = false;
    struct dirent *ent = NULL;
    while ((ent = readdir(d)) != NULL) {
        if (!is_track(ent->d_name)) {
            continue;
        }
        if (first[0] == '\0') {
            snprintf(first, sizeof(first), "%s", ent->d_name);
        }
        if (name != NULL && name[0] != '\0' && strstr(ent->d_name, name) != NULL) {
            snprintf(path, path_size, "%s/%s", dir, ent->d_name);
            found = true;
            break;
        }
    }
    closedir(d);
    if (!found) {
        if (first[0] == '\0' || (name != NULL && name[0] != '\0')) {
            return ESP_ERR_NOT_FOUND;
        }
        snprintf(path, path_size, "%s/%s", dir, first);
    }
    struct stat st;
    if (stat(path, &st) != 0) {
        return ESP_FAIL;
    }
    *bytes = (uint64_t)st.st_size;
    return ESP_OK;
}

static esp_err_t bridge_play_music(const char *name, char *playing, size_t playing_size)
{
    char path[256];
    uint64_t bytes = 0;
    esp_err_t err = find_track(name, path, sizeof(path), &bytes);
    if (err != ESP_OK) {
        return err;
    }
    if (s_audio == NULL) {
        err = music_audio_create(&s_audio);
        if (err != ESP_OK) {
            return err;
        }
    }
    err = music_audio_play(s_audio, path, bytes);
    if (err == ESP_OK && playing != NULL) {
        const char *slash = strrchr(path, '/');
        snprintf(playing, playing_size, "%s", slash != NULL ? slash + 1 : path);
    }
    return err;
}

static esp_err_t bridge_stop_music(void)
{
    return s_audio != NULL ? music_audio_stop(s_audio) : ESP_OK;
}

static const peer_agent_ui_ops_t s_ops = {
    .take_photo = bridge_take_photo,
    .play_music = bridge_play_music,
    .stop_music = bridge_stop_music,
    .show_emoji = bridge_show_emoji,
    .show_text = bridge_show_text,
    .notify = bridge_notify,
    .open_app = bridge_open_app,
};

const peer_agent_ui_ops_t *mosaic_ui_peer_ops(void)
{
    return &s_ops;
}

#endif
