package dev.woge.gradle;

/** Which Tailwind CLI {@code wogeTailwind} runs. */
public enum TailwindExecutor {
    /** The project's own {@code node_modules/.bin/tailwindcss}, installed from its lockfile. */
    NPM,
    /** The official standalone executable, downloaded once and checked against a pinned SHA-256. */
    STANDALONE
}
