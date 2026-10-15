# Board information output
message(STATUS "Selected Board: esp_mosaico")
message(STATUS "MosaicoBot board directory: ${CMAKE_CURRENT_LIST_DIR}/../../boards/esp_mosaico")

idf_component_register(
    SRC_DIRS "." "../../boards/esp_mosaico"
    INCLUDE_DIRS "." "../../boards/esp_mosaico"
    REQUIRES esp_board_manager
)

# This is equivalent to adding WHOLE_ARCHIVE option to the idf_component_register call above:
idf_component_set_property(${COMPONENT_NAME} WHOLE_ARCHIVE TRUE)
