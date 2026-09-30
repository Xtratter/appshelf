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

    /** Файл или вложенная папка ([dir]) на сервере. */
    data class Entry(val name: String, val modified: Long, val size: Long, val dir: Boolean = false)

    private class Response(val code: Int, val reason: String, val headers: Map<String, String>, val body: ByteArray)

    val folder: URL = URL(encodeUrl(folderUrl.trim().trimEnd('/')) + "/")

    /** Путь внутри папки: «POCO F3/AppShelf.json» — каждая часть кодируется отдельно. */
    fun fileUrl(path: String) = URL(folder, path.split('/').joinToString("/") { encodeSegment(it) })

    private fun folderUrl(sub: String) = if (sub.isEmpty()) folder else URL(fileUrl(sub.trimEnd('/')).toString() + "/")

    /** Проверить адрес и вход: папка должна существовать. */
    fun check() {
        ok(request("PROPFIND", folder, mapOf("Depth" to "0", "Content-Type" to XML), PROPFIND.toByteArray()))
    }

    /** Создать папку [sub] внутри основной (пустая строка — саму основную). */
    fun createFolder(sub: String = "") {
        val r = request("MKCOL", folderUrl(sub))
        if (r.code != 405) ok(r)   // 405 — папка уже есть
    }

    /** Записать файл [path]; если папок нет — создать их и записать ещё раз. */
    fun put(path: String, data: ByteArray, type: String) {
        val headers = mapOf("Content-Type" to "$type; charset=utf-8")
        val r = request("PUT", fileUrl(path), headers, data)
        if (r.code == 404 || r.code == 409) {
            createFolder()
            val parent = path.substringBeforeLast('/', "")
            if (parent.isNotEmpty()) createFolder(parent)
            ok(request("PUT", fileUrl(path), headers, data))
        } else ok(r)
    }

    fun delete(name: String) {
        val r = request("DELETE", fileUrl(name))
        if (r.code != 404) ok(r)
    }

    fun get(name: String): ByteArray = ok(request("GET", fileUrl(name))).body

    /** Скачать большой файл сразу в [dest] (с прогрессом). */
    fun download(path: String, dest: java.io.File, progress: ((Long) -> Unit)? = null) {
        dest.outputStream().use { out -> ok(request("GET", fileUrl(path), stream = Stream(sink = out, progress = progress))) }
    }

    /** Загрузить большой файл из [file] потоком; если папок нет — создать их и загрузить ещё раз. */
    fun putFile(path: String, file: java.io.File, type: String, progress: ((Long) -> Unit)? = null) {
        val headers = mapOf("Content-Type" to type)
        val r = request("PUT", fileUrl(path), headers, stream = Stream(upload = file, progress = progress))
        if (r.code == 404 || r.code == 409) {
            createFolder()
            val parent = path.substringBeforeLast('/', "")
            if (parent.isNotEmpty()) createFolder(parent)
            ok(request("PUT", fileUrl(path), headers, stream = Stream(upload = file, progress = progress)))
        } else ok(r)
    }

    /** Содержимое папки [sub] (пустая строка — основной): файлы и вложенные папки. */
    fun list(sub: String = ""): List<Entry> {
        val url = folderUrl(sub)
        val r = ok(request("PROPFIND", url, mapOf("Depth" to "1", "Content-Type" to XML), PROPFIND.toByteArray()))
        return parseList(r.body.toString(Charsets.UTF_8), url)
    }

    private fun ok(r: Response): Response {
        if (r.code !in 200..299) throw HttpError(r.code, r.reason)
        return r
    }

    // ---------- HTTP ----------

    /**
     * Большие файлы — потоком: [upload] отправляется из файла кусками, тело успешного ответа пишется в [sink];
     * [progress] получает, сколько байт передано.
     */
    private class Stream(val upload: java.io.File? = null, val sink: java.io.OutputStream? = null,
                         val progress: ((Long) -> Unit)? = null)

    private fun request(method: String, url: URL, headers: Map<String, String> = emptyMap(), body: ByteArray? = null,
                        hops: Int = 3, stream: Stream? = null): Response {
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
            val upload = stream?.upload
            if (body != null) head.append("Content-Length: ").append(body.size).append("\r\n")
            else if (upload != null) head.append("Content-Length: ").append(upload.length()).append("\r\n")
            head.append("\r\n")
            val out = java.io.BufferedOutputStream(it.getOutputStream(), 64 * 1024)
            out.write(head.toString().toByteArray(Charsets.UTF_8))
            body?.let(out::write)
            if (upload != null) upload.inputStream().use { inp ->
                val buf = ByteArray(64 * 1024)
                var sent = 0L
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    sent += n
                    stream.progress?.invoke(sent)
                }
            }
            out.flush()
            read(BufferedInputStream(it.getInputStream()), method, stream)
        }
        // переадресация — только на тот же сервер, чтобы не отдать пароль чужому
        val location = r.headers["location"]
        if (r.code in intArrayOf(301, 302, 303, 307, 308) && location != null && hops > 0) {
            val to = URL(url, location)
            if (to.host.equals(url.host, true)) {
                return if (r.code == 303) request("GET", to, emptyMap(), null, hops - 1, stream?.let { Stream(sink = it.sink, progress = it.progress) })
                else request(method, to, headers, body, hops - 1, stream)
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

    private fun read(inp: InputStream, method: String, stream: Stream? = null): Response {
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
        val sink = stream?.sink
        if (sink != null && code in 200..299 && method != "HEAD") {
            // большой ответ — сразу в файл, не в память
            copyBody(inp, sink, len, headers["transfer-encoding"]?.contains("chunked", true) == true, stream.progress)
            return Response(code, parts.getOrElse(2) { "" }, headers, ByteArray(0))
        }
        val body = when {
            method == "HEAD" || code == 204 || code == 304 -> ByteArray(0)
            headers["transfer-encoding"]?.contains("chunked", true) == true -> chunked(inp)
            len != null -> exactly(inp, len)
            else -> limited(inp)
        }
        return Response(code, parts.getOrElse(2) { "" }, headers, body)
    }

    private fun copyBody(inp: InputStream, out: java.io.OutputStream, len: Long?, chunked: Boolean, progress: ((Long) -> Unit)?) {
        val buf = ByteArray(64 * 1024)
        var done = 0L
        fun pump(limit: Long) {   // limit < 0 — до конца потока
            var left = limit
            while (limit < 0 || left > 0) {
                val n = inp.read(buf, 0, if (limit < 0) buf.size else minOf(buf.size.toLong(), left).toInt())
                if (n < 0) { if (limit < 0) return else throw IOException("connection closed") }
                out.write(buf, 0, n)
                done += n; left -= n
                progress?.invoke(done)
            }
        }
        when {
            chunked -> while (true) {
                val size = line(inp)?.substringBefore(';')?.trim()?.toLongOrNull(16) ?: throw IOException("bad chunk")
                if (size == 0L) { while (!line(inp).isNullOrEmpty()) { }; return }
                pump(size)
                line(inp)
            }
            len != null -> pump(len)
            else -> pump(-1)
        }
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

    private fun parseList(xml: String, self: URL): List<Entry> {
        val selfPath = decode(self.path).trimEnd('/')
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
                    "response" -> if (href.isNotEmpty()) {
                        // в ответе есть и сама запрошенная папка — её пропускаем
                        val path = decode(runCatching { URL(self, href).path }.getOrDefault(href)).trimEnd('/')
                        val name = path.substringAfterLast('/')
                        if (name.isNotEmpty() && path != selfPath) out += Entry(name, modified, size, dir)
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
