/*
 * SPDX-FileCopyrightText: 2025 Espressif Systems (Shanghai) CO., LTD
 * SPDX-License-Identifier: LicenseRef-Espressif-Modified-MIT
 *
 * Auto-generated device handle definition file
 * DO NOT MODIFY THIS FILE MANUALLY
 *
 * See LICENSE file for details.
 */

#include <stddef.h>
#include "esp_board_device.h"
#include "dev_audio_codec.h"
#include "dev_button.h"
#include "dev_camera.h"
#include "dev_custom.h"
#include "dev_display_lcd.h"
#include "dev_lcd_touch.h"
#include "dev_ledc_ctrl.h"
#include "dev_power_ctrl.h"

// Device handle array
esp_board_device_handle_t g_esp_board_device_handles[] = {
    {
        .next = &g_esp_board_device_handles[1],
        .name = "usb_hs_console",
        .chip = "none",
        .type = "custom",
        .device_handle = NULL,
        .init = dev_custom_init,
        .deinit = dev_custom_deinit
    },
    {
        .next = &g_esp_board_device_handles[2],
        .name = "lcd_power_ctrl",
        .chip = NULL,
        .type = "power_ctrl",
        .device_handle = NULL,
        .init = dev_power_ctrl_init,
        .deinit = dev_power_ctrl_deinit
    },
    {
        .next = &g_esp_board_device_handles[3],
        .name = "display_lcd",
        .chip = "co5300",
        .type = "display_lcd",
        .device_handle = NULL,
        .init = dev_display_lcd_init,
        .deinit = dev_display_lcd_deinit
    },
    {
        .next = &g_esp_board_device_handles[4],
        .name = "button_boot",
        .chip = NULL,
        .type = "button",
        .device_handle = NULL,
        .init = dev_button_init,
        .deinit = dev_button_deinit
    },
    {
        .next = &g_esp_board_device_handles[5],
        .name = "button_power",
        .chip = NULL,
        .type = "button",
        .device_handle = NULL,
        .init = dev_button_init,
        .deinit = dev_button_deinit
    },
    {
        .next = &g_esp_board_device_handles[6],
        .name = "lcd_touch",
        .chip = "cst9217",
        .type = "lcd_touch",
        .device_handle = NULL,
        .init = dev_lcd_touch_init,
        .deinit = dev_lcd_touch_deinit
    },
    {
        .next = &g_esp_board_device_handles[7],
        .name = "audio_dac",
        .chip = "es8311",
        .type = "audio_codec",
        .device_handle = NULL,
        .init = dev_audio_codec_init,
        .deinit = dev_audio_codec_deinit
    },
    {
        .next = &g_esp_board_device_handles[8],
        .name = "audio_adc",
        .chip = "es8311",
        .type = "audio_codec",
        .device_handle = NULL,
        .init = dev_audio_codec_init,
        .deinit = dev_audio_codec_deinit
    },
    {
        .next = &g_esp_board_device_handles[9],
        .name = "vibration_motor",
        .chip = NULL,
        .type = "ledc_ctrl",
        .device_handle = NULL,
        .init = dev_ledc_ctrl_init,
        .deinit = dev_ledc_ctrl_deinit
    },
    {
        .next = &g_esp_board_device_handles[10],
        .name = "nand_flash",
        .chip = NULL,
        .type = "custom",
        .device_handle = NULL,
        .init = dev_custom_init,
        .deinit = dev_custom_deinit
    },
    {
        .next = &g_esp_board_device_handles[11],
        .name = "battery_monitor",
        .chip = "bq27220",
        .type = "custom",
        .device_handle = NULL,
        .init = dev_custom_init,
        .deinit = dev_custom_deinit
    },
    {
        .next = &g_esp_board_device_handles[12],
        .name = "imu_sensor",
        .chip = "bmi270",
        .type = "custom",
        .device_handle = NULL,
        .init = dev_custom_init,
        .deinit = dev_custom_deinit
    },
    {
        .next = &g_esp_board_device_handles[13],
        .name = "magnetometer_1",
        .chip = "bmm150",
        .type = "custom",
        .device_handle = NULL,
        .init = dev_custom_init,
        .deinit = dev_custom_deinit
    },
    {
        .next = &g_esp_board_device_handles[14],
        .name = "magnetometer_2",
        .chip = "bmm150",
        .type = "custom",
        .device_handle = NULL,
        .init = dev_custom_init,
        .deinit = dev_custom_deinit
    },
    {
        .next = NULL,
        .name = "camera",
        .chip = NULL,
        .type = "camera",
        .device_handle = NULL,
        .init = dev_camera_init,
        .deinit = dev_camera_deinit
    },
};
