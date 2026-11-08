// 来源：k2-fsa/sherpa-onnx v1.13.2，版权归 Xiaomi Corporation，采用 Apache-2.0。
// 保留 JNI 所需的类名与字段；运行库由 PackResources 完成固定摘要校验后显式加载。
package com.k2fsa.sherpa.onnx
data class HomophoneReplacerConfig(
    var dictDir: String = "",
    var lexicon: String = "",
    var ruleFsts: String = "",
)
