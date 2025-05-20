package io.amplicode.connekt.utils

import java.nio.file.Path

fun uniqueFilePath(parentDir: Path, originalFileName: String): Path {
    var counter = 0
    var fileName = originalFileName
    var filePath = parentDir.resolve(fileName)

    while (filePath.toFile().exists()) {
        counter++
        val lastDotIndex = originalFileName.lastIndexOf('.')
        if (lastDotIndex != -1) {
            val name = originalFileName.substring(0, lastDotIndex)
            val extension = originalFileName.substring(lastDotIndex)
            fileName = "$name-$counter$extension"
        } else {
            fileName = "$originalFileName-$counter"
        }
        filePath = parentDir.resolve(fileName)
    }

    return filePath
}