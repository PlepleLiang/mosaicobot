idf_component_register(
    SRC_DIRS "." "platform"
    INCLUDE_DIRS "." "platform"
    PRIV_REQUIRES
        lua_module_gamepad
        peer_agent
        app_settings_service
        app_system_config
        audio_hub
        claw_hw_registry
        claw_event_router
        cmd_wifi
        esp_app_format
        esp_hw_support
        esp_lcd_touch_mux
        esp_wifi
        espressif__cjson
        espressif__bq27220
        espressif2022__bmi270
        gen_bmgr_codes
        mosaico_camera_service
        mosaico_bsp_bmgr_bridge
        mosaico_boot_splash
        nvs_flash
        weather_service
        update_check_service
        gmf_core
)
if(MOSAICOBOT_RETAINED_VIBE)
    # Profile variables are not available during IDF early requirements expansion.
    # These components are already explicitly included by the selected profile.
    idf_component_optional_requires(PRIVATE esp_mosaico_app_recovery esp_iris esp_mosaico_gsp_iris)
    target_compile_definitions(${COMPONENT_LIB} PRIVATE MOSAICOBOT_RETAINED_VIBE=1)
endif()
