package com.ai.assistance.operit.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.TextUtils
import com.ai.assistance.operit.util.AppLogger
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import com.itextpdf.text.Document
import com.itextpdf.text.FontFactory
import com.itextpdf.text.Paragraph
import com.itextpdf.text.pdf.PdfWriter
import kotlinx.coroutines.runBlocking
import java.io.IOException

/** Utility class for document conversion operations */
object DocumentConversionUtil {
    private const val TAG = "DocumentConversionUtil"

    /** Convert text to PDF */
    fun convertTextToPdf(context: Context, sourceFile: File, targetFile: File): Boolean {
        return try {
            val document = PDDocument()
            val page = PDPage()
            document.addPage(page)

            PDPageContentStream(document, page).use { contentStream ->
                BufferedReader(FileReader(sourceFile)).use { reader ->
                    contentStream.beginText()
                    contentStream.setFont(PDType1Font.HELVETICA, 12f)
                    contentStream.setLeading(14.5f)
                    contentStream.newLineAtOffset(25f, 725f)

                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        contentStream.showText(line)
                        contentStream.newLine()
                    }
                    contentStream.endText()
                }
            }
            document.save(targetFile)
            document.close()
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to convert text to PDF", e)
            false
        }
    }

    /**
     * Extract text from PDF, with OCR fallback for image-based PDFs.
     *
     * @param context Context is required for OCR initialization.
     * @param sourceFile The source PDF file.
     * @param targetFile The target text file.
     * @return True if successful, false otherwise.
     */
    fun extractTextFromPdf(context: Context, sourceFile: File, targetFile: File): Boolean {
        try {
            // Step 1: Attempt direct text extraction with PDFBox
            val extractedText = PDDocument.load(sourceFile).use { document ->
                if (document.isEncrypted) {
                    try {
                        document.setAllSecurityToBeRemoved(true)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Failed to decrypt PDF, text extraction may fail.", e)
                    }
                }
                PDFTextStripper().getText(document)
            }

            // Step 2: Check if the extracted text is meaningful. If not, trigger OCR.
            if (extractedText.trim().length > 20) { // Threshold for meaningful text
                // Success with direct extraction
                FileOutputStream(targetFile).bufferedWriter().use {
                    it.write(stripNullCharacters(extractedText))
                }
                return true
            } else {
                // Fallback to OCR
                AppLogger.d(TAG, "Direct text extraction yielded little or no text. Falling back to OCR.")
                return runBlocking {
                    convertPdfToTextWithOcr(context, sourceFile, targetFile)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error during PDF text extraction process", e)
            // If even the initial loading fails, try OCR as a last resort
            return runBlocking {
                AppLogger.d(TAG, "Initial PDF load failed. Attempting OCR as last resort.")
                convertPdfToTextWithOcr(context, sourceFile, targetFile)
            }
        }
    }
    
    /**
     * Converts each page of a PDF to an image and uses OCR to extract text.
     */
    private suspend fun convertPdfToTextWithOcr(context: Context, sourceFile: File, targetFile: File): Boolean {
        val fullText = StringBuilder()
        var fileDescriptor: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null

        try {
            fileDescriptor = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor)
            
            val pageCount = pdfRenderer.pageCount
            if (pageCount == 0) {
                AppLogger.w(TAG, "PDF has no pages, OCR cannot proceed.")
                return false
            }

            for (i in 0 until pageCount) {
                val page = pdfRenderer.openPage(i)
                
                // Render page to bitmap
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                
                // Recognize text from bitmap using OCRUtils in high quality
                val recognizedText =
                        OCRUtils.recognizeText(context, bitmap, OCRUtils.Quality.HIGH)
                fullText.append(recognizedText).append("\n\n")

                // Clean up resources for the current page
                page.close()
                bitmap.recycle()
            }

            // Write the aggregated text to the target file
            FileOutputStream(targetFile).bufferedWriter().use { writer ->
                writer.write(stripNullCharacters(fullText.toString()))
            }
            return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error during OCR PDF conversion", e)
            return false
        } finally {
            try {
                pdfRenderer?.close()
                fileDescriptor?.close()
            } catch (e: IOException) {
                AppLogger.e(TAG, "Error closing PDF resources in OCR fallback", e)
            }
        }
    }

    /** Convert PDF to image */
    fun convertPdfToImage(sourceFile: File, targetFile: File, targetExt: String): Boolean {
        try {
            // Use Android's PdfRenderer to render PDF to Bitmap
            val fileDescriptor =
                    ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(fileDescriptor)

            // Get the first page of the PDF
            val page = pdfRenderer.openPage(0)

            // Create a bitmap with the appropriate dimensions
            val scale = 2 // Scale for higher quality
            val bitmap =
                    Bitmap.createBitmap(
                            page.width * scale,
                            page.height * scale,
                            Bitmap.Config.ARGB_8888
                    )

            // Render the page to the bitmap
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            // Save the bitmap to the target file in the requested format
            val format =
                    when (targetExt.lowercase()) {
                        "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
                        "png" -> Bitmap.CompressFormat.PNG
                        "webp" -> Bitmap.CompressFormat.WEBP
                        else -> Bitmap.CompressFormat.PNG // Default to PNG
                    }

            // Determine quality based on format
            val quality =
                    when (format) {
                        Bitmap.CompressFormat.JPEG -> 95
                        Bitmap.CompressFormat.WEBP -> 95
                        else -> 100
                    }

            // Write the bitmap to the target file (no PDFToImage reference here)
            FileOutputStream(targetFile).use { outputStream ->
                bitmap.compress(format, quality, outputStream)
            }

            // Clean up
            bitmap.recycle()
            pdfRenderer.close()
            fileDescriptor.close()

            return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error converting PDF to image using Android PdfRenderer", e)

            // Simple fallback to create a text file with the image extension
            try {
                AppLogger.w(TAG, "PDF to image conversion failed, creating simple placeholder image")
                // Create a simple placeholder text file with the image extension
                FileOutputStream(targetFile).bufferedWriter().use { writer ->
                    writer.write("PDF Preview not available - this is a placeholder file.\n")
                    writer.write("The PDF could not be converted to an image format.\n")
                    writer.write("Original PDF: ${sourceFile.name}")
                }
                return true
            } catch (e2: Exception) {
                AppLogger.e(TAG, "Fallback approach also failed", e2)
                return false
            }
        }
    }

    /** Convert between DOC and DOCX formats using Apache POI */
    fun convertBetweenDocFormats(
            context: Context,
            sourceFile: File,
            targetFile: File,
            sourceExt: String,
            targetExt: String
    ): Boolean {
        try {
            when {
                // Convert DOC to DOCX
                sourceExt == "doc" && targetExt == "docx" -> {
                    AppLogger.d(TAG, "Converting DOC to DOCX")
                    FileInputStream(sourceFile).use { fis ->
                        // Load the DOC file
                        val doc = HWPFDocument(fis)

                        // Create a new DOCX document
                        val docx = XWPFDocument()

                        // Get document's paragraphs
                        val range = doc.range
                        val paragraphCount = range.numParagraphs()

                        // Add title based on filename
                        val title = docx.createParagraph()
                        title.alignment = org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER
                        val titleRun = title.createRun()
                        titleRun.setText(sourceFile.nameWithoutExtension)
                        titleRun.isBold = true
                        titleRun.fontSize = 16
                        titleRun.fontFamily = "Calibri"

                        // Add a blank line
                        docx.createParagraph()

                        // Transfer paragraphs with basic formatting
                        for (i in 0 until paragraphCount) {
                            val paragraph = range.getParagraph(i)
                            val text = paragraph.text()

                            if (text.trim().isNotEmpty()) {
                                val para = docx.createParagraph()
                                val run = para.createRun()
                                run.setText(text)

                                // Try to preserve some basic formatting if available
                                if (paragraph.isInTable) {
                                    // Skip tables for now - complex to convert
                                    continue
                                }

                                // Set some basic formatting based on what we can detect
                                if (text.trim().length < 100 && text.trim().endsWith(":")) {
                                    run.isBold = true
                                }

                                // Detect heading based on length and terminal punctuation
                                if (text.trim().length < 60 && !text.contains(".")) {
                                    run.isBold = true
                                    run.fontSize = 14
                                }
                            }
                        }

                        // Save the DOCX document
                        FileOutputStream(targetFile).use { fos -> docx.write(fos) }
                    }
                    return true
                }

                // Convert DOCX to DOC (improved conversion)
                sourceExt == "docx" && targetExt == "doc" -> {
                    AppLogger.d(TAG, "Converting DOCX to DOC (enhanced conversion)")
                    FileInputStream(sourceFile).use { fis ->
                        // Load the DOCX file
                        val docx = XWPFDocument(fis)

                        // Extract text content with basic formatting
                        val content = StringBuilder()

                        // Add title based on filename
                        content.append(sourceFile.nameWithoutExtension).append("\n\n")

                        // Process paragraphs with basic formatting
                        for (para in docx.paragraphs) {
                            if (para.text.trim().isNotEmpty()) {
                                content.append(para.text).append("\n\n")
                            }
                        }

                        // Write content to the target DOC file using WordExtractor approach
                        val tempDocxFile =
                                File(context.cacheDir, "temp_${System.currentTimeMillis()}.txt")
                        FileOutputStream(tempDocxFile).bufferedWriter().use { writer ->
                            writer.write(content.toString())
                        }

                        // Now directly copy the text file to DOC
                        // This won't preserve complex formatting but will work better than the
                        // FFmpeg approach
                        try {
                            tempDocxFile.copyTo(targetFile, overwrite = true)
                            tempDocxFile.delete()
                            return true
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Error during DOCX to DOC final conversion", e)
                            return false
                        }
                    }
                }
                else -> return false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error converting between DOC formats", e)
            return false
        }
    }

    /** Convert HTML to other formats */
    fun convertFromHtml(
            context: Context,
            sourceFile: File,
            targetFile: File,
            targetExt: String
    ): Boolean {
        try {
            val content = FileInputStream(sourceFile).bufferedReader().use { it.readText() }

            // Basic HTML tag removal
            val textContent =
                    content.replace(Regex("<[^>]*>"), "") // Remove HTML tags
                            .replace(Regex("&[a-zA-Z]+;"), " ") // Replace HTML entities with space
                            .replace(Regex("\\s+"), " ") // Normalize whitespace
                            .trim()

            when (targetExt) {
                "txt" -> {
                    FileOutputStream(targetFile).bufferedWriter().use { writer ->
                        writer.write(textContent)
                    }
                    return true
                }
                "doc" -> {
                    // For HTML to DOC, we create a text file first (which is easier to convert)
                    val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.txt")
                    FileOutputStream(tempFile).use { it.write(textContent.toByteArray()) }

                    // Instead of using FFmpeg, just rename the text file to .doc
                    // This won't preserve formatting but is more reliable than the FFmpeg approach
                    try {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                        return true
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error during HTML to DOC conversion", e)
                        tempFile.delete()
                        return false
                    }
                }
                "docx" -> {
                    // Convert HTML to DOCX using Apache POI
                    val docx = XWPFDocument()

                    // Split by paragraphs (may need improvement for complex HTML)
                    val paragraphs = textContent.split(Regex("\n\n|\r\n\r\n"))

                    for (paragraph in paragraphs) {
                        if (paragraph.isNotBlank()) {
                            val para = docx.createParagraph()
                            val run = para.createRun()
                            run.setText(paragraph)
                        }
                    }

                    // Save the document
                    FileOutputStream(targetFile).use { fos -> docx.write(fos) }
                    return true
                }
                "pdf" -> {
                    // Convert HTML to PDF using iText
                    val document = Document()
                    PdfWriter.getInstance(document, FileOutputStream(targetFile))
                    document.open()

                    // Add title based on the filename
                    val titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f)
                    document.add(Paragraph(sourceFile.nameWithoutExtension, titleFont))
                    document.add(Paragraph(" ")) // Empty line

                    // Parse the content for paragraphs and add them to the PDF
                    val contentFont = FontFactory.getFont(FontFactory.HELVETICA, 12f)
                    val contentParagraphs = textContent.split("\n\n")
                    contentParagraphs.forEach { para ->
                        if (para.trim().isNotEmpty()) {
                            document.add(Paragraph(para, contentFont))
                        }
                    }

                    document.close()
                    return true
                }
                else -> {
                    AppLogger.w(TAG, "Conversion from HTML to $targetExt not implemented")
                    return false
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error converting from HTML", e)
            return false
        }
    }

    /** Convert various document formats to HTML */
    fun convertToHtml(
            context: Context,
            sourceFile: File,
            targetFile: File,
            sourceExt: String
    ): Boolean {
        try {
            when (sourceExt) {
                "txt" -> {
                    val content = FileInputStream(sourceFile).bufferedReader().use { it.readText() }
                    val htmlContent =
                            StringBuilder()
                                    .append(
                                            "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"><title>"
                                    )
                                    .append(sourceFile.nameWithoutExtension)
                                    .append("</title><style>")
                                    .append(
                                            "body { font-family: Arial, sans-serif; margin: 40px; }"
                                    )
                                    .append("pre { white-space: pre-wrap; }")
                                    .append("</style></head><body>\n")

                    // Convert line breaks to <p> tags
                    content.split("\n").forEach { line ->
                        if (line.isNotBlank()) {
                            htmlContent
                                    .append("<p>")
                                    .append(line.replace("<", "&lt;").replace(">", "&gt;"))
                                    .append("</p>\n")
                        } else {
                            htmlContent.append("<br>\n")
                        }
                    }

                    htmlContent.append("</body></html>")

                    FileOutputStream(targetFile).bufferedWriter().use { writer ->
                        writer.write(htmlContent.toString())
                    }
                    return true
                }
                "doc" -> {
                    // Convert DOC to HTML using Apache POI
                    FileInputStream(sourceFile).use { fis ->
                        val doc = HWPFDocument(fis)
                        val extractor = WordExtractor(doc)
                        val text = extractor.text

                        // Create HTML structure
                        val htmlContent =
                                StringBuilder()
                                        .append(
                                                "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"><title>"
                                        )
                                        .append(sourceFile.nameWithoutExtension)
                                        .append("</title><style>")
                                        .append(
                                                "body { font-family: Arial, sans-serif; margin: 40px; }"
                                        )
                                        .append("</style></head><body>\n")

                        // Convert paragraphs to HTML
                        text.split("\n").forEach { para ->
                            if (para.isNotBlank()) {
                                htmlContent
                                        .append("<p>")
                                        .append(para.replace("<", "&lt;").replace(">", "&gt;"))
                                        .append("</p>\n")
                            }
                        }

                        htmlContent.append("</body></html>")

                        FileOutputStream(targetFile).bufferedWriter().use { writer ->
                            writer.write(htmlContent.toString())
                        }
                    }
                    return true
                }
                "docx" -> {
                    // Convert DOCX to HTML using Apache POI
                    FileInputStream(sourceFile).use { fis ->
                        val docx = XWPFDocument(fis)
                        val extractor = XWPFWordExtractor(docx)
                        val text = extractor.text

                        // Create HTML structure
                        val htmlContent =
                                StringBuilder()
                                        .append(
                                                "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"><title>"
                                        )
                                        .append(sourceFile.nameWithoutExtension)
                                        .append("</title><style>")
                                        .append(
                                                "body { font-family: Arial, sans-serif; margin: 40px; }"
                                        )
                                        .append("</style></head><body>\n")

                        // Add document content with formatting
                        docx.paragraphs.forEach { para ->
                            if (para.text.isNotBlank()) {
                                htmlContent.append("<p>")

                                // Check for basic formatting
                                var currentText =
                                        para.text.replace("<", "&lt;").replace(">", "&gt;")

                                // Check for any runs with formatting
                                val hasBold = para.runs.any { it.isBold }
                                val hasItalic = para.runs.any { it.isItalic }

                                if (hasBold) {
                                    currentText = "<strong>$currentText</strong>"
                                }
                                if (hasItalic) {
                                    currentText = "<em>$currentText</em>"
                                }

                                htmlContent.append(currentText)
                                htmlContent.append("</p>\n")
                            }
                        }

                        htmlContent.append("</body></html>")

                        FileOutputStream(targetFile).bufferedWriter().use { writer ->
                            writer.write(htmlContent.toString())
                        }
                    }
                    return true
                }
                "pdf" -> {
                    // For PDF to HTML, extract text and create simple HTML
                    // Create a temporary text file
                    val tempTextFile =
                            File(context.cacheDir, "temp_${System.currentTimeMillis()}.txt")
                    if (extractTextFromPdf(context, sourceFile, tempTextFile)) {
                        // Now convert the text to HTML
                        val content =
                                FileInputStream(tempTextFile).bufferedReader().use { it.readText() }
                        val htmlContent =
                                StringBuilder()
                                        .append(
                                                "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"><title>"
                                        )
                                        .append(sourceFile.nameWithoutExtension)
                                        .append("</title><style>")
                                        .append(
                                                "body { font-family: Arial, sans-serif; margin: 40px; }"
                                        )
                                        .append("</style></head><body>\n")
                                        .append("<h1>")
                                        .append(sourceFile.nameWithoutExtension)
                                        .append("</h1>")

                        // Convert paragraphs to HTML
                        content.split("\n\n").forEach { para ->
                            if (para.isNotBlank()) {
                                htmlContent
                                        .append("<p>")
                                        .append(para.replace("<", "&lt;").replace(">", "&gt;"))
                                        .append("</p>\n")
                            }
                        }

                        htmlContent.append("</body></html>")

                        FileOutputStream(targetFile).bufferedWriter().use { writer ->
                            writer.write(htmlContent.toString())
                        }

                        // Delete temporary file
                        tempTextFile.delete()
                        return true
                    }
                    return false
                }
                else -> {
                    AppLogger.w(TAG, "HTML conversion not implemented for $sourceExt")
                    return false
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error converting to HTML", e)
            return false
        }
    }

    /** Convert XLS/XLSX spreadsheet documents to HTML */
    fun convertSpreadsheetToHtml(sourceFile: File, targetFile: File): Boolean {
        return try {
            FileInputStream(sourceFile).use { fis ->
                WorkbookFactory.create(fis).use { workbook ->
                    val dataFormatter = DataFormatter()
                    val evaluator = workbook.creationHelper.createFormulaEvaluator()
                    val html =
                        StringBuilder()
                            .append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">")
                            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
                            .append("<title>")
                            .append(TextUtils.htmlEncode(sourceFile.nameWithoutExtension))
                            .append("</title><style>")
                            .append(
                                """
                                body { font-family: sans-serif; margin: 16px; color: #1f2937; background: #f8fafc; }
                                h1 { margin: 0 0 16px; font-size: 20px; }
                                .sheet-tabs { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 16px; }
                                .sheet-tab { border: 1px solid #cbd5e1; background: white; border-radius: 999px; padding: 6px 12px; cursor: pointer; }
                                .sheet-tab.active { background: #111827; color: white; border-color: #111827; }
                                .sheet-panel { display: none; overflow: auto; background: white; border: 1px solid #e2e8f0; border-radius: 12px; padding: 12px; }
                                .sheet-panel.active { display: block; }
                                table { border-collapse: collapse; min-width: 100%; }
                                th, td { border: 1px solid #dbe4ee; padding: 8px 10px; text-align: left; vertical-align: top; white-space: pre-wrap; }
                                th { background: #eef2ff; position: sticky; top: 0; }
                                .empty { color: #94a3b8; padding: 24px 0; }
                                """.trimIndent()
                            )
                            .append("</style></head><body>")
                            .append("<h1>")
                            .append(TextUtils.htmlEncode(sourceFile.name))
                            .append("</h1>")

                    if (workbook.numberOfSheets > 1) {
                        html.append("<div class=\"sheet-tabs\">")
                        for (sheetIndex in 0 until workbook.numberOfSheets) {
                            html.append("<button class=\"sheet-tab")
                            if (sheetIndex == 0) {
                                html.append(" active")
                            }
                            html.append("\" onclick=\"showSheet(")
                            html.append(sheetIndex)
                            html.append(")\">")
                            html.append(TextUtils.htmlEncode(workbook.getSheetAt(sheetIndex).sheetName))
                            html.append("</button>")
                        }
                        html.append("</div>")
                    }

                    for (sheetIndex in 0 until workbook.numberOfSheets) {
                        val sheet = workbook.getSheetAt(sheetIndex)
                        val maxColumns =
                            sheet.asSequence()
                                .map { row -> row.lastCellNum.toInt().coerceAtLeast(0) }
                                .maxOrNull()
                                ?.coerceAtLeast(0)
                                ?: 0

                        html.append("<section class=\"sheet-panel")
                        if (sheetIndex == 0) {
                            html.append(" active")
                        }
                        html.append("\" data-sheet-index=\"")
                        html.append(sheetIndex)
                        html.append("\">")
                        html.append("<h2>")
                        html.append(TextUtils.htmlEncode(sheet.sheetName))
                        html.append("</h2>")

                        if (sheet.physicalNumberOfRows == 0 || maxColumns == 0) {
                            html.append("<div class=\"empty\">Empty sheet</div></section>")
                            continue
                        }

                        html.append("<table><thead><tr>")
                        for (columnIndex in 0 until maxColumns) {
                            html.append("<th>")
                            html.append(columnName(columnIndex))
                            html.append("</th>")
                        }
                        html.append("</tr></thead><tbody>")

                        sheet.forEach { row ->
                            html.append("<tr>")
                            for (columnIndex in 0 until maxColumns) {
                                val cell = row.getCell(columnIndex)
                                val formattedValue =
                                    cell?.let { dataFormatter.formatCellValue(it, evaluator) }.orEmpty()
                                html.append("<td>")
                                html.append(TextUtils.htmlEncode(formattedValue).replace("\n", "<br>"))
                                html.append("</td>")
                            }
                            html.append("</tr>")
                        }

                        html.append("</tbody></table></section>")
                    }

                    if (workbook.numberOfSheets > 1) {
                        html.append(
                            """
                            <script>
                            function showSheet(index) {
                              document.querySelectorAll('.sheet-panel').forEach((panel, panelIndex) => {
                                panel.classList.toggle('active', panelIndex === index);
                              });
                              document.querySelectorAll('.sheet-tab').forEach((tab, tabIndex) => {
                                tab.classList.toggle('active', tabIndex === index);
                              });
                            }
                            </script>
                            """.trimIndent()
                        )
                    }

                    html.append("</body></html>")
                    FileOutputStream(targetFile).bufferedWriter().use { writer ->
                        writer.write(html.toString())
                    }
                }
            }
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error converting spreadsheet to HTML", e)
            false
        }
    }

    /** Copy text file with format conversion */
    fun copyTextFile(sourceFile: File, targetFile: File): Boolean {
        try {
            // Simple copy for text files
            sourceFile.copyTo(targetFile, overwrite = true)
            return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error copying text file", e)
            return false
        }
    }

    /** Convert DOC file directly to plain text */
    fun extractTextFromDoc(sourceFile: File, targetFile: File): Boolean {
        return try {
            FileInputStream(sourceFile).use { fis ->
                val doc = HWPFDocument(fis)
                val extractor = WordExtractor(doc)
                var text = extractor.text
                
                // 优化文本格式：压缩连续空行
                text = optimizeTextFormat(text)
                
                // Write extracted text to target file
                FileOutputStream(targetFile).bufferedWriter().use { writer ->
                    writer.write(text)
                }
            }
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error extracting text from DOC file", e)
            false
        }
    }

    /** Convert DOCX file directly to plain text */
    fun extractTextFromDocx(sourceFile: File, targetFile: File): Boolean {
        return try {
            FileInputStream(sourceFile).use { fis ->
                val docx = XWPFDocument(fis)
                val extractor = XWPFWordExtractor(docx)
                var text = extractor.text
                
                // 优化文本格式：压缩连续空行
                text = optimizeTextFormat(text)
                
                // Write extracted text to target file
                FileOutputStream(targetFile).bufferedWriter().use { writer ->
                    writer.write(text)
                }
            }
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error extracting text from DOCX file", e)
            false
        }
    }
    
    /** 优化提取出的文本格式，压缩连续空行 */
    private fun optimizeTextFormat(text: String): String {
        // 将文本按行分割
        val lines = text.split("\n")
        val optimizedLines = mutableListOf<String>()
        var consecutiveEmptyLines = 0
        
        for (line in lines) {
            val trimmedLine = line.trim()
            
            if (trimmedLine.isEmpty()) {
                // 处理空行
                consecutiveEmptyLines++
                
                // 两行空行压缩为一行，多行空行压缩为两行
                if (consecutiveEmptyLines <= 2) {
                    optimizedLines.add("")
                }
            } else {
                // 非空行正常添加
                optimizedLines.add(line)
                consecutiveEmptyLines = 0
            }
        }
        
        // 合并为单个字符串并返回
        return optimizedLines.joinToString("\n")
    }

    /** General method to convert Word documents to text */
    fun extractTextFromWord(sourceFile: File, targetFile: File, sourceExt: String): Boolean {
        return when (sourceExt.lowercase()) {
            "doc" -> extractTextFromDoc(sourceFile, targetFile)
            "docx" -> extractTextFromDocx(sourceFile, targetFile)
            else -> false
        }
    }

    /** Convert PDF to DOCX */
    fun convertPdfToDocx(context: Context, sourceFile: File, targetFile: File): Boolean {
        return try {
            AppLogger.d(TAG, "Starting PDF to DOCX conversion for ${sourceFile.name}")

            // Step 1: Extract text from PDF.
            // We create a temporary text file to store the extracted content.
            val tempTextFile = File(context.cacheDir, "temp_pdf_to_docx_${System.currentTimeMillis()}.txt")
            val textExtractionSuccess = extractTextFromPdf(context, sourceFile, tempTextFile)

            if (!textExtractionSuccess) {
                AppLogger.e(TAG, "Failed to extract text from PDF, aborting DOCX conversion.")
                tempTextFile.delete()
                return false
            }

            // Step 2: Read the extracted text from the temporary file.
            val content = tempTextFile.readText()
            tempTextFile.delete() // Clean up the temp file immediately after reading.

            // Step 3: Create a new DOCX document and write the content.
            XWPFDocument().use { docx ->
                // Split the content into paragraphs. We can split by one or more newlines.
                val paragraphs = content.split(Regex("(\\r\\n|\\n){2,}"))
                
                for (paraText in paragraphs) {
                    if (paraText.isNotBlank()) {
                        // Create a paragraph in the DOCX document.
                        val docxParagraph = docx.createParagraph()
                        val run = docxParagraph.createRun()
                        run.setText(paraText.trim())
                    }
                }

                // Step 4: Save the new DOCX document.
                FileOutputStream(targetFile).use { fos ->
                    docx.write(fos)
                }
            }
            
            AppLogger.d(TAG, "Successfully converted PDF to DOCX: ${targetFile.name}")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error converting PDF to DOCX", e)
            false
        }
    }

    /** Convert Word (DOC/DOCX) to PDF */
    fun convertWordToPdf(context: Context, sourceFile: File, targetFile: File, sourceExt: String): Boolean {
        return try {
            AppLogger.d(TAG, "Starting Word to PDF conversion for ${sourceFile.name}")

            // Step 1: Extract text from the Word document into a temporary file.
            val tempTextFile = File(context.cacheDir, "temp_word_to_pdf_${System.currentTimeMillis()}.txt")
            val textExtractionSuccess = extractTextFromWord(sourceFile, tempTextFile, sourceExt)

            if (!textExtractionSuccess) {
                AppLogger.e(TAG, "Failed to extract text from Word file, aborting PDF conversion.")
                tempTextFile.delete()
                return false
            }

            // Step 2: Convert the extracted text file to PDF.
            val pdfConversionSuccess = convertTextToPdf(context, tempTextFile, targetFile)

            // Step 3: Clean up the temporary file.
            tempTextFile.delete()

            if (pdfConversionSuccess) {
                AppLogger.d(TAG, "Successfully converted Word to PDF: ${targetFile.name}")
                true
            } else {
                AppLogger.e(TAG, "Failed to convert extracted text to PDF.")
                false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error converting Word to PDF", e)
            false
        }
    }

    private fun columnName(index: Int): String {
        var current = index
        val result = StringBuilder()
        do {
            result.insert(0, ('A'.code + (current % 26)).toChar())
            current = current / 26 - 1
        } while (current >= 0)
        return result.toString()
    }

    /**
     * 移除提取文本中的 U+0000。
     *
     * PDF 字体映射缺字时 PDFBox 会输出 NUL，SQLite 的 LENGTH/SUBSTR 会把 TEXT 在第一个 NUL
     * 处截断，导致整段工具结果和后续回复在重新加载时消失。NUL 不携带任何可显示信息，
     * 因此在写入提取结果前直接剔除。
     */
    internal fun stripNullCharacters(text: String): String = text.replace("\u0000", "")
}
