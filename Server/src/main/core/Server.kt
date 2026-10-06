package core

import core.api.log
import core.auth.AgentToken
import core.auth.Auth
import core.game.system.SystemManager
import core.game.system.SystemState
import core.game.system.config.ServerConfigParser
import core.game.world.GameWorld
import core.game.world.repository.Repository
import core.net.NioReactor
import core.net.websocket.GameWebSocketServer
import core.net.websocket.WebSocketTls
import core.tools.Log
import core.tools.NetworkReachability
import core.tools.TimeStamp
import kotlinx.coroutines.*
import java.io.File
import java.io.FileWriter
import java.lang.management.ManagementFactory
import java.lang.management.ThreadMXBean
import java.net.BindException
import java.net.URL
import java.util.*
import kotlin.math.max
import kotlin.system.exitProcess


/**
 * The main class, for those that are unable to read the class' name.
 * @author Emperor
 * @author Ceikry
 */
object Server {
    /**
     * The time stamp of when the server started running.
     */
    @JvmField
    var startTime: Long = 0

    var lastHeartbeat = System.currentTimeMillis()

    @JvmStatic
    var running = false

    /**
     * The NIO reactor.
     */
    @JvmStatic
    var reactor: NioReactor? = null

    @JvmStatic
    var webSocketServer: GameWebSocketServer? = null

    var networkReachability = NetworkReachability.Reachable

    /**
     * The main method, in this method we load background utilities such as
     * cache and our world, then end with starting networking.
     * @param args The arguments cast on runtime.
     * @throws Throwable When an exception occurs.
     */
    @Throws(Throwable::class)
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isNotEmpty()) {
            log(this::class.java, Log.INFO, "Using config file: ${args[0]}")
            ServerConfigParser.parse(args[0])
        } else {
            log(this::class.java, Log.INFO, "Using config file: ${"worldprops" + File.separator + "default.conf"}")
            ServerConfigParser.parse("worldprops" + File.separator + "default.conf")
        }
        startTime = System.currentTimeMillis()
        val t = TimeStamp()
        GameWorld.prompt(true)
        Runtime.getRuntime().addShutdownHook(ServerConstants.SHUTDOWN_HOOK)
        log(this::class.java, Log.INFO, "Starting networking...")
        try {
            reactor = NioReactor.configure(43594 + GameWorld.settings?.worldId!!)
            reactor!!.start()
            if (ServerConstants.WEBSOCKET_ENABLED) {
                val websocketPort = if (ServerConstants.WEBSOCKET_PORT > 0) {
                    ServerConstants.WEBSOCKET_PORT
                } else {
                    53594 + GameWorld.settings?.worldId!!
                }
                webSocketServer = GameWebSocketServer(websocketPort, 1)
                WebSocketTls.configure(webSocketServer!!)
                webSocketServer!!.start()
            }
        } catch (e: BindException) {
            log(this::class.java, Log.ERR, "Port " + (43594 + GameWorld.settings?.worldId!!) + " is already in use!")
            throw e
        }
        //WorldCommunicator.connect()
        log(this::class.java, Log.INFO, GameWorld.settings?.name + " flags " + GameWorld.settings?.toString())
        log(this::class.java, Log.INFO, GameWorld.settings?.name + " started in " + t.duration(false, "") + " milliseconds.")
        val scanner = Scanner(System.`in`)

        running = true
        GlobalScope.launch {
            while(scanner.hasNextLine()){
                when(val command = ConsoleCommand.parse(scanner.nextLine())){
                    ConsoleCommand.Stop -> exitProcess(0)
                    ConsoleCommand.Update -> SystemManager.flag(SystemState.UPDATING)
                    ConsoleCommand.Help -> printCommands()
                    ConsoleCommand.RestartWorker -> SystemManager.flag(SystemState.ACTIVE)
                    is ConsoleCommand.ResetToken -> resetToken(command.name)
                    // Blank lines and unknown commands are ignored, as before.
                    ConsoleCommand.Unknown -> {}
                }
            }
        }

        if (ServerConstants.WATCHDOG_ENABLED) {
            GlobalScope.launch {
                delay(20000)
                while (running) {
                    val timeStart = System.currentTimeMillis()
                    if (!checkConnectivity())
                        networkReachability = NetworkReachability.Unreachable
                    else
                        networkReachability = NetworkReachability.Reachable
                    if (System.currentTimeMillis() - lastHeartbeat > 7200 && running) {
                        log(this::class.java, Log.ERR, "Triggering reboot due to heartbeat timeout")
                        log(this::class.java, Log.ERR, "Creating thread dump...")
                        val dump = threadDump(true, true)

                        withContext(Dispatchers.IO) {
                            FileWriter("latestdump.txt").use {

                                if (dump != null) {
                                    it.write(dump)
                                }

                                it.flush()
                                it.close()
                            }
                        }

                        if (!SystemManager.isTerminated())
                            exitProcess(0)
                    }
                    val timeNow = System.currentTimeMillis()
                    delay(max(0L, 625 - (timeNow - timeStart)))
                }
            }
        }
    }

    private fun checkConnectivity(): Boolean    {
        //Has to be done this way because you can't actually ping in Java unless you run the whole thing as root
        val urls = ServerConstants.CONNECTIVITY_CHECK_URL.split(",")
        var timeout = ServerConstants.CONNECTIVITY_TIMEOUT
        if (timeout * urls.size > 5000) //Limit timeout down to 5000ms so other watchdog functions continue as expected.
            timeout = 5000 / urls.size
        for (targetUrl in urls) {
            try {
                val url = URL(targetUrl)
                val conn = url.openConnection()
                conn.connectTimeout = timeout
                conn.connect()
                conn.getInputStream().close()
                return true
            } catch (e: Exception) {
                log(this::class.java, Log.WARN, "${targetUrl} failed to respond. Are we offline?")
                continue
            }
        }
        return false
    }

    @JvmStatic
    fun heartbeat() {
        lastHeartbeat = System.currentTimeMillis()
    }

    fun printCommands(){
        println("stop - stop the server (saves all accounts and such)")
        println("players - show online player count")
        println("update - initiate an update with a countdown visible to players")
        println("help, commands - show this")
        println("restartworker - Reboots the major update worker in case of a travesty.")
        println("resettoken <name> - issue a new agent token for an account, kicking it if online")
    }

    /**
     * AIO-05 — rotate an agent's token from the console.
     *
     * The new token is printed to this console only: it must not go through
     * [core.api.log], because `write_logs = true` persists log lines to disk.
     */
    private fun resetToken(name: String?) {
        if (name == null) {
            println("usage: resettoken <name>")
            return
        }
        if (!ServerConstants.USE_AUTH) {
            println("auth disabled, tokens unused")
            return
        }
        if (!Auth.storageProvider.checkUsernameTaken(name)) {
            println("no such account")
            return
        }
        val token = AgentToken.generate()
        GameWorld.authenticator.updatePassword(name, token)
        val online = Repository.getPlayerByName(name)
        if (online != null) {
            // A leaked-token session ends now rather than at the next restart.
            online.session.disconnect()
        }
        println("token for $name: $token")
    }

    fun autoReconnect() {
        /*SystemLogger.log("Attempting autoreconnect of server")
        WorldCommunicator.connect()*/
    }
    /**
     * Gets the startTime.
     * @return the startTime
     */
    fun getStartTime(): Long {
        return startTime
    }

    private fun threadDump(lockedMonitors: Boolean, lockedSynchronizers: Boolean): String? {
        val threadDump = StringBuffer(System.lineSeparator())
        val threadMXBean: ThreadMXBean = ManagementFactory.getThreadMXBean()
        for (threadInfo in threadMXBean.dumpAllThreads(lockedMonitors, lockedSynchronizers)) {
            threadDump.append(threadInfo.toString())
        }
        return threadDump.toString()
    }

    /**
     * Sets the bastartTime.ZZ
     * @param startTime the startTime to set.
     */
    fun setStartTime(startTime: Long) {
        Server.startTime = startTime
    }
}
