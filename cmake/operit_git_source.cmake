include_guard(GLOBAL)

include(FetchContent)

function(operit_normalize_source_token out_var token)
    string(TOLOWER "${token}" normalized_token)
    string(REGEX REPLACE "[^a-z0-9_.-]" "_" normalized_token "${normalized_token}")
    set(${out_var} "${normalized_token}" PARENT_SCOPE)
endfunction()

function(operit_git_ref_var out_var dependency_name git_ref)
    string(TOUPPER "${dependency_name}" dependency_key)
    string(REPLACE "-" "_" dependency_key "${dependency_key}")

    set(ref_var "OPERIT_${dependency_key}_GIT_REF")
    if(NOT DEFINED ${ref_var})
        set(${ref_var} "${git_ref}" CACHE STRING "Git ref used to fetch ${dependency_name}")
    endif()

    set(${out_var} "${ref_var}" PARENT_SCOPE)
endfunction()

function(operit_resolve_git_ref out_var repository git_ref)
    string(LENGTH "${git_ref}" git_ref_length)
    if(git_ref_length EQUAL 40 AND "${git_ref}" MATCHES "^[0-9a-fA-F]+$")
        string(TOLOWER "${git_ref}" resolved_sha)
        set(${out_var} "${resolved_sha}" PARENT_SCOPE)
        return()
    endif()

    execute_process(
        COMMAND git ls-remote "${repository}" "${git_ref}" "refs/heads/${git_ref}" "refs/tags/${git_ref}"
        OUTPUT_VARIABLE remote_refs
        ERROR_VARIABLE remote_error
        RESULT_VARIABLE remote_result
        OUTPUT_STRIP_TRAILING_WHITESPACE
        ERROR_STRIP_TRAILING_WHITESPACE
    )

    if(NOT remote_result EQUAL 0 OR "${remote_refs}" STREQUAL "")
        message(FATAL_ERROR "Unable to resolve ${repository} ref '${git_ref}': ${remote_error}")
    endif()

    string(REGEX MATCH "^[0-9a-fA-F]+" resolved_sha "${remote_refs}")
    if("${resolved_sha}" STREQUAL "")
        message(FATAL_ERROR "Unable to parse ${repository} ref '${git_ref}' from: ${remote_refs}")
    endif()

    string(TOLOWER "${resolved_sha}" resolved_sha)
    set(${out_var} "${resolved_sha}" PARENT_SCOPE)
endfunction()

function(operit_github_archive_url out_var repository resolved_sha)
    set(repository_without_suffix "${repository}")
    string(REGEX REPLACE "\\.git$" "" repository_without_suffix "${repository_without_suffix}")
    string(REGEX REPLACE "^https://github.com/([^/]+)/(.+)$" "https://github.com/\\1/\\2/archive/${resolved_sha}.tar.gz" archive_url "${repository_without_suffix}")

    if("${archive_url}" STREQUAL "${repository_without_suffix}")
        message(FATAL_ERROR "Only GitHub archive sources are supported by operit_github_archive_url: ${repository}")
    endif()

    set(${out_var} "${archive_url}" PARENT_SCOPE)
endfunction()

function(operit_declare_git_source dependency_name repository git_ref)
    operit_git_ref_var(ref_var "${dependency_name}" "${git_ref}")
    operit_resolve_git_ref(resolved_sha "${repository}" "${${ref_var}}")
    operit_github_archive_url(archive_url "${repository}" "${resolved_sha}")
    operit_normalize_source_token(source_token "${dependency_name}-${resolved_sha}")

    set(deps_root "${CMAKE_SOURCE_DIR}/.cxx/operit_deps")
    set(source_dir "${deps_root}/${source_token}-src")
    set(binary_dir "${deps_root}/${source_token}-build")

    FetchContent_Declare(
        ${dependency_name}
        URL "${archive_url}"
        SOURCE_DIR "${source_dir}"
        BINARY_DIR "${binary_dir}"
        ${ARGN}
    )
endfunction()

function(operit_populate_git_source out_source_dir out_binary_dir dependency_name)
    string(TOLOWER "${dependency_name}" dependency_var_prefix)

    FetchContent_GetProperties(${dependency_name})
    if(NOT ${dependency_var_prefix}_POPULATED)
        FetchContent_Populate(${dependency_name})
    endif()

    set(${out_source_dir} "${${dependency_var_prefix}_SOURCE_DIR}" PARENT_SCOPE)
    set(${out_binary_dir} "${${dependency_var_prefix}_BINARY_DIR}" PARENT_SCOPE)
endfunction()

function(operit_prepare_git_source out_source_dir out_binary_dir dependency_name repository git_ref)
    # =======================================================================
    # [PATCH 0016] 离线复用本地已存在的源码目录，跳过 FetchContent 下载。
    #
    # 背景：operit_declare_git_source() 用的是 URL 方式。CMake 的
    #       FetchContent_Populate 只要 subbuild 的 stamp 不完整就会重新
    #       走 download/extract 流程 —— 哪怕 SOURCE_DIR 里已经有完整源码。
    #       在无外网（或 GitHub 极慢）的构建机上，这会卡在
    #       Downloading .../archive/<sha>.tar.gz 上十几分钟不返回。
    #
    # 语义：只要 .cxx/operit_deps/<dep>-<sha>-src 目录存在且非空，
    #       就认定源码已就位，直接使用，不做任何网络访问。
    #       删除该目录即可强制重新下载。
    #
    # 两个必须绕开的联网点：
    #   1. FetchContent_Populate 的 download/extract
    #   2. git_ref 不是 40 位 SHA 时，operit_resolve_git_ref() 会跑
    #      `git ls-remote` 去查 SHA —— ufbx 用的是 "main" 分支，
    #      每次都必然联网。离线时改为从已有目录名里反推 SHA。
    #
    # 判据不用 CMakeLists.txt：ufbx 这类纯头文件库没有 CMakeLists.txt，
    # 用它会误判为缺失而重新联网。
    # =======================================================================
    operit_normalize_source_token(_off_token "${dependency_name}-${git_ref}")
    set(_off_deps_root "${CMAKE_SOURCE_DIR}/.cxx/operit_deps")
    set(_off_src "${_off_deps_root}/${_off_token}-src")
    set(_off_bin "${_off_deps_root}/${_off_token}-build")

    file(GLOB _off_probe "${_off_src}/*")
    if(IS_DIRECTORY "${_off_src}" AND _off_probe)
        message(WARNING "[operit] Reusing local source for ${dependency_name}: ${_off_src}")
        set(${out_source_dir} "${_off_src}" PARENT_SCOPE)
        set(${out_binary_dir} "${_off_bin}" PARENT_SCOPE)
        return()
    endif()

    # 精确目录不存在时，按 "<dep>-<40位hex>-src" 模式回退匹配。
    # 这样即使调用方传的是分支名（git_ref 无法离线解析），
    # 只要之前下载过任意版本，就能直接复用。
    #
    # [REVIEW P2] 但如果调用方钉的是完整 SHA，就绝不允许回退到别的版本——
    # 那会让构建静默用上另一个 llama.cpp，而且没有任何提示。
    string(LENGTH "${git_ref}" _off_ref_len)
    if(_off_ref_len EQUAL 40 AND "${git_ref}" MATCHES "^[0-9a-fA-F]+$")
        message(STATUS "[operit] ${dependency_name}: pinned to ${git_ref}, no fuzzy fallback")
        operit_declare_git_source("${dependency_name}" "${repository}" "${git_ref}" ${ARGN})
        operit_populate_git_source(${out_source_dir} ${out_binary_dir} "${dependency_name}")
        set(${out_source_dir} "${${out_source_dir}}" PARENT_SCOPE)
        set(${out_binary_dir} "${${out_binary_dir}}" PARENT_SCOPE)
        return()
    endif()

    operit_normalize_source_token(_off_prefix "${dependency_name}")
    file(GLOB _off_candidates "${_off_deps_root}/${_off_prefix}-*-src")
    list(LENGTH _off_candidates _off_candidate_count)
    if(_off_candidate_count GREATER 1)
        # 多个版本共存时无法判断该用哪个，宁可失败也不猜。
        string(REPLACE ";" "\n    " _off_candidate_list "${_off_candidates}")
        message(FATAL_ERROR
            "[operit] ${dependency_name}: ${_off_candidate_count} local checkouts found while "
            "resolving ref '${git_ref}' offline, refusing to pick one:\n    ${_off_candidate_list}\n"
            "  Pin an exact 40-char SHA, or remove the ones you do not need.")
    endif()
    if(_off_candidates)
        list(GET _off_candidates 0 _off_src2)
        file(GLOB _off_probe2 "${_off_src2}/*")
        if(_off_probe2)
            get_filename_component(_off_token2 "${_off_src2}" NAME)
            string(REPLACE "-src" "" _off_token2 "${_off_token2}")
            set(_off_bin "${_off_deps_root}/${_off_token2}-build")
            message(WARNING "[operit] Reusing local source for ${dependency_name} (ref '${git_ref}' unresolved offline): ${_off_src2}")
            set(${out_source_dir} "${_off_src2}" PARENT_SCOPE)
            set(${out_binary_dir} "${_off_bin}" PARENT_SCOPE)
            return()
        endif()
    endif()

    operit_declare_git_source("${dependency_name}" "${repository}" "${git_ref}" ${ARGN})
    operit_populate_git_source(${out_source_dir} ${out_binary_dir} "${dependency_name}")
    set(${out_source_dir} "${${out_source_dir}}" PARENT_SCOPE)
    set(${out_binary_dir} "${${out_binary_dir}}" PARENT_SCOPE)
endfunction()
