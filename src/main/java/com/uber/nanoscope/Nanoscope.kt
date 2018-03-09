package com.uber.nanoscope

import com.sun.org.apache.xml.internal.security.utils.Base64
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class Nanoscope {

    class Trace(
            private val packageName: String,
            private val filename: String) {

        init {
            Adb.setSystemProperty("dev.nanoscope", "$packageName:$filename")
        }

        fun stop() {
            println("Flushing trace data... (Do not close app)")
            Adb.setSystemProperty("dev.nanoscope", "\'\'")
            val remotePath = "/data/data/$packageName/files/$filename"
            while (!Adb.fileExists(remotePath)) {
                Thread.sleep(500)
            }

            val localPath = File.createTempFile("nanoscope", ".trace").absolutePath
            println("Pulling trace file... ($localPath)")
            Adb.pullFile(remotePath, localPath)

            val htmlPath = File.createTempFile("nanoscope", ".html").absolutePath
            println("Building HTML... ($htmlPath)")
            val html = buildHtml(localPath)
            File(htmlPath).writeText(html)

            println("Opening HTML...")
            Runtime.getRuntime().exec("open $htmlPath")
        }

        private fun buildHtml(traceFilePath: String): String {
            val traceDataString = File(traceFilePath).readText()
            val html = javaClass.classLoader.getResourceAsStream("index.html").bufferedReader().readText()
            return html.replaceFirst("TRACE_DATA_PLACEHOLDER", traceDataString)
        }
    }

    companion object {

        private val homeDir = File(System.getProperty("user.home"))
        private val configDir = File(homeDir, ".nanoscope")

        fun startTracing(): Trace {
            val filename = "out.txt"
            val foregroundPackage = Adb.getForegroundPackage()
            return Trace(foregroundPackage, filename)
        }

        fun flashDevice(romUrl: String) {
            val md5 = MessageDigest.getInstance("MD5").digest(romUrl.toByteArray())
            val key = Base64.encode(md5)
            val outDir = File(configDir, "roms/$key")

            downloadIfNecessary(outDir, romUrl)

            val installScipt = File(outDir, "install.sh")
            if (!installScipt.exists()) {
                throw FlashException("Invalid ROM. install.sh script does not exist.")
            }

            installScipt.setExecutable(true)

            println("Flashing device...")
            val status = ProcessBuilder("./install.sh")
                    .directory(outDir)
                    .inheritIO()
                    .start()
                    .waitFor()

            if (status != 0) {
                throw FlashException("Flash failed: $status")
            }
        }

        private fun downloadIfNecessary(outDir: File, romUrl: String) {
            val downloadSuccessFile = File(outDir, "SUCCESS")
            if (downloadSuccessFile.exists()) {
                println("ROM already downloaded: $outDir...")
                return
            }

            outDir.deleteRecursively()

            val url = try {
                URL(romUrl)
            } catch (e: MalformedURLException) {
                throw FlashException("Invalid URL: $romUrl")
            }

            val conn = try {
                url.openConnection()
            } catch (e: IOException) {
                throw FlashException("Failed to open connection: ${e.message}")
            }

            if (conn.contentType != "application/zip") {
                throw FlashException("URL must be a zip file: $romUrl.\nFound Content-Type: ${conn.contentType}.")
            }

            try {
                downloadROM(outDir, conn.inputStream.buffered())
            } catch (e: IOException) {
                throw FlashException("Failed to download ROM: ${e.message}")
            }

            downloadSuccessFile.createNewFile()
        }

        private fun downloadROM(outDir: File, `in`: InputStream) {
            println("Downloading to $outDir...")

            outDir.mkdirs()
            ZipInputStream(`in`).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val file = File(outDir, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        println("Downloading: ${entry.name}...")
                        file.outputStream().buffered().use { out ->
                            zipIn.copyTo(out)
                        }
                        zipIn.closeEntry()
                    }
                    entry = zipIn.nextEntry
                }
            }
        }
    }
}

class FlashException(message: String) : RuntimeException(message)
