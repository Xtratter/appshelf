package io.github.xtratter.appshelf

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Простой клиент WebDAV (Nextcloud, ownCloud, Яндекс Диск, NAS…): папка [folderUrl], вход по логину и паролю (Basic).
 * HTTP/1.1 написан вручную поверх сокета: HttpURLConnection не умеет методы WebDAV (PROPFIND, MKCOL).
 */
class WebDav(folderUrl: String, private val user: String, private val pass: String) {
    /** Сервер ответил ошибкой. */
    class HttpError(val code: Int, reason: String) : IOException("HTTP $code $reason".trim())

    /** Файл в папке на сервере. */
    data class Entry(val name: String, val modified: Long, val size: Long)

    private class Response(val code: Int, val reason: String, val headers: Map<String, String>, val body: ByteArray)

    val folder: URL = URL(encodeUrl(folderUrl.trim().trimEnd('/')) + "/")

    fun fileUrl(name: String) = URL(folder, encodeSegment(name))

    /** Проверить адрес и вход: папка должна существовать. */
    fun check() {
        ok(request("PROPFIND", folder, mapOf("Depth" to "0", "Content-Type" to XML), PROPFIND.toByteArray()))
    }

    /** Создать папку (одну — последнюю в адресе). */
    fun createFolder() {
        val r = request("MKCOL", folder)
        if (r.code != 405) ok(r)   // 405 — папка уже есть
    }

    /** Записать файл; если папки нет — создать её и записать ещё раз. */
    fun put(name: String, data: ByteArray, type: String) {
        val headers = mapOf("Content-Type" to "$type; charset=utf-8")
        val r = request("PUT", fileUrl(name), headers, data)
        if (r.code == 404 || r.code == 409) {
            createFolder()
            ok(request("PUT", fileUrl(name), headers, data))
        } else ok(r)
    }

    fun delete(name: String) {
        val r = request("DELETE", fileUrl(name))
        if (r.code != 404) ok(r)
    }

    fun get(name: String): ByteArray = ok(request("GET", fileUrl(name))).body

    /** Файлы в папке (без вложенных папок). */
    fun list(): List<Entry> {
        val r = ok(request("PROPFIND", folder, mapOf("Depth" to "1", "Content-Type" to XML), PROPFIND.toByteArray()))
        return parseList(r.body.toString(Charsets.UTF_8))
    }

    private fun ok(r: Response): Response {
        if (r.code !in 200..299) throw HttpError(r.code, r.reason)
        return r
    }

    // ---------- HTTP ----------

    private fun request(method: String, url: URL, headers: Map<String, String> = emptyMap(), body: ByteArray? = null,
                        hops: Int = 3): Response {
        val https = url.protocol.equals("https", true)
        if (!https && !url.protocol.equals("http", true)) throw IllegalArgumentException("http:// / https://")
        val host = url.host.removeSurrounding("[", "]")
        val port = if (url.port > 0) url.port else url.defaultPort
        val raw = connect(host, port)
        val sock = if (!https) raw else {
            val s = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw, host, port, true) as SSLSocket
            s.startHandshake()
            if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, s.session)) {
                s.close()
                throw SSLPeerUnverifiedException("certificate is not for $host")
            }
            s
        }
        val r = sock.use {
            val head = StringBuilder()
            head.append(method).append(' ').append(url.path.ifEmpty { "/" })
            url.query?.let { q -> head.append('?').append(q) }
            head.append(" HTTP/1.1\r\nHost: ").append(url.host).append(if (url.port > 0) ":${url.port}" else "")
            head.append("\r\nUser-Agent: AppShelf\r\nConnection: close\r\nAccept-Encoding: identity\r\n")
            if (user.isNotEmpty() || pass.isNotEmpty()) head.append("Authorization: Basic ")
                .append(Base64.getEncoder().encodeToString("$user:$pass".toByteArray(Charsets.UTF_8))).append("\r\n")
            for ((k, v) in headers) head.append(k).append(": ").append(v).append("\r\n")
            if (body != null) head.append("Content-Length: ").append(body.size).append("\r\n")
            head.append("\r\n")
            val out = it.getOutputStream()
            out.write(head.toString().toByteArray(Charsets.UTF_8))
            body?.let(out::write)
            out.flush()
            read(BufferedInputStream(it.getInputStream()), method)
        }
        // переадресация — только на тот же сервер, чтобы не отдать пароль чужому
        val location = r.headers["location"]
        if (r.code in intArrayOf(301, 302, 303, 307, 308) && location != null && hops > 0) {
            val to = URL(url, location)
            if (to.host.equals(url.host, true)) {
                return if (r.code == 303) request("GET", to, emptyMap(), null, hops - 1)
                else request(method, to, headers, body, hops - 1)
            }
        }
        return r
    }

    /** Пробуем все адреса сервера по очереди (IPv6 и IPv4): один из них может быть недоступен из этой сети. */
    private fun connect(host: String, port: Int): Socket {
        var last: IOException? = null
        for (addr in InetAddress.getAllByName(host)) {
            val s = Socket()
            try {
                s.connect(InetSocketAddress(addr, port), 10_000)
                s.soTimeout = 20_000
                return s
            } catch (e: IOException) {
                s.close()
                last = e
            }
        }
        throw last ?: IOException("no address for $host")
    }

    private fun read(inp: InputStream, method: String): Response {
        var status: String
        var headers: Map<String, String>
        do {   // промежуточные ответы 1xx пропускаем
            status = line(inp) ?: throw IOException("empty response")
            headers = HashMap<String, String>().apply {
                while (true) {
                    val l = line(inp) ?: break
                    if (l.isEmpty()) break
                    val i = l.indexOf(':')
                    if (i > 0) put(l.substring(0, i).trim().lowercase(), l.substring(i + 1).trim())
                }
            }
            val code = status.split(' ').getOrNull(1)?.toIntOrNull() ?: throw IOException("bad response: $status")
        } while (code in 100..199)
        val parts = status.split(' ', limit = 3)
        val code = parts[1].toInt()
        val len = headers["content-length"]?.toLongOrNull()
        val body = when {
            method == "HEAD" || code == 204 || code == 304 -> ByteArray(0)
            headers["transfer-encoding"]?.contains("chunked", true) == true -> chunked(inp)
            len != null -> exactly(inp, len)
            else -> limited(inp)
        }
        return Response(code, parts.getOrElse(2) { "" }, headers, body)
    }

    private fun line(inp: InputStream): String? {
        val b = ByteArrayOutputStream()
        while (true) {
            val c = inp.read()
            if (c < 0) return if (b.size() == 0) null else b.toString("ISO-8859-1")
            if (c == '\n'.code) break
            if (c != '\r'.code) b.write(c)
            if (b.size() > 16 * 1024) throw IOException("header too long")
        }
        return b.toString("ISO-8859-1")
    }

    private fun exactly(inp: InputStream, n: Long): ByteArray {
        if (n > MAX_BODY) throw IOException("response too large")
        val buf = ByteArray(n.toInt())
        var off = 0
        while (off < buf.size) {
            val k = inp.read(buf, off, buf.size - off)
            if (k < 0) throw IOException("connection closed")
            off += k
        }
        return buf
    }

    private fun chunked(inp: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val size = line(inp)?.substringBefore(';')?.trim()?.toLongOrNull(16) ?: throw IOException("bad chunk")
            if (size == 0L) {
                while (!line(inp).isNullOrEmpty()) { /* заголовки после тела не нужны */ }
                return out.toByteArray()
            }
            if (out.size() + size > MAX_BODY) throw IOException("response too large")
            out.write(exactly(inp, size))
            line(inp)
        }
    }

    private fun limited(inp: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val k = inp.read(buf)
            if (k < 0) return out.toByteArray()
            out.write(buf, 0, k)
            if (out.size() > MAX_BODY) throw IOException("response too large")
        }
    }

    // ---------- PROPFIND ----------

    private fun parseList(xml: String): List<Entry> {
        val p = android.util.Xml.newPullParser()
        p.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        p.setInput(xml.reader())
        val out = ArrayList<Entry>()
        var href = ""; var modified = 0L; var size = 0L; var dir = false
        var text = StringBuilder()
        val rfc1123 = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        while (true) {
            when (p.next()) {
                org.xmlpull.v1.XmlPullParser.END_DOCUMENT -> break
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    text = StringBuilder()
                    when (p.name) {
                        "response" -> { href = ""; modified = 0; size = 0; dir = false }
                        "collection" -> dir = true
                    }
                }
                org.xmlpull.v1.XmlPullParser.TEXT -> text.append(p.text)
                org.xmlpull.v1.XmlPullParser.END_TAG -> when (p.name) {
                    "href" -> href = text.toString().trim()
                    "getlastmodified" -> modified = runCatching { rfc1123.parse(text.toString().trim())?.time }.getOrNull() ?: 0
                    "getcontentlength" -> size = text.toString().trim().toLongOrNull() ?: 0
                    "response" -> if (!dir && href.isNotEmpty()) {
                        val name = decode(href.trimEnd('/').substringAfterLast('/'))
                        if (name.isNotEmpty()) out += Entry(name, modified, size)
                    }
                }
            }
        }
        return out
    }

    companion object {
        private const val MAX_BODY = 32L * 1024 * 1024
        private const val XML = "application/xml; charset=utf-8"
        private const val PROPFIND = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getlastmodified/><d:getcontentlength/></d:prop></d:propfind>"""

        private const val HEX = "0123456789ABCDEF"
        private const val KEEP = "-._~!$&'()*+,;=:@"

        /** Часть пути: всё, кроме безопасных символов, кодируется %XX (UTF-8); уже закодированное не трогаем. */
        fun encodeSegment(s: String, keep: String = KEEP): String {
            val sb = StringBuilder()
            var i = 0
            while (i < s.length) {
                val ch = s[i]
                val escaped = ch == '%' && i + 2 < s.length && s[i + 1].isHex() && s[i + 2].isHex()
                if (escaped || (ch.code < 128 && (ch.isLetterOrDigit() || ch in keep))) sb.append(ch)
                else {
                    val cp = s.codePointAt(i)
                    for (b in String(Character.toChars(cp)).toByteArray(Charsets.UTF_8))
                        sb.append('%').append(HEX[(b.toInt() shr 4) and 15]).append(HEX[b.toInt() and 15])
                    if (Character.charCount(cp) == 2) i++
                }
                i++
            }
            return sb.toString()
        }

        /** Адрес, введённый как есть (с пробелами, кириллицей), — в правильно закодированный. */
        fun encodeUrl(url: String): String {
            val m = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*://[^/?#]*)([^?#]*)(.*)$").find(url) ?: return url
            return m.groupValues[1] + encodeSegment(m.groupValues[2], "$KEEP/") + m.groupValues[3]
        }

        private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

        private fun decode(s: String) = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)
    }
}
