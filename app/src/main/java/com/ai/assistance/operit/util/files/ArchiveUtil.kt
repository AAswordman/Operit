package com.ai.assistance.operit.util.files

import android.content.Context
import com.ai.assistance.operit.util.logging.AppLogger
import com.github.junrar.Archive
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.utils.IOUtils

/** Utility class for archive operations */
object ArchiveUtil {
    private const val TAG = "ArchiveUtil"
    private const val BUFFER_SIZE = 8192

    /** Convert between archive formats or extract */
    fun convertArchive(
            context: Context,
            sourceFile: File,
            targetFile: File,
            sourceExt: String,
            targetExt: String,
            extraParams: String? = null,
            password: String? = null
    ): Boolean {
        AppLogger.d(TAG, "Converting archive from $sourceExt to $targetExt")

        try {
            // If target is "extract", extract the archive
            if (targetExt == "extract") {
                val extractDir = targetFile
                AppLogger.d(TAG, "Extracting ${sourceFile.name} to directory ${extractDir.absolutePath}")
                val extractResult = extractArchive(sourceFile, extractDir, sourceExt, password)

                // Check if extraction failed due to encryption
                val noteFile = File(extractDir, "EXTRACTION_FAILED.txt")
                if (!extractResult && noteFile.exists()) {
                    val errMessage = noteFile.readText()
                    if (errMessage.contains("encrypted") ||
                                    errMessage.contains("password-protected")
                    ) {
                        throw IOException(
                                "Cannot extract encrypted archive: ${sourceFile.name}. Password-protected archives are not supported."
                        )
                    }
                }

                return extractResult
            }

            // For archive format conversion, we extract to temp directory then repackage
            AppLogger.d(TAG, "Converting archive from $sourceExt to $targetExt format")
            val tempDir = File(context.cacheDir, "temp_extract_${System.currentTimeMillis()}")

            try {
                if (extractArchive(sourceFile, tempDir, sourceExt, password)) {
                    val result = createArchive(tempDir, targetFile, targetExt)
                    AppLogger.d(TAG, "Archive conversion ${if (result) "successful" else "failed"}")
                    return result
                } else {
                    // Check if extraction failed due to encryption
                    val noteFile = File(tempDir, "EXTRACTION_FAILED.txt")
                    if (noteFile.exists()) {
                        val errMessage = noteFile.readText()
                        if (errMessage.contains("encrypted") ||
                                        errMessage.contains("password-protected")
                        ) {
                            throw IOException(
                                    "Cannot convert encrypted archive: ${sourceFile.name}. " +
                                            (if (password != null)
                                                    "The provided password may be incorrect."
                                            else
                                                    "No password was provided for the encrypted archive.")
                            )
                        }
                    }

                    AppLogger.e(TAG, "Failed to extract source archive for conversion")
                    return false
                }
            } finally {
                // Always clean up temporary directory
                if (tempDir.exists()) {
                    AppLogger.d(TAG, "Cleaning up temporary directory")
                    tempDir.deleteRecursively()
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error converting archive", e)
            throw e // Re-throw to allow proper error handling by caller
        }
    }

    /** Create directory if it doesn't exist */
    fun ensureDirectoryExists(directory: File): Boolean {
        return if (!directory.exists()) {
            directory.mkdirs()
        } else {
            directory.isDirectory
        }
    }

    /** Extract an archive file */
    fun extractArchive(
            archiveFile: File,
            extractDir: File,
            archiveExt: String,
            password: String? = null
    ): Boolean {
        if (!ensureDirectoryExists(extractDir)) {
            return false
        }

        return try {
            when (archiveExt.lowercase()) {
                "zip" -> extractZip(archiveFile, extractDir, password)
                "tar" -> extractTar(archiveFile, extractDir)
                "7z" -> extract7z(archiveFile, extractDir, password)
                "rar" -> extractRar(archiveFile, extractDir, password)
                else -> false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error extracting archive", e)
            false
        }
    }

    /** Extract a zip file */
    fun extractZip(zipFile: File, targetDir: File, password: String? = null): Boolean {
        // Note: The standard ZipInputStream in Java doesn't support password-protected ZIPs
        // We would need a third-party library like zip4j to implement password support
        // For now, we'll just note that password-protection is detected

        try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry?
                val buffer = ByteArray(BUFFER_SIZE)

                try {
                    while (zis.nextEntry.also { entry = it } != null) {
                        val currentEntry = entry ?: continue

                        val fileName = currentEntry.name
                        val newFile = File(targetDir, fileName)

                        // Create directories if needed
                        if (currentEntry.isDirectory) {
                            ensureDirectoryExists(newFile)
                        } else {
                            newFile.parentFile?.let { ensureDirectoryExists(it) }

                            // Extract file
                            FileOutputStream(newFile).use { fos ->
                                BufferedOutputStream(fos).use { bos ->
                                    var len: Int
                                    while (zis.read(buffer).also { len = it } > 0) {
                                        bos.write(buffer, 0, len)
                                    }
                                }
                            }
                        }

                        zis.closeEntry()
                    }
                } catch (e: java.util.zip.ZipException) {
                    if (e.message?.contains("encrypted", ignoreCase = true) == true) {
                        val msg =
                                if (password != null) {
                                    "The ZIP file is encrypted, but standard Java ZipInputStream doesn't support passwords. " +
                                            "Please use a dedicated ZIP application."
                                } else {
                                    "The ZIP file appears to be password-protected or encrypted.\n" +
                                            "This tool currently does not support extracting encrypted ZIP files.\n" +
                                            "Please use a dedicated archive manager with password support."
                                }

                        AppLogger.e(
                                TAG,
                                "Encrypted ZIP file detected. Password-protected ZIP files are not supported",
                                e
                        )
                        // Create a note file in the target directory to inform the user
                        val noteFile = File(targetDir, "EXTRACTION_FAILED.txt")
                        noteFile.writeText(msg)
                        return false
                    } else {
                        throw e
                    }
                }
            }
            return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error extracting zip", e)
            return false
        }
    }

    /** Extract a tar file */
    fun extractTar(tarFile: File, targetDir: File): Boolean {
        try {
            TarArchiveInputStream(BufferedInputStream(FileInputStream(tarFile))).use { tis ->
                var entry: TarArchiveEntry?
                val buffer = ByteArray(BUFFER_SIZE)

                while (tis.nextTarEntry.also { entry = it } != null) {
                    val currentEntry = entry ?: continue

                    val fileName = currentEntry.name
                    val newFile = File(targetDir, fileName)

                    // Create directories if needed
                    if (currentEntry.isDirectory) {
                        ensureDirectoryExists(newFile)
                    } else {
                        newFile.parentFile?.let { ensureDirectoryExists(it) }

                        // Extract file
                        FileOutputStream(newFile).use { fos ->
                            BufferedOutputStream(fos).use { bos ->
                                var len: Int
                                while (tis.read(buffer).also { len = it } > 0) {
                                    bos.write(buffer, 0, len)
                                }
                            }
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            // Check if the error might be related to encryption
            if (e.message?.contains("encrypted", ignoreCase = true) == true ||
                            e.message?.contains("password", ignoreCase = true) == true
            ) {
                AppLogger.e(TAG, "Encrypted TAR file detected", e)
                // Create a note file in the target directory to inform the user
                val noteFile = File(targetDir, "EXTRACTION_FAILED.txt")
                noteFile.writeText(
                        "The TAR file appears to be password-protected or encrypted.\n" +
                                "This tool currently does not support extracting encrypted archives.\n" +
                                "Please use a dedicated archive manager with password support."
                )
                return false
            }

            AppLogger.e(TAG, "Error extracting tar", e)
            return false
        }
    }

    /** Extract a 7z file */
    fun extract7z(sevenZFile: File, targetDir: File, password: String? = null): Boolean {
        try {
            try {
                // Try to open the 7z file with password if provided
                val szf =
                        if (password != null) {
                            try {
                                SevenZFile(sevenZFile, password.toCharArray())
                            } catch (e: Exception) {
                                // If password doesn't work, try without password
                                AppLogger.w(
                                        TAG,
                                        "Failed to open 7z with password, trying without password",
                                        e
                                )
                                SevenZFile(sevenZFile)
                            }
                        } else {
                            SevenZFile(sevenZFile)
                        }

                szf.use { sz ->
                    var entry: SevenZArchiveEntry?
                    val buffer = ByteArray(BUFFER_SIZE)

                    try {
                        while (sz.nextEntry.also { entry = it } != null) {
                            val currentEntry = entry ?: continue

                            val fileName = currentEntry.name
                            val newFile = File(targetDir, fileName)

                            // Create directories if needed
                            if (currentEntry.isDirectory) {
                                ensureDirectoryExists(newFile)
                            } else {
                                newFile.parentFile?.let { ensureDirectoryExists(it) }

                                // Extract file
                                FileOutputStream(newFile).use { fos ->
                                    BufferedOutputStream(fos).use { bos ->
                                        var len: Int
                                        while (sz.read(buffer).also { len = it } > 0) {
                                            bos.write(buffer, 0, len)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (e.message?.contains("password", ignoreCase = true) == true ||
                                        e.message?.contains("encrypted", ignoreCase = true) == true
                        ) {
                            throw IOException("Encrypted 7z file detected", e)
                        } else {
                            throw e
                        }
                    }
                }
                return true
            } catch (e: IOException) {
                // Check if this is an encryption-related error
                if (e.message?.contains("password", ignoreCase = true) == true ||
                                e.message?.contains("encrypted", ignoreCase = true) == true
                ) {
                    val msg =
                            if (password != null) {
                                "The 7z file is encrypted, but the provided password was incorrect.\n" +
                                        "Please check the password and try again."
                            } else {
                                "The 7z file appears to be password-protected or encrypted.\n" +
                                        "Please provide a password to extract this archive."
                            }

                    AppLogger.e(TAG, "Encrypted 7z file detected", e)
                    // Create a note file in the target directory to inform the user
                    val noteFile = File(targetDir, "EXTRACTION_FAILED.txt")
                    noteFile.writeText(msg)
                    return false
                } else {
                    throw e
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error extracting 7z", e)
            return false
        }
    }

    /** Extract a RAR file */
    fun extractRar(rarFile: File, targetDir: File, password: String? = null): Boolean {
        try {
            try {
                // Create Archive with password if provided
                val archive =
                        if (password != null) {
                            try {
                                Archive(rarFile, password)
                            } catch (e: Exception) {
                                // If password doesn't work, try without password
                                AppLogger.w(
                                        TAG,
                                        "Failed to open RAR with password, trying without password",
                                        e
                                )
                                Archive(rarFile)
                            }
                        } else {
                            Archive(rarFile)
                        }

                archive.use { archive ->
                    val buffer = ByteArray(BUFFER_SIZE)

                    // Check if RAR is password protected
                    if (archive.isEncrypted && password == null) {
                        AppLogger.e(TAG, "Encrypted RAR file detected, but no password provided")
                        // Create a note file in the target directory to inform the user
                        val noteFile = File(targetDir, "EXTRACTION_FAILED.txt")
                        noteFile.writeText(
                                "The RAR file is password-protected or encrypted.\n" +
                                        "Please provide a password to extract this archive."
                        )
                        return false
                    }

                    archive.fileHeaders.forEach { fileHeader ->
                        val fileName = fileHeader.fileName
                        val newFile = File(targetDir, fileName)

                        // Create directories if needed
                        if (fileHeader.isDirectory) {
                            ensureDirectoryExists(newFile)
                        } else {
                            newFile.parentFile?.let { ensureDirectoryExists(it) }

                            // Extract file
                            FileOutputStream(newFile).use { fos ->
                                BufferedOutputStream(fos).use { bos ->
                                    archive.extractFile(fileHeader, bos)
                                }
                            }
                        }
                    }
                }
                return true
            } catch (e: Exception) {
                // Check if this is an encryption-related error
                if (e.message?.contains("password", ignoreCase = true) == true ||
                                e.message?.contains("encrypted", ignoreCase = true) == true
                ) {
                    val msg =
                            if (password != null) {
                                "The RAR file is encrypted, but the provided password was incorrect.\n" +
                                        "Please check the password and try again."
                            } else {
                                "The RAR file appears to be password-protected or encrypted.\n" +
                                        "Please provide a password to extract this archive."
                            }

                    AppLogger.e(TAG, "Encrypted RAR file detected", e)
                    // Create a note file in the target directory to inform the user
                    val noteFile = File(targetDir, "EXTRACTION_FAILED.txt")
                    noteFile.writeText(msg)
                    return false
                } else {
                    throw e
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error extracting RAR", e)
            return false
        }
    }

    /** Create an archive from a directory */
    fun createArchive(sourceDir: File, targetFile: File, archiveExt: String): Boolean {
        return try {
            when (archiveExt.lowercase()) {
                "zip" -> createZip(sourceDir, targetFile)
                "tar" -> createTar(sourceDir, targetFile)
                "7z" -> create7z(sourceDir, targetFile)
                "rar" -> false // RAR creation is not supported due to license restrictions
                else -> false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error creating archive", e)
            false
        }
    }

    /** Create a zip file from a directory */
    fun createZip(sourceDir: File, zipFile: File): Boolean {
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                addToZip(sourceDir, sourceDir, zos)
            }
            return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error creating zip", e)
            return false
        }
    }

    /** Add files to a zip archive recursively */
    fun addToZip(baseDir: File, currentDir: File, zos: ZipOutputStream) {
        val files = currentDir.listFiles() ?: return
        val buffer = ByteArray(BUFFER_SIZE)

        for (file in files) {
            if (file.isDirectory) {
                addToZip(baseDir, file, zos)
                continue
            }

            val relativePath = file.toRelativeString(baseDir)
            val entry = ZipEntry(relativePath)
            zos.putNextEntry(entry)

            FileInputStream(file).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    var len: Int
                    while (bis.read(buffer).also { len = it } > 0) {
                        zos.write(buffer, 0, len)
                    }
                }
            }

            zos.closeEntry()
        }
    }

    /** Create a tar file from a directory */
    fun createTar(sourceDir: File, tarFile: File): Boolean {
        try {
            TarArchiveOutputStream(BufferedOutputStream(FileOutputStream(tarFile))).use { tos ->
                // Set the long file mode to handle longer filenames
                tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
                addToTar(sourceDir, sourceDir, tos)
            }
            return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error creating tar", e)
            return false
        }
    }

    /** Add files to a TAR archive recursively */
    fun addToTar(baseDir: File, currentDir: File, tos: TarArchiveOutputStream) {
        val files = currentDir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                // Add directory entry
                val relativePath = file.toRelativeString(baseDir) + "/"
                val entry = TarArchiveEntry(file, relativePath)
                tos.putArchiveEntry(entry)
                tos.closeArchiveEntry()

                // Add directory contents
                addToTar(baseDir, file, tos)
            } else {
                // Add file entry
                val relativePath = file.toRelativeString(baseDir)
                val entry = TarArchiveEntry(file, relativePath)
                tos.putArchiveEntry(entry)

                FileInputStream(file).use { fis ->
                    BufferedInputStream(fis).use { bis -> IOUtils.copy(bis, tos) }
                }

                tos.closeArchiveEntry()
            }
        }
    }

    /** Create a 7z file from a directory */
    fun create7z(sourceDir: File, sevenZFile: File): Boolean {
        try {
            SevenZOutputFile(sevenZFile).use { szof -> addTo7z(sourceDir, sourceDir, szof) }
            return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error creating 7z", e)
            return false
        }
    }

    /** Add files to a 7z archive recursively */
    fun addTo7z(baseDir: File, currentDir: File, szof: SevenZOutputFile) {
        val files = currentDir.listFiles() ?: return
        val buffer = ByteArray(BUFFER_SIZE)

        for (file in files) {
            val relativePath = file.toRelativeString(baseDir)

            if (file.isDirectory) {
                // Make sure we include directory entries with trailing slashes
                val directoryPath =
                        if (relativePath.endsWith("/")) relativePath else "$relativePath/"
                val entry = szof.createArchiveEntry(file, directoryPath)
                szof.putArchiveEntry(entry)
                szof.closeArchiveEntry()

                // Process directory contents
                addTo7z(baseDir, file, szof)
            } else {
                // Add file entry
                val entry = szof.createArchiveEntry(file, relativePath)
                szof.putArchiveEntry(entry)

                FileInputStream(file).use { fis ->
                    BufferedInputStream(fis).use { bis ->
                        var len: Int
                        while (bis.read(buffer).also { len = it } > 0) {
                            szof.write(buffer, 0, len)
                        }
                    }
                }

                szof.closeArchiveEntry()
            }
        }
    }
}
