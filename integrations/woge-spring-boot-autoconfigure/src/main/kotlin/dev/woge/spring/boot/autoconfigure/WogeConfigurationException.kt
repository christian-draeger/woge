package dev.woge.spring.boot.autoconfigure

internal class WogeConfigurationException(
    message: String,
) : IllegalStateException(message)

internal fun requireWogeConfiguration(
    condition: Boolean,
    message: () -> String,
) {
    if (!condition) throw WogeConfigurationException(message())
}
