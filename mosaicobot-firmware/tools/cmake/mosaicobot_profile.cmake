# Optional retained-Vibe profile; normal Claw configuration remains unchanged.
set(MOSAICOBOT_RETAINED_VIBE ON)
get_filename_component(MOSAICO_UTILS_ROOT
    "${CMAKE_CURRENT_LIST_DIR}/../../../esp-mosaico-vibe-custom/submodule/esp-mosaico-utils" ABSOLUTE)
set(MOSAICO_IDF_PROJECT_CMAKE
    "${MOSAICO_UTILS_ROOT}/esp-mosaico-recovery/cmake/mosaico_idf_project.cmake")
if(NOT EXISTS "${MOSAICO_IDF_PROJECT_CMAKE}")
    message(FATAL_ERROR "MosaicoBot requires the adjacent esp-mosaico-vibe-custom utilities checkout")
endif()
if(CMAKE_BINARY_DIR STREQUAL "${CMAKE_SOURCE_DIR}/build")
    message(FATAL_ERROR "Use a separate build directory for the MosaicoBot profile")
endif()
set(SDKCONFIG "${CMAKE_BINARY_DIR}/sdkconfig" CACHE FILEPATH "Profile configuration" FORCE)
set(SDKCONFIG_DEFAULTS
    "${CMAKE_SOURCE_DIR}/sdkconfig.defaults"
    "${CMAKE_SOURCE_DIR}/components/gen_bmgr_codes/board_manager.defaults"
    "${CMAKE_SOURCE_DIR}/sdkconfig.mosaicobot.defaults")
set(EDGE_AGENT_PARTITION_TABLE "partitions_mosaicobot.csv")
list(APPEND EXTRA_COMPONENT_DIRS
    "${MOSAICO_UTILS_ROOT}/ESP-Iris/components/esp_iris"
    "${MOSAICO_UTILS_ROOT}/mosaico-tools/components/esp_mosaico_gsp_iris"
    "${MOSAICO_UTILS_ROOT}/esp-mosaico-recovery/components/esp_mosaico_app_recovery")

function(esp_mosaico_build_profile_package)
    set_property(GLOBAL PROPERTY MOSAICO_SYSTEM_UPDATE_UI_APPS_IMAGE
        "${CMAKE_BINARY_DIR}/mmap_build/ui_apps/ui_apps/ui_apps.bin")
    set_property(GLOBAL PROPERTY MOSAICO_SYSTEM_UPDATE_UI_APPS_TARGET assets_ui_apps_bin)
    set_property(GLOBAL PROPERTY MOSAICO_SYSTEM_UPDATE_DATA_LABELS system)
    set_property(GLOBAL PROPERTY MOSAICO_SYSTEM_UPDATE_DATA_system_IMAGE "${CMAKE_BINARY_DIR}/system.bin")
    set_property(GLOBAL PROPERTY MOSAICO_SYSTEM_UPDATE_DATA_system_TARGET littlefs_system_bin)
    include("${MOSAICO_UTILS_ROOT}/mosaico-tools/cmake/system_update.cmake")
    add_custom_target(mosaicobot-package ALL DEPENDS system-update-bundle)
endfunction()
