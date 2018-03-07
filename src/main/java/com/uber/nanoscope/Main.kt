package com.uber.nanoscope

import kotlin.reflect.KClass
import kotlin.system.exitProcess

/**
 * Represents available subcommands.
 */
enum class Subcommand(
        private val handlerClass: KClass<out Runnable>,
        private val help: String) {
    START(StartHandler::class, "Starts tracing on adb-connected device."),
    FLASH(FlashHandler::class, "Flashes adb-connected device with nanoscope image.");

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
        println("flash called: $args")
    }
}

/**
 * Entrypoint for "nanoscope" command.
 *
 * usage: nanoscope [start, flash]
 *   start: Starts tracing on adb-connected device.
 *   flash: Flashes adb-connected device with nanoscope image.
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