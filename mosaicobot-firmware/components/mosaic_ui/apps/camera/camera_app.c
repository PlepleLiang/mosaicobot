/*
 * SPDX-FileCopyrightText: 2026 Espressif Systems (Shanghai) CO LTD
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "camera_actions.h"
#include "camera_binds.h"
#include "camera_objects.h"
#include "mosaic_app_catalog.h"
#include "mosaic_hub_actions.h"
#include "mosaic_runtime.h"

#include <errno.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>

#include "claw_paths.h"
#include "mosaic_camera.h"
#if defined(ESP_PLATFORM)
#include "mosaic_loader.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_timer.h"
#include "nvs.h"
#endif

#define CAMERA_PHOTO_DIR "photos"
#define CAMERA_PHOTO_PATH_MAX 256U
#define CAMERA_NVS_NAMESPACE "mosaic_camera"
#define CAMERA_NVS_FLASH_MODE_KEY "flash_mode"

extern const mosaic_app_descriptor_t mosaic_camera_app;

typedef enum {
    CAMERA_FLASH_OFF = 0,
    CAMERA_FLASH_AUTO,
    CAMERA_FLASH_ON,
    CAMERA_FLASH_MODE_COUNT,
} camera_flash_mode_t;

static camera_flash_mode_t s_flash_mode;
static camera_vision_mode_t s_recognition_mode;
static bool s_recognition_menu_open;
static void *s_remote_capture_timer;
static void camera_capture_photo(void);
static esp_err_t camera_capture_photo_to(char *saved, size_t saved_size);
#if defined(ESP_PLATFORM)
static portMUX_TYPE s_remote_lock = portMUX_INITIALIZER_UNLOCKED;
static bool s_remote_capture_pending;
static char s_remote_path[CAMERA_PHOTO_PATH_MAX];
static esp_err_t s_remote_error;

/* Called off the UI task. Never return success before JPEG encoding and fclose. */
esp_err_t mosaic_camera_app_take_photo(char *saved, size_t saved_size)
{
    portENTER_CRITICAL(&s_remote_lock);
    s_remote_capture_pending = true;
    s_remote_path[0] = '\0';
    s_remote_error = ESP_OK;
    portEXIT_CRITICAL(&s_remote_lock);
    esp_err_t result = mosaic_loader_request(&mosaic_camera_app);
    int64_t deadline = esp_timer_get_time() + 6000000;
    while (result == ESP_OK && esp_timer_get_time() < deadline) {
        char path[CAMERA_PHOTO_PATH_MAX];
        portENTER_CRITICAL(&s_remote_lock);
        snprintf(path, sizeof(path), "%s", s_remote_path);
        result = s_remote_error;
        portEXIT_CRITICAL(&s_remote_lock);
        if (result != ESP_OK) break;
        if (path[0] != '\0') {
            result = mosaic_camera_photo_result(path);
            if (result == ESP_OK) {
                snprintf(saved, saved_size, "%s", strrchr(path, '/') + 1);
                return ESP_OK;
            }
            if (result != ESP_ERR_NOT_FINISHED) break;
            result = ESP_OK;
        }
        vTaskDelay(pdMS_TO_TICKS(50));
    }
    portENTER_CRITICAL(&s_remote_lock);
    s_remote_capture_pending = false;
    portEXIT_CRITICAL(&s_remote_lock);
    return result == ESP_OK ? ESP_ERR_TIMEOUT : result;
}
#endif

static void camera_load_flash_mode(void)
{
    s_flash_mode = CAMERA_FLASH_OFF;
#if defined(ESP_PLATFORM)
    nvs_handle_t nvs;
    esp_err_t err = nvs_open(CAMERA_NVS_NAMESPACE, NVS_READONLY, &nvs);
    if (err == ESP_ERR_NVS_NOT_FOUND) {
        return;
    }
    if (err != ESP_OK) {
        printf("mosaic_camera: open flash settings failed err=%d\n", (int)err);
        return;
    }

    uint8_t value = CAMERA_FLASH_OFF;
    err = nvs_get_u8(nvs, CAMERA_NVS_FLASH_MODE_KEY, &value);
    nvs_close(nvs);
    if (err == ESP_ERR_NVS_NOT_FOUND) {
        return;
    }
    if (err != ESP_OK || value >= CAMERA_FLASH_MODE_COUNT) {
        printf("mosaic_camera: load flash mode failed err=%d value=%u\n", (int)err, (unsigned)value);
        return;
    }
    s_flash_mode = (camera_flash_mode_t)value;
#endif
}

static esp_err_t camera_save_flash_mode(void)
{
#if defined(ESP_PLATFORM)
    nvs_handle_t nvs;
    esp_err_t err = nvs_open(CAMERA_NVS_NAMESPACE, NVS_READWRITE, &nvs);
    if (err != ESP_OK) {
        return err;
    }
    err = nvs_set_u8(nvs, CAMERA_NVS_FLASH_MODE_KEY, (uint8_t)s_flash_mode);
    if (err == ESP_OK) {
        err = nvs_commit(nvs);
    }
    nvs_close(nvs);
    return err;
#else
    return ESP_OK;
#endif
}

static void camera_render_flash_mode(esp_gsp_handle_t ui)
{
    if (ui == NULL) {
        return;
    }
    (void)esp_gsp_set_visible(ui, GSP_BIND_CAMERA_FLASH_OFF_VISIBLE, s_flash_mode == CAMERA_FLASH_OFF);
    (void)esp_gsp_set_visible(ui, GSP_BIND_CAMERA_FLASH_AUTO_VISIBLE, s_flash_mode == CAMERA_FLASH_AUTO);
    (void)esp_gsp_set_visible(ui, GSP_BIND_CAMERA_FLASH_ON_VISIBLE, s_flash_mode == CAMERA_FLASH_ON);
}

static void camera_render_recognition(esp_gsp_handle_t ui)
{
    if (ui == NULL) {
        return;
    }
    (void)esp_gsp_set_visible(ui, GSP_BIND_CAMERA_RECOGNITION_MENU_VISIBLE, s_recognition_menu_open);
    (void)esp_gsp_set_visible(ui, GSP_BIND_CAMERA_RECOGNITION_ACTIVE_VISIBLE, s_recognition_mode != CAMERA_VISION_MODE_OFF);
    (void)esp_gsp_set_visible(ui, GSP_BIND_CAMERA_QRCODE_SELECTED_VISIBLE, s_recognition_mode == CAMERA_VISION_MODE_QRCODE);
    (void)esp_gsp_set_visible(ui, GSP_BIND_CAMERA_COLOR_SELECTED_VISIBLE, s_recognition_mode == CAMERA_VISION_MODE_COLOR);
    (void)esp_gsp_set_visible(ui, GSP_BIND_CAMERA_MOTION_SELECTED_VISIBLE, s_recognition_mode == CAMERA_VISION_MODE_MOTION);
    (void)esp_gsp_set_text(ui, GSP_BIND_CAMERA_RECOGNITION_STATUS,
                           s_recognition_mode == CAMERA_VISION_MODE_QRCODE ? "QR scanning" :
                           s_recognition_mode == CAMERA_VISION_MODE_COLOR ? "Color scanning" :
                           s_recognition_mode == CAMERA_VISION_MODE_MOTION ? "Motion scanning" : "");
    if (s_recognition_mode == CAMERA_VISION_MODE_OFF) {
        (void)esp_gsp_set_visible(ui, GSP_BIND_CAMERA_RECOGNITION_RESULT_VISIBLE, false);
    }
}

static void camera_select_recognition_mode(esp_gsp_handle_t ui, camera_vision_mode_t selected)
{
    const camera_vision_mode_t next = s_recognition_mode == selected ? CAMERA_VISION_MODE_OFF : selected;
    const esp_err_t err = mosaic_camera_set_recognition_mode(next);
    if (err != ESP_OK) {
        printf("mosaic_camera: switch recognition mode failed err=%d\n", (int)err);
        return;
    }
    s_recognition_mode = next;
    s_recognition_menu_open = false;
    (void)esp_gsp_set_visible(ui, GSP_BIND_CAMERA_RECOGNITION_RESULT_VISIBLE, false);
    camera_render_recognition(ui);
}

#if defined(ESP_PLATFORM)
static void camera_remote_capture_tick(esp_gsp_handle_t ui, void *user_ctx)
{
    (void)user_ctx;
    (void)ui;
    portENTER_CRITICAL(&s_remote_lock);
    bool pending = s_remote_capture_pending;
    s_remote_capture_pending = false;
    portEXIT_CRITICAL(&s_remote_lock);
    if (!pending) return;
    char path[CAMERA_PHOTO_PATH_MAX] = {0};
    esp_err_t err = camera_capture_photo_to(path, sizeof(path));
    portENTER_CRITICAL(&s_remote_lock);
    snprintf(s_remote_path, sizeof(s_remote_path), "%s", path);
    s_remote_error = err;
    portEXIT_CRITICAL(&s_remote_lock);
}

static void camera_start_deferred(void *user_ctx)
{
    (void)user_ctx;
    if (mosaic_loader_app() != &mosaic_camera_app) {
        return;
    }

    esp_gsp_handle_t ui = mosaic_loader_ui();
    if (ui == NULL) {
        printf("mosaic_camera: active UI unavailable\n");
        return;
    }
    const esp_err_t err = mosaic_camera_start(ui);
    if (err != ESP_OK) {
        printf("mosaic_camera: start failed err=%d\n", (int)err);
        return;
    }
    if (s_flash_mode == CAMERA_FLASH_ON) {
        const esp_err_t flash_err = mosaic_camera_set_flash_enabled(true);
        if (flash_err != ESP_OK) {
            printf("mosaic_camera: restore continuous flash failed err=%d\n", (int)flash_err);
        }
    }
    /* Keep polling while the app is open: another remote capture may arrive
     * without a new on_started callback. The first tick lets preview settle. */
    s_remote_capture_timer = esp_gsp_timer_create(ui, 1500, camera_remote_capture_tick, NULL);
}

static void camera_started(esp_gsp_handle_t ui)
{
    camera_load_flash_mode();
    s_recognition_mode = CAMERA_VISION_MODE_OFF;
    s_recognition_menu_open = false;
    (void)esp_gsp_set_visible(ui, GSP_BIND_CAMERA_ALBUM_THUMBNAIL_VISIBLE, false);
    camera_render_flash_mode(ui);
    camera_render_recognition(ui);
    const esp_err_t err = mosaic_loader_defer(camera_start_deferred, NULL);
    if (err != ESP_OK) {
        printf("mosaic_camera: defer start failed err=%d\n", (int)err);
    }
}
#else
static void camera_tick(esp_gsp_handle_t ui, void *user_ctx)
{
    (void)user_ctx;
    mosaic_camera_tick(ui);
}

static void camera_started(esp_gsp_handle_t ui)
{
    camera_load_flash_mode();
    s_recognition_mode = CAMERA_VISION_MODE_OFF;
    s_recognition_menu_open = false;
    (void)esp_gsp_set_visible(ui, GSP_BIND_CAMERA_ALBUM_THUMBNAIL_VISIBLE, false);
    camera_render_flash_mode(ui);
    camera_render_recognition(ui);
    (void)esp_gsp_set_visible(ui, GSP_BIND_CAMERA_MISSING_VISIBLE, false);
    mosaic_camera_tick(ui);
    (void)esp_gsp_timer_create(ui, 40, camera_tick, NULL);
}
#endif

static void camera_stopping(esp_gsp_handle_t ui)
{
    s_remote_capture_timer = NULL;
    if (s_flash_mode == CAMERA_FLASH_ON) {
        const esp_err_t err = mosaic_camera_set_flash_enabled(false);
        if (err != ESP_OK && err != ESP_ERR_INVALID_STATE) {
            printf("mosaic_camera: disable continuous flash failed err=%d\n", (int)err);
        }
    }
    s_recognition_mode = CAMERA_VISION_MODE_OFF;
    s_recognition_menu_open = false;
    mosaic_camera_stop(ui);
}

static const mosaic_app_route_t s_camera_routes[] = {
    { .action_id = GSP_ACT_ID_CAMERA_ALBUM, .target_name = "album" },
};

static esp_err_t camera_capture_photo_to(char *saved, size_t saved_size)
{
    char directory[CAMERA_PHOTO_PATH_MAX] = {0};
    esp_err_t err = claw_paths_join(
        CLAW_PATH_DATA, CAMERA_PHOTO_DIR,
        directory, sizeof(directory));
    if (err != ESP_OK) {
        printf("mosaic_camera: compose photo directory failed err=%d\n",
               (int)err);
        return err;
    }
    if (mkdir(directory, 0775) != 0 && errno != EEXIST) {
        printf("mosaic_camera: create photo directory failed errno=%d\n",
               errno);
        return ESP_FAIL;
    }

    const time_t now = time(NULL);
    struct tm local_time = {0};
    if (localtime_r(&now, &local_time) == NULL) {
        printf("mosaic_camera: resolve local time failed\n");
        return ESP_FAIL;
    }

    char relative[96] = {0};
    if (strftime(relative, sizeof(relative), CAMERA_PHOTO_DIR "/camera_%Y%m%d_%H%M%S.jpg", &local_time) == 0) {
        printf("mosaic_camera: compose photo name failed\n");
        return ESP_ERR_INVALID_SIZE;
    }

    char path[CAMERA_PHOTO_PATH_MAX] = {0};
    err = claw_paths_join(
        CLAW_PATH_DATA, relative, path, sizeof(path));
    if (err == ESP_OK) {
        err = mosaic_camera_capture_photo(path, s_flash_mode == CAMERA_FLASH_AUTO);
    }
    if (err != ESP_OK) {
        printf("mosaic_camera: request photo failed err=%d\n", (int)err);
    } else {
        printf("mosaic_camera: photo request queued: %s\n", path);
    }
    if (err == ESP_OK && saved != NULL) snprintf(saved, saved_size, "%s", path);
    return err;
}

static void camera_capture_photo(void)
{
    (void)camera_capture_photo_to(NULL, 0);
}

static void camera_event(
    esp_gsp_handle_t ui, const mosaic_event_t *event)
{
    (void)ui;
    if (event == NULL || event->type != MOSAIC_EVENT_UI_CALL) {
        return;
    }
    switch (event->data.call.action_id) {
    case GSP_ACT_ID_CAMERA_ALBUM:
        break;
    case GSP_ACT_ID_CAMERA_SHUTTER:
        camera_capture_photo();
        break;
    case GSP_ACT_ID_CAMERA_FLIP: {
        bool enabled = false;
        const esp_err_t err = mosaic_camera_toggle_flip(&enabled);
        if (err != ESP_OK) {
            printf("mosaic_camera: flip failed err=%d\n", (int)err);
        }
        break;
    }
    case GSP_ACT_ID_CAMERA_FLASH_TOGGLE: {
        if (s_flash_mode == CAMERA_FLASH_ON) {
            const esp_err_t err = mosaic_camera_set_flash_enabled(false);
            if (err != ESP_OK) {
                printf("mosaic_camera: disable continuous flash failed err=%d\n", (int)err);
            }
        }
        s_flash_mode = (camera_flash_mode_t)((s_flash_mode + 1U) % CAMERA_FLASH_MODE_COUNT);
        if (s_flash_mode == CAMERA_FLASH_ON) {
            const esp_err_t err = mosaic_camera_set_flash_enabled(true);
            if (err != ESP_OK) {
                printf("mosaic_camera: enable continuous flash failed err=%d\n", (int)err);
            }
        }
        camera_render_flash_mode(ui);
        const esp_err_t err = camera_save_flash_mode();
        if (err != ESP_OK) {
            printf("mosaic_camera: save flash mode failed err=%d\n", (int)err);
        }
        break;
    }
    case GSP_ACT_ID_CAMERA_RECOGNITION_MENU:
        s_recognition_menu_open = !s_recognition_menu_open;
        camera_render_recognition(ui);
        break;
    case GSP_ACT_ID_CAMERA_QRCODE_MODE:
        camera_select_recognition_mode(ui, CAMERA_VISION_MODE_QRCODE);
        break;
    case GSP_ACT_ID_CAMERA_COLOR_MODE:
        camera_select_recognition_mode(ui, CAMERA_VISION_MODE_COLOR);
        break;
    case GSP_ACT_ID_CAMERA_MOTION_MODE:
        camera_select_recognition_mode(ui, CAMERA_VISION_MODE_MOTION);
        break;
    default:
        break;
    }
}

const mosaic_app_descriptor_t mosaic_camera_app = {
    .id = 1,
    .launch_action = GSP_ACT_ID_APP_CAMERA,
    .back_action = MOSAIC_APP_SHELL_BACK_ACTION,
    .routes = s_camera_routes,
    .route_count = sizeof(s_camera_routes) / sizeof(s_camera_routes[0]),
    .name = "camera",
    .title = "Camera",
    .directory = &gsp_obj_directory_camera,
    .disable_swipe = true,
    .root_header_in_stack = true,
    .on_started = camera_started,
    .on_stopping = camera_stopping,
    .on_event = camera_event,
};
