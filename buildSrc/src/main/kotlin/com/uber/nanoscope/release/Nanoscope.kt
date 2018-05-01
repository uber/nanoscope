@file:JvmName("Nanoscope")
@file:Suppress("unused")

package com.uber.nanoscope.release

import com.github.kittinunf.fuel.httpDelete
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.moshi.responseObject
import org.gradle.api.InvalidUserDataException
import java.io.File
import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter

fun release(distZip: File, incrementType: IncrementType) {
    ensureGitHubToken()
    val homebrewRepo = HomebrewRepo.init()
    homebrewRepo.ensureClean()

    val version = homebrewRepo.readVersion().increment(incrementType)

    val release = GitHubPublisher(distZip, version).publish()
    val sha256 = DatatypeConverter.printHexBinary(
        MessageDigest.getInstance("SHA-256").digest(release.bytes)).toLowerCase()
    val downloadUrl = release.downloadUrl

    homebrewRepo.update(version, downloadUrl, sha256)
    homebrewRepo.commit("Update version to $version.")
    homebrewRepo.push()
}

fun deleteReleaseDrafts() {
    ensureGitHubToken()
    "https://api.github.com/repos/uber/nanoscope/releases"
            .httpGet()
            .authGitHub()
            .responseObject<List<Map<String, Any>>>().third.get()
            .filter { it["draft"] == true }
            .forEach {
                it["url"].toString()
                        .httpDelete()
                        .authGitHub()
                        .response()
            }
}

private fun ensureGitHubToken() {
    if (GITHUB_TOKEN.isNullOrEmpty()) {
        throw InvalidUserDataException("GITHUB_ACCESS_TOKEN not set.")
    }
}
