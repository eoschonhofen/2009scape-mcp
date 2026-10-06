package core

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/** AIO-05 — console parsing and name normalization. */
class ConsoleCommandTest {

    @Test
    fun shouldKeepTheExistingCommands() {
        Assertions.assertEquals(ConsoleCommand.Stop, ConsoleCommand.parse("stop"))
        Assertions.assertEquals(ConsoleCommand.Update, ConsoleCommand.parse("update"))
        Assertions.assertEquals(ConsoleCommand.Help, ConsoleCommand.parse("help"))
        Assertions.assertEquals(ConsoleCommand.Help, ConsoleCommand.parse("commands"))
        Assertions.assertEquals(ConsoleCommand.RestartWorker, ConsoleCommand.parse("restartworker"))
    }

    @Test
    fun shouldNormalizeTheResetTokenName() {
        Assertions.assertEquals(ConsoleCommand.ResetToken("bob"), ConsoleCommand.parse("resettoken Bob"))
        Assertions.assertEquals(ConsoleCommand.ResetToken("bob_smith"), ConsoleCommand.parse("resettoken Bob Smith"))
        Assertions.assertEquals(ConsoleCommand.ResetToken("bob"), ConsoleCommand.parse("  resettoken   bob  "))
        Assertions.assertEquals(ConsoleCommand.ResetToken("bob"), ConsoleCommand.parse("resettoken b|ob"))
    }

    @Test
    fun shouldReportAMissingResetTokenArgument() {
        Assertions.assertEquals(ConsoleCommand.ResetToken(null), ConsoleCommand.parse("resettoken"))
    }

    @Test
    fun shouldIgnoreUnknownAndBlankLines() {
        Assertions.assertEquals(ConsoleCommand.Unknown, ConsoleCommand.parse(""))
        Assertions.assertEquals(ConsoleCommand.Unknown, ConsoleCommand.parse("   "))
        Assertions.assertEquals(ConsoleCommand.Unknown, ConsoleCommand.parse("ban bob"))
    }
}
