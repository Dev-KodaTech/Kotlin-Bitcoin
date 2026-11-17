package com.kotlinbitcoins.web.e2e.support

import org.assertj.core.api.Assertions.assertThat
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Screenshot comparison against a committed reference (`src/test/resources/snapshots/<name>.png`). With
 * `-PupdateSnapshots=true` the reference is written instead (review it before committing). Two images match when they
 * have the same size and at most [MAX_DIFFERENT_PIXELS_RATIO] of the pixels differ by more than [CHANNEL_TOLERANCE]
 * in any channel (antialiasing of fonts and lines).
 */
object Snapshots {
    private const val MAX_DIFFERENT_PIXELS_RATIO = 0.01
    private const val CHANNEL_TOLERANCE = 16
    private const val BYTE_MASK = 0xFF

    private val update = System.getProperty("e2e.updateSnapshots", "false").toBoolean()
    private val directory = Path.of(System.getProperty("e2e.snapshotsDir", "src/test/resources/snapshots"))

    fun assertMatches(
        name: String,
        png: ByteArray,
    ) {
        val reference = directory.resolve("$name.png")
        if (update) {
            Files.createDirectories(reference.parent)
            Files.write(reference, png)
            return
        }
        check(Files.exists(reference)) { "No reference $reference: run with -PupdateSnapshots=true and review it" }
        val actualFile = Path.of("build/reports/snapshots/$name.actual.png")
        Files.createDirectories(actualFile.parent)
        Files.write(actualFile, png)
        val expected = ImageIO.read(reference.toFile())
        val actual = ImageIO.read(ByteArrayInputStream(png))
        assertThat(actual.width to actual.height)
            .describedAs("size of $name (actual saved at $actualFile)")
            .isEqualTo(expected.width to expected.height)
        var different = 0
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                if (!close(expected.getRGB(x, y), actual.getRGB(x, y))) different++
            }
        }
        val ratio = different.toDouble() / (expected.width * expected.height)
        assertThat(ratio)
            .describedAs("ratio of different pixels of $name (actual saved at $actualFile)")
            .isLessThanOrEqualTo(MAX_DIFFERENT_PIXELS_RATIO)
    }

    private fun close(
        left: Int,
        right: Int,
    ): Boolean =
        (0..3).all { channel ->
            val shift = channel * 8
            abs((left shr shift and BYTE_MASK) - (right shr shift and BYTE_MASK)) <= CHANNEL_TOLERANCE
        }
}
