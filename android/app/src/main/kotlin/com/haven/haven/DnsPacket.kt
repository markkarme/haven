package com.haven.haven

/** Minimal IPv4 / UDP / DNS helpers for the DNS-only local VPN. */
object DnsPacket {
    private const val IP_HEADER = 20
    private const val UDP_HEADER = 8
    private const val DNS_HEADER = 12
    private const val PROTOCOL_UDP = 17

    class Query(
        val packet: ByteArray,
        val ipHeaderLength: Int,
        val dns: ByteArray,
        val hostname: String?,
    )

    /** Returns the DNS query carried by an IPv4 UDP/53 packet, or null for anything else. */
    fun parse(buffer: ByteArray, length: Int): Query? {
        if (length < IP_HEADER + UDP_HEADER + DNS_HEADER) return null
        if ((buffer[0].toInt() shr 4) and 0x0F != 4) return null
        val ihl = (buffer[0].toInt() and 0x0F) * 4
        if (ihl < IP_HEADER || length < ihl + UDP_HEADER + DNS_HEADER) return null
        if (buffer[9].toInt() and 0xFF != PROTOCOL_UDP) return null
        if (u16(buffer, ihl + 2) != 53) return null

        val udpLength = u16(buffer, ihl + 4)
        val dnsEnd = minOf(length, ihl + udpLength)
        val dnsStart = ihl + UDP_HEADER
        if (dnsEnd - dnsStart < DNS_HEADER) return null

        val packet = buffer.copyOf(length)
        val dns = packet.copyOfRange(dnsStart, dnsEnd)
        return Query(packet, ihl, dns, questionName(dns))
    }

    /** NXDOMAIN answer for [query]: header + original question, no records. */
    fun nxDomain(query: ByteArray): ByteArray? {
        val questionEnd = questionEnd(query) ?: return null
        val response = query.copyOf(questionEnd)
        // QR=1, keep opcode + RD; RA=1, RCODE=3 (NXDOMAIN).
        response[2] = ((query[2].toInt() and 0x79) or 0x80).toByte()
        response[3] = 0x83.toByte()
        put16(response, 4, 1)
        put16(response, 6, 0)
        put16(response, 8, 0)
        put16(response, 10, 0)
        return response
    }

    /** Wraps [dnsPayload] in an IPv4/UDP packet addressed back to the sender of [request]. */
    fun buildReply(request: Query, dnsPayload: ByteArray): ByteArray {
        val req = request.packet
        val ihl = request.ipHeaderLength
        val total = IP_HEADER + UDP_HEADER + dnsPayload.size
        val out = ByteArray(total)

        out[0] = 0x45
        put16(out, 2, total)
        put16(out, 6, 0x4000) // Don't fragment
        out[8] = 64
        out[9] = PROTOCOL_UDP.toByte()
        System.arraycopy(req, 16, out, 12, 4)
        System.arraycopy(req, 12, out, 16, 4)
        put16(out, 10, ipChecksum(out))

        put16(out, IP_HEADER, u16(req, ihl + 2))
        put16(out, IP_HEADER + 2, u16(req, ihl))
        put16(out, IP_HEADER + 4, UDP_HEADER + dnsPayload.size)
        // UDP checksum 0 = "not computed", valid for IPv4.
        System.arraycopy(dnsPayload, 0, out, IP_HEADER + UDP_HEADER, dnsPayload.size)
        return out
    }

    private fun questionName(dns: ByteArray): String? {
        if (u16(dns, 4) < 1) return null
        val labels = StringBuilder()
        var i = DNS_HEADER
        while (i < dns.size) {
            val len = dns[i].toInt() and 0xFF
            if (len == 0) return labels.toString().lowercase()
            if (len and 0xC0 != 0) return null
            i++
            if (i + len > dns.size) return null
            if (labels.isNotEmpty()) labels.append('.')
            labels.append(String(dns, i, len, Charsets.US_ASCII))
            i += len
        }
        return null
    }

    private fun questionEnd(dns: ByteArray): Int? {
        var i = DNS_HEADER
        while (i < dns.size) {
            val len = dns[i].toInt() and 0xFF
            if (len == 0) {
                val end = i + 1 + 4 // QTYPE + QCLASS
                return if (end <= dns.size) end else null
            }
            if (len and 0xC0 != 0) return null
            i += 1 + len
        }
        return null
    }

    private fun ipChecksum(packet: ByteArray): Int {
        var sum = 0
        for (i in 0 until IP_HEADER step 2) {
            sum += u16(packet, i)
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun put16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value shr 8).toByte()
        data[offset + 1] = value.toByte()
    }
}
