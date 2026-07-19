if(NOT DEFINED NCNN_SOURCE_DIR)
  message(FATAL_ERROR "NCNN_SOURCE_DIR is not set")
endif()

set(_ncnn_src_cmake "${NCNN_SOURCE_DIR}/src/CMakeLists.txt")

if(EXISTS "${_ncnn_src_cmake}")
  file(READ "${_ncnn_src_cmake}" _ncnn_src_content)
  string(REGEX REPLACE "[^\n]*set_target_properties\\(ncnn PROPERTIES VERSION[^\n]*\n?" "" _ncnn_src_content "${_ncnn_src_content}")
  file(WRITE "${_ncnn_src_cmake}" "${_ncnn_src_content}")
endif()
