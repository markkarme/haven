package com.haven.haven

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * DNS-only local VPN. Only DNS traffic is routed into the tunnel; every other packet
 * keeps using the normal network. Blocked domains get NXDOMAIN, others are forwarded.
 */
class HavenVpnService : VpnService() {

    private var tunnel: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private var stopSignal: FileDescriptor? = null
    private var forwarder: ExecutorService? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile private var rules: Set<String> = emptySet()
    @Volatile private var systemDnsServers: List<InetAddress> = emptyList()

    private val lastNotified = HashMap<String, Long>()
    private var lastNotificationAt = 0L

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == BlockerPrefs.KEY_VPN_EXCLUDED) {
                restartTunnel()
            } else {
                reloadRules()
            }
        }

    override fun onCreate() {
        super.onCreate()
        BlockerPrefs.registerListener(this, prefsListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !BlockerPrefs.isProtectionEnabled(this)) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!startTunnel()) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onRevoke() {
        val stillWanted = BlockerPrefs.isProtectionEnabled(this)
        shutdown()
        if (stillWanted) notifyStopped()
        super.onRevoke()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (BlockerPrefs.isProtectionEnabled(this) || BlockerPrefs.shouldGuardAppInfo(this)) {
            ProtectionController.scheduleRestart(this, 400)
            ProtectionController.startGuard(this)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        BlockerPrefs.unregisterListener(this, prefsListener)
        shutdown()
        if (BlockerPrefs.isProtectionEnabled(this) || BlockerPrefs.shouldGuardAppInfo(this)) {
            ProtectionController.scheduleRestart(this, 400)
        }
        super.onDestroy()
    }

    private fun startTunnel(): Boolean {
        if (tunnel != null) return true
        reloadRules()
        trackSystemDns()

        val builder = Builder()
            .setSession(getString(R.string.vpn_session_name))
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(VPN_DNS)
            .addRoute(VPN_DNS, 32)
            .setBlocking(true)
            .setConfigureIntent(openAppIntent())
        // Excluded apps never see the VPN, so apps that refuse to run behind one keep working.
        (BlockerPrefs.getVpnExcludedPackages(this) + packageName).forEach { pkg ->
            try {
                builder.addDisallowedApplication(pkg)
            } catch (_: Exception) {
                // Not installed.
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        val fd = try {
            builder.establish()
        } catch (_: Exception) {
            null
        } ?: return false

        tunnel = fd
        promoteToForeground()

        val pipe = Os.pipe()
        stopSignal = pipe[1]
        forwarder = Executors.newFixedThreadPool(4)
        worker = Thread({ runLoop(fd, pipe[0]) }, "haven-vpn").also { it.start() }
        ProtectionController.startGuard(this)
        ProtectionController.scheduleRestart(this)
        running = true
        return true
    }

    private fun restartTunnel() {
        if (tunnel == null) return
        shutdown()
        if (!startTunnel()) stopSelf()
    }

    private fun shutdown() {
        running = false
        stopSignal?.let {
            try {
                Os.close(it)
            } catch (_: ErrnoException) {
            }
        }
        stopSignal = null
        worker?.join(1000)
        worker = null
        forwarder?.shutdownNow()
        forwarder = null
        try {
            tunnel?.close()
        } catch (_: IOException) {
        }
        tunnel = null
        networkCallback?.let {
            try {
                connectivity().unregisterNetworkCallback(it)
            } catch (_: Exception) {
            }
        }
        networkCallback = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun runLoop(fd: ParcelFileDescriptor, stopRead: FileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buffer = ByteArray(32767)
        val tunPoll = StructPollfd().apply {
            this.fd = fd.fileDescriptor
            events = OsConstants.POLLIN.toShort()
        }
        val stopPoll = StructPollfd().apply {
            this.fd = stopRead
            events = OsConstants.POLLIN.toShort()
        }

        try {
            while (true) {
                try {
                    Os.poll(arrayOf(tunPoll, stopPoll), -1)
                } catch (e: ErrnoException) {
                    if (e.errno == OsConstants.EINTR) continue
                    break
                }
                if (stopPoll.revents.toInt() != 0) break
                if (tunPoll.revents.toInt() and OsConstants.POLLIN == 0) continue

                val length = try {
                    input.read(buffer)
                } catch (_: IOException) {
                    break
                }
                if (length < 0) break
                val query = DnsPacket.parse(buffer, length) ?: continue
                handleQuery(query, output)
            }
        } finally {
            try {
                Os.close(stopRead)
            } catch (_: ErrnoException) {
            }
        }
    }

    private fun handleQuery(query: DnsPacket.Query, output: FileOutputStream) {
        val host = query.hostname
        if (host != null && isBlocked(host)) {
            val answer = DnsPacket.nxDomain(query.dns) ?: return
            write(output, DnsPacket.buildReply(query, answer))
            if (host !in ALWAYS_BLOCKED) notifyBlocked(host)
            return
        }

        val pool = forwarder ?: return
        try {
            pool.execute {
                val answer = forward(query.dns) ?: return@execute
                write(output, DnsPacket.buildReply(query, answer))
            }
        } catch (_: Exception) {
            // Pool shut down while stopping.
        }
    }

    private fun isBlocked(host: String): Boolean =
        host in ALWAYS_BLOCKED || DomainMatcher.isBlockedNormalized(host, rules)

    private fun forward(query: ByteArray): ByteArray? {
        for (server in upstreamServers()) {
            try {
                DatagramSocket().use { socket ->
                    protect(socket)
                    socket.soTimeout = UPSTREAM_TIMEOUT_MS
                    socket.send(DatagramPacket(query, query.size, server, 53))
                    val buffer = ByteArray(4096)
                    val response = DatagramPacket(buffer, buffer.size)
                    socket.receive(response)
                    return buffer.copyOf(response.length)
                }
            } catch (_: IOException) {
                // Try the next resolver.
            }
        }
        return null
    }

    /** Family resolver adds a second adult filter; system DNS covers networks that block public DNS. */
    private fun upstreamServers(): List<InetAddress> {
        val public = if (BlockerPrefs.isAdultProtectionEnabled(this)) FAMILY_DNS else PLAIN_DNS
        return public.map { InetAddress.getByName(it) } + systemDnsServers
    }

    private fun write(output: FileOutputStream, packet: ByteArray) {
        try {
            synchronized(output) { output.write(packet) }
        } catch (_: IOException) {
            // Tunnel closed.
        }
    }

    private fun reloadRules() {
        rules = DomainMatcher.normalizeRules(BlockerPrefs.getBlockedDomains(this))
    }

    /** Our own package is excluded from the VPN, so the default network is the real one. */
    private fun trackSystemDns() {
        if (networkCallback != null) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                systemDnsServers = linkProperties.dnsServers
                    .filter { it.hostAddress != VPN_DNS }
            }

            override fun onLost(network: Network) {
                systemDnsServers = emptyList()
            }
        }
        try {
            connectivity().registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (_: Exception) {
        }
    }

    private fun connectivity(): ConnectivityManager =
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private fun promoteToForeground() {
        ensureChannel(this)
        val notification = NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_haven)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_text))
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_STATUS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
            )
        } else {
            startForeground(NOTIFICATION_STATUS, notification)
        }
    }

    private fun notifyBlocked(host: String) {
        val now = SystemClock.elapsedRealtime()
        synchronized(lastNotified) {
            if (now - lastNotificationAt < 3_000) return
            if (now - (lastNotified[host] ?: 0L) < 60_000) return
            lastNotified[host] = now
            lastNotificationAt = now
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_BLOCKED)
            .setSmallIcon(R.drawable.ic_stat_haven)
            .setContentTitle(getString(R.string.site_blocked_title))
            .setContentText(host)
            .setAutoCancel(true)
            .setTimeoutAfter(15_000)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        postNotification(NOTIFICATION_BLOCKED, notification)
    }

    private fun notifyStopped() {
        ensureChannel(this)
        val notification = NotificationCompat.Builder(this, CHANNEL_BLOCKED)
            .setSmallIcon(R.drawable.ic_stat_haven)
            .setContentTitle(getString(R.string.vpn_stopped_title))
            .setContentText(getString(R.string.vpn_stopped_text))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        postNotification(NOTIFICATION_STOPPED, notification)
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(id: Int, notification: android.app.Notification) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Notification permission not granted.
        }
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        private const val ACTION_STOP = "com.haven.haven.vpn.STOP"

        private const val VPN_ADDRESS = "10.111.222.1"
        private const val VPN_DNS = "10.111.222.2"
        private const val UPSTREAM_TIMEOUT_MS = 4000

        private val FAMILY_DNS = listOf("1.1.1.3", "1.0.0.3")
        private val PLAIN_DNS = listOf("1.1.1.1", "1.0.0.1")

        /** Encrypted-DNS endpoints + Firefox's canary domain (NXDOMAIN disables its built-in DoH). */
        private val ALWAYS_BLOCKED = setOf(
            "use-application-dns.net",
            "dns.google",
            "dns.google.com",
            "cloudflare-dns.com",
            "mozilla.cloudflare-dns.com",
            "chrome.cloudflare-dns.com",
            "one.one.one.one",
            "dns.quad9.net",
            "doh.opendns.com",
            "dns.adguard-dns.com",
            "dns.nextdns.io",
        )

        private const val CHANNEL_STATUS = "haven_vpn_status"
        private const val CHANNEL_BLOCKED = "haven_blocked"
        private const val NOTIFICATION_STATUS = 2001
        private const val NOTIFICATION_BLOCKED = 2002
        private const val NOTIFICATION_STOPPED = 2003

        @Volatile
        var running = false
            private set

        fun hasPermission(context: Context): Boolean = prepare(context) == null

        fun start(context: Context) {
            if (!hasPermission(context)) return
            try {
                context.startService(Intent(context, HavenVpnService::class.java))
            } catch (_: Exception) {
                // Background start not allowed right now; boot / next app open retries.
            }
        }

        fun stop(context: Context) {
            if (!running) return
            try {
                context.startService(
                    Intent(context, HavenVpnService::class.java).setAction(ACTION_STOP),
                )
            } catch (_: Exception) {
            }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_STATUS,
                    context.getString(R.string.vpn_channel_status),
                    NotificationManager.IMPORTANCE_MIN,
                ),
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_BLOCKED,
                    context.getString(R.string.vpn_channel_blocked),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }
}
