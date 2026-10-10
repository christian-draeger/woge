package dev.woge.spike.vite

// Tailwind reads this file as text; the classes are complete static tokens.
fun taskCardClasses(urgent: Boolean): String = when (urgent) {
    true -> "rounded-lg border border-red-500 p-4"
    false -> "rounded-lg border border-slate-300 p-4"
}
