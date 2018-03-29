package com.uber.nanoscope

import java.io.File
import kotlin.reflect.KClass
import kotlin.system.exitProcess

val ROM_VERSION = Version(0, 2, 0)
val ROM_URL = "https://s3-us-west-2.amazonaws.com/uber-common-public/nanoscope/nanoscope-rom-$ROM_VERSION.zip"
val FLASH_WARNING_MESSAGE = """ |
                                |###################################################
                                |# WARNING: This will wipe all of your phone data! #
                                |###################################################""".trimMargin()

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

private fun ensureCompatibility() {
    try {
        Nanoscope.checkVersion(ROM_VERSION)
    } catch (e: IncompatibleVersionError) {
        val reason = if (e.romVersion == null) {
            """The OS running on your device is not supported. In order to install the Nanoscope ROM, run the following:
                |
                $FLASH_WARNING_MESSAGE
                |
                |    $ nanoscope flash""".trimMargin()
        } else {
            val r = e.romVersion.compareTo(e.supportedRomVersion)
            if (r < 0) {
                """Your Nanoscope ROM is out of date and incompatible with your client:
                    |    ROM Version: ${e.romVersion}
                    |    Supported Version: ${e.supportedRomVersion}
                    |
                    |To update your ROM, run the following:
                    |    $ brew update && brew upgrade nanoscope
                    |    $ nanoscope flash
                    """.trimMargin()
            } else {
                """Your Nanoscope client is out of date and incompatible with your ROM:
                    |    ROM Version: ${e.romVersion}
                    |    Supported Version: ${e.supportedRomVersion}
                    |
                    |To update your client, run the following:
                    |    $ brew update && brew upgrade nanoscope
                    """.trimMargin()
            }
        }

         println(reason)
         exitProcess(1)
    }
}

private fun acceptConfirmation(warningMessage: String?): Boolean {
    println("$warningMessage\n")

    print("Are you sure you want to continue? [y/N]: ")

    val response = readLine()!!.trim().toLowerCase()

    return when (response) {
        "y", "yes" -> true
        else -> false
    }
}

abstract class VersionedHandler: Runnable {

    override final fun run() {
        ensureCompatibility()
        doRun()
    }

    abstract fun doRun()
}

/**
 * Abstraction for handlers that show a warning/confirmation message
 */
abstract class ConfirmationHandler: Runnable {
    override fun run() {
        if (acceptConfirmation(getWarningMessage())) {
            doRun()
        }
    }

    abstract fun getWarningMessage(): String?

    abstract fun doRun()
}
/**
 * Handler for "nanoscope start" subcommand.
 */
class StartHandler(private val args: List<String>): VersionedHandler() {

    override fun doRun() {
        val trace = Nanoscope.startTracing(getPackageName())
        println("Tracing... (Press ENTER to stop)")
        while (true) {
            if (System.`in`.read() == 10) {
                break
            }
        }
        trace.stop()
    }

    private fun getPackageName(): String? {
        if (args.isEmpty()) {
            return null
        }

        val usage = "usage: nanoscope start [--package=com.example]"
        if (args.size != 1) {
            println(usage)
            exitProcess(1)
        }

        val parts = args[0].split('=')
        if (parts.size != 2) {
            println(usage)
            exitProcess(1)
        }

        if (parts[0] != "--package") {
            println(usage)
            exitProcess(1)
        }

        return parts[1]
    }
}

/**
 * Handler for "nanoscope flash" subcommand.
 */
class FlashHandler(private val args: List<String>): ConfirmationHandler() {
    override fun doRun() {
        try {
            Nanoscope.flashDevice(ROM_URL)
        } catch (e: FlashException) {
            println(e.message)
            exitProcess(1)
        }
    }

    override fun getWarningMessage(): String? {
        return FLASH_WARNING_MESSAGE
    }
}

/**
 * Handler for "nanoscope open" subcommand.
 */
class OpenHandler(private val args: List<String>): VersionedHandler() {

    override fun doRun() {
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
    val subcommandOptions = Subcommand.values().map { it.name.toLowerCase() }
    var usageMessage = "usage: nanoscope $subcommandOptions\n"
    Subcommand.values().forEach {
        usageMessage += "  ${it.usage}\n"
    }

    if (args.isEmpty()) {
        println(usageMessage)
        exitProcess(1)
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