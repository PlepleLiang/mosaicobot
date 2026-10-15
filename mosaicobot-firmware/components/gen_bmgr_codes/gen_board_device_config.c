/*
 * SPDX-FileCopyrightText: 2025 Espressif Systems (Shanghai) CO., LTD
 * SPDX-License-Identifier: LicenseRef-Espressif-Modified-MIT
 *
 * Auto-generated device configuration file
 * DO NOT MODIFY THIS FILE MANUALLY
 *
 * See LICENSE file for details.
 */

#include <stdlib.h>
#include "esp_board_device.h"
#include "dev_audio_codec.h"
#include "dev_button.h"
#include "dev_camera.h"
#include "dev_custom.h"
#include "dev_display_lcd.h"
#include "dev_lcd_touch.h"
#include "dev_ledc_ctrl.h"
#include "dev_power_ctrl.h"
#include "gen_board_device_custom.h"

// Device configuration structures
const static dev_custom_usb_hs_console_config_t esp_bmgr_usb_hs_console_cfg = {
    .name = "usb_hs_console",
    .type = "custom",
    .chip = "none",
};

const static dev_power_ctrl_config_t esp_bmgr_lcd_power_ctrl_cfg = {
    .name = "lcd_power_ctrl",
    .sub_type = "gpio",
    .sub_cfg = {
        .gpio = {
            .gpio_name = "gpio_lcd_en",
            .active_level = 0,
        },
    },
};

const static dev_display_lcd_config_t esp_bmgr_display_lcd_cfg = {
    .name = "display_lcd",
    .chip = "co5300",
    .sub_type = "spi",
    .lcd_width = 480,
    .lcd_height = 480,
    .swap_xy = false,
    .mirror_x = false,
    .mirror_y = false,
    .need_reset = true,
    .invert_color = false,
    .rgb_ele_order = LCD_RGB_ELEMENT_ORDER_RGB,
    .data_endian = LCD_RGB_DATA_ENDIAN_BIG,
    .bits_per_pixel = 16,
    .sub_cfg = {
        .spi = {
            .spi_name = "spi_display",
            .panel_config = {
                .reset_gpio_num = 42,
                .rgb_ele_order = LCD_RGB_ELEMENT_ORDER_RGB,
                .data_endian = LCD_RGB_DATA_ENDIAN_BIG,
                .bits_per_pixel = 16,
                .flags = {
                    .reset_active_high = false,
                },
                .vendor_config = NULL,
            },
            .io_spi_config = {
                .cs_gpio_num = 50,
                .dc_gpio_num = -1,
                .spi_mode = 0,
                .pclk_hz = 40000000,
                .trans_queue_depth = 2,
                .lcd_cmd_bits = 32,
                .lcd_param_bits = 8,
                .cs_ena_pretrans = 0,
                .cs_ena_posttrans = 0,
                .flags = {
                    .dc_high_on_cmd = false,
                    .dc_low_on_data = false,
                    .dc_low_on_param = false,
                    .octal_mode = false,
                    .quad_mode = true,
                    .sio_mode = false,
                    .lsb_first = false,
                    .cs_high_active = false,
                    .psram_dma_direct = true,
                },
            },
        },
    },
};

const static dev_button_config_t esp_bmgr_button_boot_cfg = {
    .sub_type = "gpio",
    .button_timing_cfg = {
        .long_press_time = 2000,
        .short_press_time = 100,
    },
    .events_cfg = {
        .enabled_events = {
            .press_down = 1,
            .press_up = 1,
            .single_click = 1,
            .double_click = 1,
            .multi_click = 0,
            .long_press_start = 1,
            .long_press_hold = 0,
            .long_press_up = 1,
            .press_repeat = 0,
            .press_repeat_done = 0,
            .press_end = 0,
        },
    },
    .sub_cfg = {
        .gpio = {
            .gpio_name = "gpio_button_boot",
            .active_level = 0,
            .enable_power_save = false,
            .disable_pull = false,
        },
    },
    .name = "button_boot",
};

const static dev_button_config_t esp_bmgr_button_power_cfg = {
    .sub_type = "gpio",
    .button_timing_cfg = {
        .long_press_time = 2000,
        .short_press_time = 100,
    },
    .events_cfg = {
        .enabled_events = {
            .press_down = 1,
            .press_up = 1,
            .single_click = 1,
            .double_click = 1,
            .multi_click = 0,
            .long_press_start = 1,
            .long_press_hold = 0,
            .long_press_up = 1,
            .press_repeat = 0,
            .press_repeat_done = 0,
            .press_end = 0,
        },
    },
    .sub_cfg = {
        .gpio = {
            .gpio_name = "gpio_button_power",
            .active_level = 0,
            .enable_power_save = false,
            .disable_pull = false,
        },
    },
    .name = "button_power",
};

const static dev_lcd_touch_config_t esp_bmgr_lcd_touch_cfg = {
    .name = "lcd_touch",
    .chip = "cst9217",
    .type = "lcd_touch",
    .sub_type = "i2c",
    .touch_config = {
        .x_max = 480,
        .y_max = 480,
        .rst_gpio_num = -1,
        .int_gpio_num = 6,
        .levels = {
            .reset = 0,
            .interrupt = 0,
        },
        .flags = {
            .swap_xy = false,
            .mirror_x = false,
            .mirror_y = false,
        },
        .process_coordinates = NULL,
        .interrupt_callback = NULL,
        .user_data = NULL,
        .driver_data = NULL,
    },
    .sub_cfg = {
        .i2c = {
            .i2c_name = "i2c_master",
            .i2c_addr_count = 1,
            .i2c_addr = {0xb4, 0x00, 0x00, 0x00},
            .io_i2c_config = {
                .dev_addr = 0,
                .control_phase_bytes = 1,
                .dc_bit_offset = 0,
                .lcd_cmd_bits = 8,
                .lcd_param_bits = 0,
                .scl_speed_hz = 400000,
                .flags = {
                    .dc_low_on_data = false,
                    .disable_control_phase = true,
                },
                .transaction_timeout_ms = 100,
            },
        },
    },
};

const static dev_audio_codec_config_t esp_bmgr_audio_dac_cfg = {
    .name = "audio_dac",
    .chip = "es8311",
    .type = "audio_codec",
    .data_if_type = 0,
    .adc_enabled = false,
    .adc_max_channel = 0,
    .adc_channel_mask = 0x3,
    .adc_channel_labels = "",
    .adc_init_gain = 0,
    .dac_enabled = true,
    .dac_max_channel = 1,
    .dac_channel_mask = 0x1,
    .dac_init_gain = 0,
    .pa_cfg = {
        .name = "gpio_pa_control",
        .port = 45,
        .active_level = 1,
        .gain = 6.0,
    },
    .i2c_cfg = {
        .name = "i2c_master",
        .port = 0,
        .address = 50,
        .frequency = 400000,
    },
    .i2s_cfg = {
        .name = "i2s_audio_out",
        .port = 0,
        .clk_src = 0,
        .tx_aux_out_io = -1,
        .tx_aux_out_line = 0,
        .tx_aux_out_invert = false,
    },
    .adc_cfg = {
        .periph_name = NULL,
        .sample_rate_hz = 0,
        .max_store_buf_size = 0,
        .conv_frame_size = 0,
        .conv_mode = 0,
        .format = 0,
        .pattern_num = 0,
        .cfg_mode = 0,
        .cfg = {
            .single_unit = {
                .unit_id = 0,
                .atten = 0,
                .bit_width = 0,
                .channel_id = {},
            },
        },
    },
    .metadata = NULL,
    .metadata_size = 0,
    .mclk_enabled = true,
    .aec_enabled = false,
    .eq_enabled = false,
    .alc_enabled = false,
};

const static dev_audio_codec_config_t esp_bmgr_audio_adc_cfg = {
    .name = "audio_adc",
    .chip = "es8311",
    .type = "audio_codec",
    .data_if_type = 0,
    .adc_enabled = true,
    .adc_max_channel = 1,
    .adc_channel_mask = 0x1,
    .adc_channel_labels = "RE",
    .adc_init_gain = 0,
    .dac_enabled = false,
    .dac_max_channel = 0,
    .dac_channel_mask = 0x0,
    .dac_init_gain = 0,
    .pa_cfg = {
        .name = NULL,
        .port = -1,
        .active_level = 0,
        .gain = 0.0,
    },
    .i2c_cfg = {
        .name = "i2c_master",
        .port = 0,
        .address = 50,
        .frequency = 400000,
    },
    .i2s_cfg = {
        .name = "i2s_audio_in",
        .port = 0,
        .clk_src = 0,
        .tx_aux_out_io = -1,
        .tx_aux_out_line = 0,
        .tx_aux_out_invert = false,
    },
    .adc_cfg = {
        .periph_name = NULL,
        .sample_rate_hz = 0,
        .max_store_buf_size = 0,
        .conv_frame_size = 0,
        .conv_mode = 0,
        .format = 0,
        .pattern_num = 0,
        .cfg_mode = 0,
        .cfg = {
            .single_unit = {
                .unit_id = 0,
                .atten = 0,
                .bit_width = 0,
                .channel_id = {},
            },
        },
    },
    .metadata = NULL,
    .metadata_size = 0,
    .mclk_enabled = true,
    .aec_enabled = false,
    .eq_enabled = false,
    .alc_enabled = false,
};

const static dev_ledc_ctrl_config_t esp_bmgr_vibration_motor_cfg = {
    .name = "vibration_motor",
    .type = "ledc_ctrl",
    .ledc_name = "ledc_vibration_motor",
    .default_percent = 0,
};

const static dev_custom_nand_flash_config_t esp_bmgr_nand_flash_cfg = {
    .name = "nand_flash",
    .type = "custom",
    .chip = "unknown",
    .chip_size = 1073741824,
};

const static dev_custom_battery_monitor_config_t esp_bmgr_battery_monitor_cfg = {
    .name = "battery_monitor",
    .type = "custom",
    .chip = "bq27220",
    .design_capacity = 65,
    .peripheral_count = 1,
    .peripheral_name = "i2c_master",
};

const static dev_custom_imu_sensor_config_t esp_bmgr_imu_sensor_cfg = {
    .name = "imu_sensor",
    .type = "custom",
    .chip = "bmi270",
    .addr = 104,
    .peripheral_count = 1,
    .peripheral_name = "i2c_master",
};

const static dev_custom_magnetometer_1_config_t esp_bmgr_magnetometer_1_cfg = {
    .name = "magnetometer_1",
    .type = "custom",
    .chip = "bmm150",
    .addr = 34,
    .peripheral_count = 1,
    .peripheral_name = "i2c_master",
};

const static dev_custom_magnetometer_2_config_t esp_bmgr_magnetometer_2_cfg = {
    .name = "magnetometer_2",
    .type = "custom",
    .chip = "bmm150",
    .addr = 36,
    .peripheral_count = 1,
    .peripheral_name = "i2c_master",
};

const static dev_camera_config_t esp_bmgr_camera_cfg = {
    .name = "camera",
    .type = "camera",
    .sub_type = "dvp",
    .sub_cfg = {
        .dvp = {
            .i2c_name = "i2c_master",
            .i2c_freq = 100000,
            .reset_io = 53,
            .pwdn_io = 48,
            .dvp_io = {
                .data_width = CAM_CTLR_DATA_WIDTH_8,
                .data_io = {16, 15, 33, 4, 14, 12, 18, 13, -1, -1, -1, -1, -1, -1, -1, -1},
                .vsync_io = 55,
                .de_io = 19,
                .pclk_io = 17,
                .xclk_io = -1,
            },
            .xclk_freq = 0,
        },
    },
};

// Device descriptor array

const esp_board_device_desc_t g_esp_board_devices[] = {
    {
        .next = &g_esp_board_devices[1],
        .name = "usb_hs_console",
        .chip = "none",
        .type = "custom",
        .sub_type = NULL,
        .cfg = &esp_bmgr_usb_hs_console_cfg,
        .cfg_size = sizeof(esp_bmgr_usb_hs_console_cfg),
        .init_skip = true,
        .depends_on = NULL,
        .depends_on_num = 0,
    },
    {
        .next = &g_esp_board_devices[2],
        .name = "lcd_power_ctrl",
        .chip = NULL,
        .type = "power_ctrl",
        .sub_type = "gpio",
        .cfg = &esp_bmgr_lcd_power_ctrl_cfg,
        .cfg_size = sizeof(esp_bmgr_lcd_power_ctrl_cfg),
        .init_skip = false,
        .depends_on = NULL,
        .depends_on_num = 0,
    },
    {
        .next = &g_esp_board_devices[3],
        .name = "display_lcd",
        .chip = "co5300",
        .type = "display_lcd",
        .sub_type = "spi",
        .cfg = &esp_bmgr_display_lcd_cfg,
        .cfg_size = sizeof(esp_bmgr_display_lcd_cfg),
        .init_skip = false,
        .depends_on = NULL,
        .depends_on_num = 0,
    },
    {
        .next = &g_esp_board_devices[4],
        .name = "button_boot",
        .chip = NULL,
        .type = "button",
        .sub_type = "gpio",
        .cfg = &esp_bmgr_button_boot_cfg,
        .cfg_size = sizeof(esp_bmgr_button_boot_cfg),
        .init_skip = true,
        .depends_on = NULL,
        .depends_on_num = 0,
    },
    {
        .next = &g_esp_board_devices[5],
        .name = "button_power",
        .chip = NULL,
        .type = "button",
        .sub_type = "gpio",
        .cfg = &esp_bmgr_button_power_cfg,
        .cfg_size = sizeof(esp_bmgr_button_power_cfg),
        .init_skip = true,
        .depends_on = NULL,
        .depends_on_num = 0,
    },
    {
        .next = &g_esp_board_devices[6],
        .name = "lcd_touch",
        .chip = "cst9217",
        .type = "lcd_touch",
        .sub_type = "i2c",
        .cfg = &esp_bmgr_lcd_touch_cfg,
        .cfg_size = sizeof(esp_bmgr_lcd_touch_cfg),
        .init_skip = false,
        .depends_on = NULL,
        .depends_on_num = 0,
    },
    {
        .next = &g_esp_board_devices[7],
        .name = "audio_dac",
        .chip = "es8311",
        .type = "audio_codec",
        .sub_type = NULL,
        .cfg = &esp_bmgr_audio_dac_cfg,
        .cfg_size = sizeof(esp_bmgr_audio_dac_cfg),
        .init_skip = true,
        .depends_on = NULL,
        .depends_on_num = 0,
    },
    {
        .next = &g_esp_board_devices[8],
        .name = "audio_adc",
        .chip = "es8311",
        .type = "audio_codec",
        .sub_type = NULL,
        .cfg = &esp_bmgr_audio_adc_cfg,
        .cfg_size = sizeof(esp_bmgr_audio_adc_cfg),
        .init_skip = true,
        .depends_on = NULL,
        .depends_on_num = 0,
    },
    {
        .next = &g_esp_board_devices[9],
        .name = "vibration_motor",
        .chip = NULL,
        .type = "ledc_ctrl",
        .sub_type = NULL,
        .cfg = &esp_bmgr_vibration_motor_cfg,
        .cfg_size = sizeof(esp_bmgr_vibration_motor_cfg),
        .init_skip = true,
        .depends_on = NULL,
        .depends_on_num = 0,
    },
    {
        .next = &g_esp_board_devices[10],
        .name = "nand_flash",
        .chip = NULL,
        .type = "custom",
        .sub_type = NULL,
        .cfg = &esp_bmgr_nand_flash_cfg,
        .cfg_size = sizeof(esp_bmgr_nand_flash_cfg),
        .init_skip = false,
        .depends_on = NULL,
        .depends_on_num = 0,
    },
    {
        .next = &g_esp_board_devices[11],
        .name = "battery_monitor",
        .chip = "bq27220",
        .type = "custom",
        .sub_type = NULL,
        .cfg = &esp_bmgr_battery_monitor_cfg,
        .cfg_size = sizeof(esp_bmgr_battery_monitor_cfg),
        .init_skip = true,
        .depends_on = NULL,
        .depends_on_num = 0,
    },
    {
        .next = &g_esp_board_devices[12],
        .name = "imu_sensor",
        .chip = "bmi270",
        .type = "custom",
        .sub_type = NULL,
        .cfg = &esp_bmgr_imu_sensor_cfg,
        .cfg_size = sizeof(esp_bmgr_imu_sensor_cfg),
        .init_skip = true,
        .depends_on = NULL,
        .depends_on_num = 0,
    },
    {
        .next = &g_esp_board_devices[13],
        .name = "magnetometer_1",
        .chip = "bmm150",
        .type = "custom",
        .sub_type = NULL,
        .cfg = &esp_bmgr_magnetometer_1_cfg,
        .cfg_size = sizeof(esp_bmgr_magnetometer_1_cfg),
        .init_skip = true,
        .depends_on = NULL,
        .depends_on_num = 0,
    },
    {
        .next = &g_esp_board_devices[14],
        .name = "magnetometer_2",
        .chip = "bmm150",
        .type = "custom",
        .sub_type = NULL,
        .cfg = &esp_bmgr_magnetometer_2_cfg,
        .cfg_size = sizeof(esp_bmgr_magnetometer_2_cfg),
        .init_skip = true,
        .depends_on = NULL,
        .depends_on_num = 0,
    },
    {
        .next = NULL,
        .name = "camera",
        .chip = NULL,
        .type = "camera",
        .sub_type = "dvp",
        .cfg = &esp_bmgr_camera_cfg,
        .cfg_size = sizeof(esp_bmgr_camera_cfg),
        .init_skip = true,
        .depends_on = NULL,
        .depends_on_num = 0,
    },
};
