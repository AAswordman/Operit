package com.ai.assistance.operit.ui.common.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** 保留全部 Filled 名称的动态查找，以路径数据替代数千个反射类。 */
internal object MaterialFilledIconCatalog {
    fun resolve(name: String): ImageVector? {
        val data = MaterialFilledIconData.entries[name] ?: return null
        val vector = ImageVector.Builder(
            name = "Filled.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = data.substringBefore('\t') == "1",
        )
        data.substringAfter('\t').split('|').forEach { path ->
            vector.addPath(
                pathData = PathParser().parsePathString(path.substringAfter(':')).toNodes(),
                fill = SolidColor(Color.Black),
                pathFillType = if (path.startsWith("E:")) PathFillType.EvenOdd else PathFillType.NonZero,
            )
        }
        return vector.build()
    }
}