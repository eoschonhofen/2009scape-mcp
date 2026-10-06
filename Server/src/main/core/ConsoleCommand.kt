package core

/**
 * AIO-05 — one line typed into the server console.
 *
 * Pure, so the parsing and the account-name normalization can be tested without
 * starting the server. The stdin loop in [Server] turns a line into one of
 * these and acts on it.
 */
sealed class ConsoleCommand {
    object Stop : ConsoleCommand()
    object Update : ConsoleCommand()
    object Help : ConsoleCommand()
    object RestartWorker : ConsoleCommand()

    /** `resettoken [name]`; [name] is normalized, or null when the argument is missing. */
    data class ResetToken(val name: String?) : ConsoleCommand()

    object Unknown : ConsoleCommand()

    companion object {
        private val WHITESPACE = Regex("\\s+")

        fun parse(line: String): ConsoleCommand {
            val parts = line.trim().split(WHITESPACE)
            return when (parts.firstOrNull()?.lowercase()) {
                "stop" -> Stop
                "update" -> Update
                "help", "commands" -> Help
                "restartworker" -> RestartWorker
                "resettoken" -> {
                    // A username may contain spaces, so everything after the command is the name.
                    val raw = parts.drop(1).joinToString(" ").trim()
                    ResetToken(if (raw.isEmpty()) null else normalizeName(raw))
                }
                else -> Unknown
            }
        }

        /** The same normalization the registration path applies to a username. */
        fun normalizeName(raw: String): String =
            raw.replace(" ", "_").replace("|", "").lowercase()
    }
}
