/**
 * Copyright (c) 2018 Uber Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uber.nanoscope

import java.io.File
import kotlin.reflect.KClass
import kotlin.system.exitProcess

val ROM_VERSION = Version(0, 2, 4)
val EMULATOR_URL = "https://github.com/uber/nanoscope-art/releases/download/$ROM_VERSION/nanoscope-emulator-$ROM_VERSION.zip"
val ROM_URL = "https://github.com/uber/nanoscope-art/releases/download/$ROM_VERSION/nanoscope-rom-$ROM_VERSION.zip"
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
    EMULATOR(EmulatorHandler::class, "Launches a Nanoscope emulator."),
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
    } catch (e: AdbNoDevicesFoundError) {
        println("No adb-connected devices found.")
        exitProcess(1)
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

    final override fun run() {
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

    val usage = "usage: nanoscope start [--package=com.example] [--ext[=perf_timer|=cpu_timer]]"
    val max_params = 2;

    override fun doRun() {
        val extOption = getExtOption();
        val trace = Nanoscope.startTracing(getPackageName(), extOption)
        println("Tracing" + if (extOption == null) { "" } else { " with " + extOption } + " ... (Press ENTER to stop)")
        while (true) {
            if (System.`in`.read() == 10) {
                break
            }
        }
        trace.stop()
    }

    private fun validParams(): Boolean {
        if (args.isEmpty()) {
            return false
        }

        if (args.size > max_params) {
            println(usage)
            exitProcess(1)
        }
        
        return true;
    }

    private fun getPackageName(): String? {
        if (!validParams()) {
            return null
        }

        for (i in 0..args.size-1) {
            val parts = args[i].split('=')
            if (parts.size == 2 && parts[0] == "--package") {
               return parts[1]
            } // else continue

        }
        return null;
    }

    private fun getExtOption(): String? {
        if (!validParams()) {
            return null
        }

        for (i in 0..args.size-1) {
            if (args[i] == "--ext") {
               return "perf_timer";
            } else {
              val parts = args[i].split('=')
              if (parts.size != 2) {
                  continue;
              }

              if (parts[0] == "--ext") {
                if (parts[1] != "perf_timer" && parts[1] != "cpu_timer") {
                   println(usage)
                   exitProcess(1)
                } else {
                  return parts[1]
                }
              } // else continue
           }
        }
        return null;
    }

}

/**
 * Handler for "nanoscope emulator" subcommand.
 */
class EmulatorHandler(private val args: List<String>): Runnable {

    override fun run() {
        try {
            Nanoscope.launchEmulator(EMULATOR_URL)
        } catch (e: FlashException) {
            println(e.message)
            exitProcess(1)
        }
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
class OpenHandler(private val args: List<String>): Runnable {

    override fun run() {
        val usage = "usage: nanoscope open <tracefile> [--sample-data=example.txt] [--state-data=example2.txt]"
        if (args.isEmpty()) {
            println(usage);
            exitProcess(1)
        }

        val inFile = File(args[0])
        if (!inFile.exists()) {
            println("File does not exist: $inFile")
            exitProcess(1)
        }

        var sampleFile = File(args[0] + ".timer")
        var stateFile = File(args[0] + ".state")
        for ((index, arg) in args.withIndex()){
            if(index != 0){
                val parts = arg.split('=')
                if (parts.size != 2) {
                    println(usage)
                    exitProcess(1)
                }

                if (parts[0] == "--sample-data") {
                    sampleFile = File(parts[1]);
                    if (!sampleFile.exists()) {
                        println("File does not exist: $sampleFile")
                        exitProcess(1)
                    }
                } else if (parts[0] == "--state-data"){
                    stateFile = File(parts[1]);
                    if (!stateFile.exists()) {
                        println("File does not exist: $stateFile")
                        exitProcess(1)
                    }
                } else {
                    println(usage)
                    exitProcess(1)
                }
            }
        }

        Nanoscope.openTrace(inFile, sampleFile, stateFile)
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
