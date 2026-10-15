/*
 * SPDX-FileCopyrightText: 2025 Espressif Systems (Shanghai) CO., LTD
 * SPDX-License-Identifier: LicenseRef-Espressif-Modified-MIT
 *
 * Auto-generated custom device structure definitions
 * DO NOT MODIFY THIS FILE MANUALLY
 *
 * See LICENSE file for details.
 */

#pragma once

#include <stdint.h>
#include <stdbool.h>

// Custom device structure definitions
// These structures are dynamically generated based on YAML configuration

// Structure definition for usb_hs_console
typedef struct {
    const char *name;           /*!< Custom device name */
    const char *type;           /*!< Device type: "custom" */
    const char *chip;           /*!< Chip name */

} dev_custom_usb_hs_console_config_t;

// Structure definition for nand_flash
typedef struct {
    const char *name;           /*!< Custom device name */
    const char *type;           /*!< Device type: "custom" */
    const char *chip;           /*!< Chip name */
    int32_t      chip_size;
} dev_custom_nand_flash_config_t;

// Structure definition for battery_monitor
typedef struct {
    const char *name;           /*!< Custom device name */
    const char *type;           /*!< Device type: "custom" */
    const char *chip;           /*!< Chip name */
    int8_t       design_capacity;
    uint8_t     peripheral_count;
    const char *peripheral_name;
} dev_custom_battery_monitor_config_t;

// Structure definition for imu_sensor
typedef struct {
    const char *name;           /*!< Custom device name */
    const char *type;           /*!< Device type: "custom" */
    const char *chip;           /*!< Chip name */
    int8_t       addr;
    uint8_t     peripheral_count;
    const char *peripheral_name;
} dev_custom_imu_sensor_config_t;

// Structure definition for magnetometer_1
typedef struct {
    const char *name;           /*!< Custom device name */
    const char *type;           /*!< Device type: "custom" */
    const char *chip;           /*!< Chip name */
    int8_t       addr;
    uint8_t     peripheral_count;
    const char *peripheral_name;
} dev_custom_magnetometer_1_config_t;

// Structure definition for magnetometer_2
typedef struct {
    const char *name;           /*!< Custom device name */
    const char *type;           /*!< Device type: "custom" */
    const char *chip;           /*!< Chip name */
    int8_t       addr;
    uint8_t     peripheral_count;
    const char *peripheral_name;
} dev_custom_magnetometer_2_config_t;

