idf_component_register(
    SRCS
        "src/peer_codec.c"
        "src/peer_registry.c"
        "src/peer_pairing.c"
    INCLUDE_DIRS
        "include"
)
target_compile_options(${COMPONENT_LIB} PRIVATE -Wno-format-truncation)
