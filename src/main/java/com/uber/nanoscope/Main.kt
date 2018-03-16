package com.uber.nanoscope

import java.io.File
import kotlin.reflect.KClass
import kotlin.system.exitProcess

val ROM_URL = "https://s3-us-west-2.amazonaws.com/uber-common-public/nanoscope/nanoscope-rom-0.0.1.zip"

/**
 * Represents available subcommands.
 */
enum class Subcommand(
        private val handlerClass: KClass<out Runnable>,
        private val help: String) {
    START(StartHandler::class, "Starts tracing on adb-connected device."),
    FLASH(FlashHandler::class, "Flashes adb-connected device with Nanoscope image."),
    OPEN(OpenHandler::class, "Opens a trace file with the Nanoscope Visualizer. Currently support Nanoscope trace file and Chrome trace file formats");

    val usage: String
    get() = "${name.toLowerCase()}: $help"

    fun handle(args: List<String>) {
        return handlerClass.constructors
                .first()
                .call(args)
                .run()
    }
}

/**
 * Handler for "nanoscope start" subcommand.
 */
class StartHandler(private val args: List<String>): Runnable {

    override fun run() {
        val trace = Nanoscope.startTracing()
        println("Tracing... (Press ENTER to stop)")
        while (true) {
            if (System.`in`.read() == 10) {
                break
            }
        }
        trace.stop()
    }
}

/**
 * Handler for "nanoscope flash" subcommand.
 */
class FlashHandler(private val args: List<String>): Runnable {

    override fun run() {
        try {
            Nanoscope.flashDevice(ROM_URL)
        } catch (e: FlashException) {
            println(e.message)
            exitProcess(1)
        }
    }
}

/**
 * Handler for "nanoscope open" subcommand.
 */
class OpenHandler(private val args: List<String>): Runnable {

    override fun run() {
        if (args.isEmpty()) {
            println("usage: nanoscope open <tracefile>")
            exitProcess(1)
        }

        val inFile = File(args[0])
        if (!inFile.exists()) {
            println("File does not exist: $inFile")
            exitProcess(1)
        }

        Nanoscope.openTrace(inFile)
    }

    data class Event(
            val name: String,
            val timestamp: Double,
            val start: Boolean,
            val duration: Double): Comparable<Event> {

        override fun compareTo(other: Event): Int {
            val r = timestamp.compareTo(other.timestamp)
            if (r != 0) {
                return r
            }

            return if (start) {
                if (other.start) {
                    -duration.compareTo(other.duration)
                } else {
                    1
                }
            } else {
                if (other.start) {
                    -1
                } else {
                     duration.compareTo(other.duration)
                }
            }
        }
    }
    data class TraceEvent(val name: String, val ph: String, val ts: String, val dur: String?)
}

/**
 * Entrypoint for "nanoscope" command.
 *
 * usage: nanoscope [start, flash, open]
 *   start: Starts tracing on adb-connected device.
 *   flash: Flashes adb-connected device with nanoscope image.
 *   open: Opens a trace file with the Nanoscope Visualizer. Currently supports Nanoscope and Chrome trace file formats.
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Must specify a subcommand: [start, flash].")
        exitProcess(1)
    }

    val subcommandOptions = Subcommand.values().map { it.name.toLowerCase() }
    var usageMessage = "usage: nanoscope $subcommandOptions\n"
    Subcommand.values().forEach {
        usageMessage += "  ${it.usage}\n"
    }

    val subcommandString = args[0]
    if (subcommandString in listOf("-h", "--help")) {
        println(usageMessage)
        exitProcess(0)
    }

    try {
        Subcommand.valueOf(subcommandString.toUpperCase())
    } catch (e: IllegalArgumentException) {
        println("Subcommand must be one of $subcommandOptions. Found: $subcommandString")
        println()
        println(usageMessage)
        exitProcess(1)
    }.handle(args.drop(1))
}