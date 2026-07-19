function(download_ncnn)
  include("${CMAKE_CURRENT_LIST_DIR}/../../../../../cmake/operit_git_source.cmake")

  set(NCNN_PIXEL OFF CACHE BOOL "" FORCE)
  set(NCNN_PIXEL_ROTATE OFF CACHE BOOL "" FORCE)
  set(NCNN_PIXEL_AFFINE OFF CACHE BOOL "" FORCE)
  set(NCNN_PIXEL_DRAWING OFF CACHE BOOL "" FORCE)
  set(NCNN_BUILD_BENCHMARK OFF CACHE BOOL "" FORCE)
  set(NCNN_C_API OFF CACHE BOOL "" FORCE)
  set(NCNN_INSTALL_SDK OFF CACHE BOOL "" FORCE)

  set(NCNN_DISABLE_EXCEPTION OFF CACHE BOOL "" FORCE)

  set(NCNN_SHARED_LIB ${BUILD_SHARED_LIBS} CACHE BOOL "" FORCE)

  set(NCNN_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
  set(NCNN_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
  set(NCNN_BUILD_TESTS OFF CACHE BOOL "" FORCE)

  set(disabled_layers
    AbsVal
    ArgMax
    BatchNorm
    Bias
    BNLL
    Deconvolution
    ELU
    Exp
    Log
    LRN
    MVN
    Pooling
    Power
    PReLU
    Proposal
    ROIPooling
    Scale
    SPP
    Threshold
    Tile
    ConvolutionDepthWise
    Normalize
    PriorBox
    DetectionOutput
    Interp
    DeconvolutionDepthWise
    ShuffleChannel
    InstanceNorm
    Clip
    Reorg
    YoloDetectionOutput
    Yolov3DetectionOutput
    PSROIPooling
    ROIAlign
    HardSigmoid
    SELU
    HardSwish
    Noop
    PixelShuffle
    DeepCopy
    Mish
    StatisticsPooling
    Swish
    GroupNorm
    Softplus
    GRU
    MultiHeadAttention
    Pooling1D
    Convolution3D
    ConvolutionDepthWise3D
    Pooling3D
    Deconvolution3D
    DeconvolutionDepthWise3D
    Einsum
    DeformableConv2D
    Fold
    Unfold
    GridSample
    CumulativeSum
    CopyTo
    Erf
    Diag
    CELU
    Shrink
    RMSNorm
    Spectrogram
    InverseSpectrogram
    RelPositionalEncoding
    MakePadMask
    RelShift
  )

  foreach(layer IN LISTS disabled_layers)
    string(TOLOWER ${layer} name)
    set(WITH_LAYER_${name} OFF CACHE BOOL "" FORCE)
  endforeach()

  operit_declare_git_source(
    ncnn
    "https://github.com/Tencent/ncnn.git"
    "master"
    PATCH_COMMAND
      ${CMAKE_COMMAND}
      -DNCNN_SOURCE_DIR=<SOURCE_DIR>
      -P
      ${CMAKE_CURRENT_LIST_DIR}/patch-ncnn.cmake
  )
  operit_populate_git_source(ncnn_SOURCE_DIR ncnn_BINARY_DIR ncnn)

  message(STATUS "ncnn source dir: ${ncnn_SOURCE_DIR}")
  message(STATUS "ncnn binary dir: ${ncnn_BINARY_DIR}")

  add_subdirectory(${ncnn_SOURCE_DIR} ${ncnn_BINARY_DIR} EXCLUDE_FROM_ALL)
  install(TARGETS ncnn DESTINATION lib)
endfunction()

download_ncnn()
