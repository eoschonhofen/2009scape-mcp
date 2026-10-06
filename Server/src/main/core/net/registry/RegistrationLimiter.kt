package core.net.registry

/**
 * AIO-04 — sliding-window rate limits for in-client registration, per IP.
 *
 * Pure apart from the injected [clock], so the windows can be tested without
 * sleeping. There are three windows: successful creations per hour (recorded
 * with [recordCreate], so duplicate names and refused passwords never spend the
 * hourly budget), name checks per minute, and bare creation *attempts* per
 * minute, which every call to [allowCreate] spends because each attempt costs
 * the server an RSA private-key decryption.
 */
class RegistrationLimiter @JvmOverloads constructor(
    private val perHour: () -> Int,
    private val nameChecksPerMinute: () -> Int,
    private val attemptsPerMinute: () -> Int = { 20 },
    private val clock: () -> Long = System::currentTimeMillis
) {
    private companion object {
        const val HOUR_MILLIS = 60L * 60L * 1000L
        const val MINUTE_MILLIS = 60L * 1000L
    }

    private val creations = HashMap<String, ArrayDeque<Long>>()
    private val nameChecks = HashMap<String, ArrayDeque<Long>>()
    private val attempts = HashMap<String, ArrayDeque<Long>>()
    private var lastSweep = clock()

    /**
     * True when this IP may attempt another creation right now. Does not spend the
     * hourly budget, but it does spend one attempt: an attempt costs the server an
     * RSA private-key decryption, so failures cannot be free.
     */
    @Synchronized
    fun allowCreate(ip: String): Boolean {
        val now = clock()
        maybeSweep(now)
        if (window(creations, ip, now, HOUR_MILLIS).size >= perHour()) {
            return false
        }
        val tries = window(attempts, ip, now, MINUTE_MILLIS)
        if (tries.size >= attemptsPerMinute()) {
            return false
        }
        tries.addLast(now)
        return true
    }

    /** Spends one creation slot. Call this only after the account was stored. */
    @Synchronized
    fun recordCreate(ip: String) {
        val now = clock()
        window(creations, ip, now, HOUR_MILLIS).addLast(now)
    }

    /** True when this IP may run another name-availability check; a check consumes one. */
    @Synchronized
    fun allowNameCheck(ip: String): Boolean {
        val now = clock()
        maybeSweep(now)
        val times = window(nameChecks, ip, now, MINUTE_MILLIS)
        if (times.size >= nameChecksPerMinute()) {
            return false
        }
        times.addLast(now)
        return true
    }

    /** Drops every expired entry. Runs automatically about once a minute. */
    @Synchronized
    fun sweep() {
        purgeAll(clock())
    }

    /** How many distinct IPs are currently tracked; for tests and diagnostics. */
    @Synchronized
    fun trackedIpCount(): Int = (creations.keys + nameChecks.keys + attempts.keys).size

    private fun maybeSweep(now: Long) {
        if (now - lastSweep >= MINUTE_MILLIS) {
            purgeAll(now)
        }
    }

    private fun purgeAll(now: Long) {
        purge(creations, now, HOUR_MILLIS)
        purge(nameChecks, now, MINUTE_MILLIS)
        purge(attempts, now, MINUTE_MILLIS)
        lastSweep = now
    }

    private fun window(map: HashMap<String, ArrayDeque<Long>>, ip: String, now: Long, span: Long): ArrayDeque<Long> {
        val times = map.getOrPut(ip) { ArrayDeque() }
        while (times.isNotEmpty() && now - times.first() >= span) {
            times.removeFirst()
        }
        return times
    }

    private fun purge(map: HashMap<String, ArrayDeque<Long>>, now: Long, span: Long) {
        val iterator = map.entries.iterator()
        while (iterator.hasNext()) {
            val times = iterator.next().value
            while (times.isNotEmpty() && now - times.first() >= span) {
                times.removeFirst()
            }
            if (times.isEmpty()) {
                iterator.remove()
            }
        }
    }
}
