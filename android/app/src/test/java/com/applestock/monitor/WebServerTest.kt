package com.applestock.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

class WebServerTest {
    @Test
    fun parsesRequest() {
        val body = """{"parts":"MK2N4CH/A 夜空色"}"""
        val raw = "POST /api/config?key=123456&x=a%20b HTTP/1.1\r\nHost: x\r\nX-Key: 654321\r\n" +
            "Content-Length: ${body.toByteArray().size}\r\n\r\n$body"
        val req = WebServer.parse(ByteArrayInputStream(raw.toByteArray()))!!
        assertEquals("POST", req.method)
        assertEquals("/api/config", req.path)
        assertEquals("123456", req.query["key"])
        assertEquals("a b", req.query["x"])
        assertEquals("654321", req.headers["x-key"])
        assertEquals(body, req.body)
    }

    @Test
    fun servesOverHttp() {
        val server = WebServer(18765) { req ->
            if (req.path == "/api/echo") HttpResponse.json("""{"m":"${req.method}","b":"${req.body}"}""")
            else HttpResponse.text(404, "Not Found")
        }
        server.start()
        try {
            val c = URL("http://127.0.0.1:18765/api/echo").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.outputStream.use { it.write("你好".toByteArray()) }
            assertEquals(200, c.responseCode)
            val text = c.inputStream.bufferedReader().readText()
            assertTrue(text, text.contains("\"b\":\"你好\""))
            val nf = URL("http://127.0.0.1:18765/nope").openConnection() as HttpURLConnection
            assertEquals(404, nf.responseCode)
        } finally {
            server.stop()
        }
    }
}
