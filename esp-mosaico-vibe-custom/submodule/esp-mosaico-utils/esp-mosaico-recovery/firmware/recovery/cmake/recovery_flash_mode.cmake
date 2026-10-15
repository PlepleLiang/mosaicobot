# Only the product ROM target opts out of a pre-connection reset. Preserve
# IDF's post-flash/security policy and all remaining arguments.
function(mosaico_recovery_flash_args output already_in_rom)
    set(result "${ARGN}")
    if(already_in_rom)
        list(FILTER result EXCLUDE REGEX "^--before=")
        list(PREPEND result "--before=no-reset")
    endif()
    set(${output} "${result}" PARENT_SCOPE)
endfunction()
