package core.net.registry

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/** AIO-04 — per-IP sliding windows with a fake clock. */
class RegistrationLimiterTest {
    private var now = 1_000_000L
    private val limiter = RegistrationLimiter({ 3 }, { 2 }, { now })

    @Test
    fun shouldAllowThreeCreationsPerHourPerIp() {
        repeat(3) {
            Assertions.assertTrue(limiter.allowCreate("10.0.0.1"), "creation ${it + 1} should fit")
            limiter.recordCreate("10.0.0.1")
        }
        Assertions.assertFalse(limiter.allowCreate("10.0.0.1"))
    }

    @Test
    fun shouldNotCountFailedAttemptsAgainstTheHourlyLimit() {
        // allowCreate only checks, so repeated attempts never spend the budget.
        repeat(10) { Assertions.assertTrue(limiter.allowCreate("10.0.0.2")) }
        repeat(3) {
            Assertions.assertTrue(limiter.allowCreate("10.0.0.2"))
            limiter.recordCreate("10.0.0.2")
        }
        Assertions.assertFalse(limiter.allowCreate("10.0.0.2"))
    }

    @Test
    fun shouldExpireCreationsOnTheHourBoundary() {
        limiter.recordCreate("10.0.0.3")
        limiter.recordCreate("10.0.0.3")
        limiter.recordCreate("10.0.0.3")
        Assertions.assertFalse(limiter.allowCreate("10.0.0.3"))

        now += 3_600_000L
        Assertions.assertTrue(limiter.allowCreate("10.0.0.3"))
    }

    @Test
    fun shouldIsolateTheLimitPerIp() {
        repeat(3) { limiter.recordCreate("10.0.0.4") }
        Assertions.assertFalse(limiter.allowCreate("10.0.0.4"))
        Assertions.assertTrue(limiter.allowCreate("10.0.0.5"))
    }

    @Test
    fun shouldLimitNameChecksPerMinuteAndConsumeThem() {
        Assertions.assertTrue(limiter.allowNameCheck("10.0.0.6"))
        Assertions.assertTrue(limiter.allowNameCheck("10.0.0.6"))
        Assertions.assertFalse(limiter.allowNameCheck("10.0.0.6"))

        now += 60_000L
        Assertions.assertTrue(limiter.allowNameCheck("10.0.0.6"))
    }

    @Test
    fun shouldPruneExpiredEntries() {
        limiter.recordCreate("10.0.0.7")
        limiter.allowNameCheck("10.0.0.8")
        Assertions.assertEquals(2, limiter.trackedIpCount())

        now += 3_600_000L
        limiter.sweep()
        Assertions.assertEquals(0, limiter.trackedIpCount())
    }
}
