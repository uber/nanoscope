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

class Adb {

    companion object {

        fun getForegroundPackage(): String {
            return "dumpsys activity activities"
                    .adbShell()
                    .inputStream
                    .bufferedReader()
                    .useLines { lines ->
                        val line = lines.first { "mFocusedActivity" in it || "ResumedActivity" in it }.trim()
                        val component = line.split(' ')[3]
                        val packageName = component.split('/')[0]
                        packageName
                    }
        }

        fun setSystemProperty(name: String, value: String): Int {
            return "setprop $name $value".adbShell().waitFor()
        }

        fun pullFile(remotePath: String, localPath: String): Int {
            val process = "adb pull $remotePath $localPath".run()
            process.inputStream.bufferedReader().forEachLine {  }
            return process.waitFor()
        }

        fun fileExists(path: String): Boolean {
            val output = "[ ! -e \"$path\" ]; echo $?".adbShell().inputStream.bufferedReader().readText().trim()
            return output == "1"
        }

        fun lineCount(path: String): Int? {
            val output = "wc -l < $path".adbShell().inputStream.bufferedReader().readText().trim()
            return output.toIntOrNull()
        }

        fun root() {
            "adb root".run().waitFor()
        }

        fun getDeviceHardware(): String {
            return "getprop ro.hardware".adbShell().inputStream.bufferedReader().readText().trim()
        }

        fun getROMVersion(): String {
            val proc = "adb shell getprop ro.build.nanoscope".run()
            val errOutput = proc.errorStream.bufferedReader().readText().trim()
            if ("no devices/emulators found" in errOutput) {
                throw AdbNoDevicesFoundError()
            }
            return proc.inputStream.bufferedReader().readText().trim()
        }
    }
}

private fun String.adbShell(): Process {
    return "adb shell $this".run()
}

private fun String.run(): Process {
    return Runtime.getRuntime().exec(this)
}

class AdbNoDevicesFoundError: RuntimeException()