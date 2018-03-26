package com.uber.nanoscope

import com.squareup.moshi.KotlinJsonAdapterFactory
import com.squareup.moshi.Moshi
import com.sun.org.apache.xml.internal.security.utils.Base64
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {

    override fun toString(): String {
        return "$major.$minor.$patch"
    }

    override fun compareTo(other: Version): Int {
        val majorRes = major.compareTo(other.major)
        if (majorRes != 0) {
            return majorRes
        }

        val minorRes = minor.compareTo(other.minor)
        if (minorRes != 0) {
            return minorRes
        }

        return patch.compareTo(other.patch)
    }

    companion object {

        fun fromString(str: String): Version {
            val parts = str.split(".")
            if (parts.size != 3) {
                throw IllegalArgumentException("Invalid version string: $str")
            }

            fun parsePart(index: Int): Int {
                return parts[index].toIntOrNull() ?: throw IllegalArgumentException("Invalid version string: $str")
            }

            val major = parsePart(0)
            val minor = parsePart(1)
            val patch = parsePart(2)
            return Version(major, minor, patch)
        }
    }
}

class IncompatibleVersionError(val clientVersion: Version, val romVersion: Version?): RuntimeException()

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

            val localFile = File.createTempFile("nanoscope", ".txt")
            val localPath = localFile.absolutePath
            println("Pulling trace file... ($localPath)")
            Adb.pullFile(remotePath, localPath)

            displayTrace(localFile)
        }
    }

    companion object {

        private val homeDir = File(System.getProperty("user.home"))
        private val configDir = File(homeDir, ".nanoscope")

        fun checkVersion() {
            val clientVersion = getClientVersion()
            val romVersion = getROMVersion() ?: throw IncompatibleVersionError(clientVersion, null)
            if (clientVersion.major != romVersion.major
                    || (clientVersion.major == 0 && clientVersion.minor != romVersion.minor)) {
                throw IncompatibleVersionError(clientVersion, romVersion)
            }
        }

        private fun getClientVersion(): Version {
            Nanoscope::class.java.classLoader.getResourceAsStream("version.txt").bufferedReader().use {
                return Version.fromString(it.readText().trim())
            }
        }

        private fun getROMVersion(): Version? {
            return try {
                Version.fromString(Adb.getROMVersion())
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        fun openTrace(file: File) {
            val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(OpenHandler.TraceEvent::class.java)
            val events = sortedSetOf<OpenHandler.Event>()
            var nanotraceFile = file

            file.bufferedReader().use {
                if (it.readLine().startsWith("[")) {
                    // This appears to be a Chrome trace file so convert to a Nanoscope trace file before opening.
                    nanotraceFile = createTempFile("nanoscope", ".txt")
                    file.useLines { lines ->
                        lines
                                .filter { it != "["  && it != "]" && "{}" !in it}
                                .map { adapter.fromJson(it)!! }
                                .filter { it.ph == "X" }
                                .forEach {
                                    val startUs = it.ts.toDouble()
                                    val endUs = startUs + it.dur!!.toDouble()
                                    val start = startUs * 1000
                                    val end = endUs * 1000
                                    val duration = end - start
                                    events.add(OpenHandler.Event(it.name, start, true, duration))
                                    events.add(OpenHandler.Event(it.name, end, false, duration))
                                }
                    }

                    nanotraceFile.printWriter().use { out ->
                        var firstTimestamp: Long? = null
                        events.forEach {
                            var timestamp = it.timestamp.toLong()
                            if (firstTimestamp == null) {
                                firstTimestamp = timestamp
                                println("first timestamp: $firstTimestamp")
                            }
                            timestamp -= firstTimestamp!!
                            out.println(if (it.start) "$timestamp:+${it.name}" else "$timestamp:POP")
                        }
                    }
                }
            }

            displayTrace(nanotraceFile)
        }

        fun startTracing(packageName: String?): Trace {
            Adb.root()
            val filename = "out.txt"
            val tracedPackage = packageName ?: Adb.getForegroundPackage()
            return Trace(tracedPackage, filename)
        }

        fun flashDevice(romUrl: String) {
            Adb.root()
            val md5 = MessageDigest.getInstance("MD5").digest(romUrl.toByteArray())
            val key = Base64.encode(md5)
            val outDir = File(configDir, "roms/$key")

            downloadIfNecessary(outDir, romUrl)

            val installScript = File(outDir, "install.sh")
            if (!installScript.exists()) {
                throw FlashException("Invalid ROM. install.sh script does not exist.")
            }

            installScript.setExecutable(true)

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

        private fun displayTrace(traceFile: File) {
            val traceDataString = traceFile.readText()
            var html = this::class.java.classLoader.getResourceAsStream("index.html").bufferedReader().readText()
            html = html.replaceFirst("TRACE_DATA_PLACEHOLDER", traceDataString)

            val htmlPath = File.createTempFile("nanoscope", ".html").absolutePath
            println("Building HTML... ($htmlPath)")

            File(htmlPath).writeText(html)

            println("Opening HTML...")
            Runtime.getRuntime().exec("open $htmlPath")
        }
    }
}

class FlashException(message: String) : RuntimeException(message)
