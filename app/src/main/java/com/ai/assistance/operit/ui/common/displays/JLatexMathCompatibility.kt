package com.ai.assistance.operit.ui.common.displays

import com.ai.assistance.operit.util.logging.AppLogger
import org.scilab.forge.jlatexmath.Atom
import org.scilab.forge.jlatexmath.ColorAtom
import org.scilab.forge.jlatexmath.EmptyAtom
import org.scilab.forge.jlatexmath.MacroInfo
import org.scilab.forge.jlatexmath.RowAtom
import org.scilab.forge.jlatexmath.SpaceAtom
import org.scilab.forge.jlatexmath.SymbolAtom
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.TeXParser
import org.scilab.forge.jlatexmath.TypedAtom

private fun advanceTeXParserPosition(parser: TeXParser, consumedLength: Int) {
    runCatching {
        val positionField = TeXParser::class.java.getDeclaredField("pos").apply {
            isAccessible = true
        }
        positionField.setInt(parser, parser.getPos() + consumedLength)
    }.onFailure {
        AppLogger.w("JLatexMathCompatibility", "Failed to advance TeXParser after color macro", it)
    }
}

@Suppress("UNUSED_PARAMETER")
internal class JLatexMathCompatMacros {
    fun color_macro(tp: TeXParser, args: Array<String>): Atom? {
        val color = ColorAtom.getColor(args[1])
        val remaining = tp.getStringFromCurrentPos()
        val nextContentIndex = remaining.indexOfFirst { !it.isWhitespace() }

        if (nextContentIndex == -1) {
            return null
        }

        return if (remaining[nextContentIndex] == '{') {
            ColorAtom(tp.getArgument(), null, color)
        } else {
            val atom = TeXFormula(remaining).root ?: EmptyAtom()
            advanceTeXParserPosition(tp, remaining.length)
            ColorAtom(atom, null, color)
        }
    }

    fun oiint_macro(tp: TeXParser, args: Array<String>): Atom {
        return buildClosedIntegralAtom(extraIntegrals = 1)
    }

    fun oiiint_macro(tp: TeXParser, args: Array<String>): Atom {
        return buildClosedIntegralAtom(extraIntegrals = 2)
    }

    private fun buildClosedIntegralAtom(extraIntegrals: Int): Atom {
        val contourIntegral = SymbolAtom.get("oint").clone().apply {
            type_limits = TeXConstants.SCRIPT_NOLIMITS
        }
        val openIntegral = SymbolAtom.get("int").clone().apply {
            type_limits = TeXConstants.SCRIPT_NOLIMITS
        }

        val row = RowAtom(contourIntegral)
        repeat(extraIntegrals) {
            row.add(SpaceAtom(TeXConstants.UNIT_MU, -6f, 0f, 0f))
            row.add(openIntegral.clone())
        }
        row.lookAtLastAtom = true

        return TypedAtom(
            TeXConstants.TYPE_BIG_OPERATOR,
            TeXConstants.TYPE_BIG_OPERATOR,
            row
        )
    }
}

internal object JLatexMathCompatibility {
    private const val TAG = "JLatexMathCompatibility"

    @Volatile
    private var registered = false

    fun ensureRegistered() {
        if (registered) return

        synchronized(this) {
            if (registered) return

            registerCommandIfMissing("color", "color_macro")
            registerCommandIfMissing("oiint", "oiint_macro")
            registerCommandIfMissing("oiiint", "oiiint_macro")
            registerUnicodeFormulaIfMissing('\u222F', "\\oiint")
            registerUnicodeFormulaIfMissing('\u2230', "\\oiiint")

            registered = true
            AppLogger.d(TAG, "Registered LaTeX compatibility macros")
        }
    }

    private fun registerCommandIfMissing(commandName: String, methodName: String) {
        if (MacroInfo.Commands.containsKey(commandName)) return

        MacroInfo.Commands[commandName] =
            MacroInfo(
                JLatexMathCompatMacros::class.java.name,
                methodName,
                0f
            )
    }

    private fun registerUnicodeFormulaIfMissing(char: Char, formula: String) {
        if (TeXFormula.symbolFormulaMappings[char.code] != null) return
        TeXFormula.symbolFormulaMappings[char.code] = formula
    }
}
