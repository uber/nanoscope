package com.uber.nanoscope

import java.io.File

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

        fun startTracing(): Trace {
            val filename = "out.txt"
            val foregroundPackage = Adb.getForegroundPackage()
            return Trace(foregroundPackage, filename)
        }
    }
}
