package dev.woge.development.gradle

import dev.woge.development.DevelopmentChange
import dev.woge.development.DevelopmentChangeKind
import dev.woge.development.DevelopmentSourcePath
import dev.woge.development.ExperimentalWogeDevelopmentApi
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * Finds edited, added and deleted files by comparing small directory snapshots.
 *
 * Polling works the same on macOS, Linux and Windows and needs no native file-watching library.
 * Woge only scans the application's source folders and build files, not `build` or `node_modules`.
 */
@ExperimentalWogeDevelopmentApi
public class SourceWatcher(
    private val projectDirectory: Path,
    private val roots: List<Path>,
    private val buildFiles: List<Path> = emptyList(),
) {
    private data class Stamp(
        val modified: Long,
        val size: Long,
    )

    private var previous: Map<Path, Stamp> = scan()

    /** Returns the changes since the last call. */
    public fun poll(): Set<DevelopmentChange> {
        val current = scan()
        val changed = (current.keys + previous.keys).filter { current[it] != previous[it] }
        previous = current
        return changed.mapTo(linkedSetOf(), ::change)
    }

    private fun scan(): Map<Path, Stamp> {
        val files = HashMap<Path, Stamp>()
        buildFiles.forEach { file -> stamp(file)?.let { files[file] = it } }
        roots.filter { it.isDirectory() }.forEach { root -> scanRoot(root, files) }
        return files
    }

    private fun scanRoot(
        root: Path,
        files: MutableMap<Path, Stamp>,
    ) {
        try {
            Files.walk(root).use { paths ->
                paths
                    .filter { path -> root.relativize(path).none(::isIgnored) && Files.isRegularFile(path) }
                    .forEach { file -> record(file, files) }
            }
        } catch (expected: IOException) {
            // A folder was deleted while scanning; the next poll sees the final state.
        } catch (expected: java.io.UncheckedIOException) {
            // Same as above, raised by the lazy directory stream.
        }
    }

    private fun record(
        file: Path,
        files: MutableMap<Path, Stamp>,
    ) {
        stamp(file)?.let { files[file] = it }
    }

    private fun isIgnored(segment: Path): Boolean = segment.name in IGNORED_DIRECTORIES || segment.name.startsWith('.')

    private fun stamp(file: Path): Stamp? =
        try {
            val attributes = Files.readAttributes(file, BasicFileAttributes::class.java)
            Stamp(attributes.lastModifiedTime().toMillis(), attributes.size())
        } catch (expected: IOException) {
            null
        }

    private fun change(file: Path): DevelopmentChange {
        val kind =
            when {
                file in buildFiles -> DevelopmentChangeKind.BUILD_CONFIGURATION
                file.name.endsWith(".kt") || file.name.endsWith(".java") -> DevelopmentChangeKind.KOTLIN_SOURCE
                file.name.endsWith(".css") -> DevelopmentChangeKind.CSS
                else -> DevelopmentChangeKind.UNKNOWN
            }
        val relative =
            runCatching {
                projectDirectory
                    .toAbsolutePath()
                    .normalize()
                    .relativize(file.toAbsolutePath().normalize())
                    .toString()
                    .replace('\\', '/')
            }.getOrNull()
        val path =
            relative
                ?.takeUnless {
                    it.startsWith("..")
                }?.let { runCatching { DevelopmentSourcePath.of(it) }.getOrNull() }
        return DevelopmentChange(kind, path)
    }

    private companion object {
        val IGNORED_DIRECTORIES = setOf("build", "node_modules", "out")
    }
}
