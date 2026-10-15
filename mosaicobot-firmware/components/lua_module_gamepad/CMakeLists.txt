idf_component_register(SRCS "src/lua_module_gamepad.c" INCLUDE_DIRS "src"
    REQUIRES cap_lua freertos esp_timer esp_hw_support esp_wifi espressif__cjson)
