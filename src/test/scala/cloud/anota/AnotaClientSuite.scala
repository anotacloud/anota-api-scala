package cloud.anota

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class AnotaClientSuite extends munit.FunSuite:

  /** What the stub server recorded about the incoming request. */
  private case class Captured(method: String, path: String, query: String, auth: String, body: String)

  /** Spin up a throwaway `HttpServer` that returns `(status, responseBody)` for every
    * request, hand the test a client pointed at it plus a getter for the captured
    * request, and tear the server down afterwards. */
  private def withServer(status: Int, responseBody: String)(
      f: (AnotaClient, () => Captured) => Unit
  ): Unit =
    var captured: Captured = null
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      "/",
      exchange => {
        val uri  = exchange.getRequestURI
        val auth = Option(exchange.getRequestHeaders.getFirst("Authorization")).getOrElse("")
        val body = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
        captured = Captured(exchange.getRequestMethod, uri.getPath, Option(uri.getRawQuery).getOrElse(""), auth, body)
        val bytes = responseBody.getBytes(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(status, if bytes.isEmpty then -1L else bytes.length.toLong)
        if bytes.nonEmpty then
          val os = exchange.getResponseBody
          os.write(bytes)
          os.close()
        exchange.close()
      }
    )
    server.start()
    try
      val port   = server.getAddress.getPort
      val client = AnotaClient("test-key", s"http://127.0.0.1:$port/api/v1")
      f(client, () => captured)
    finally server.stop(0)

  test("listForms sends GET /forms with the Bearer auth header") {
    withServer(200, "[]") { (client, captured) =>
      val result = client.listForms()
      val c      = captured()
      assertEquals(c.method, "GET")
      assertEquals(c.path, "/api/v1/forms")
      assertEquals(c.auth, "Bearer test-key")
      assertEquals(result, "[]")
    }
  }

  test("createSubmission sends the wrapped answers JSON body") {
    withServer(200, "{}") { (client, captured) =>
      client.createSubmission("form_1", """{"f_1":"hola"}""")
      val c = captured()
      assertEquals(c.method, "POST")
      assertEquals(c.path, "/api/v1/forms/form_1/submissions")
      assertEquals(c.body, """{"answers":{"f_1":"hola"}}""")
    }
  }

  test("a 400 problem-details response raises AnotaApiError with status and detail") {
    withServer(400, """{"detail":"Error: bad"}""") { (client, _) =>
      val error = intercept[AnotaApiError](client.listForms())
      assertEquals(error.status, 400)
      assertEquals(error.message, "Error: bad")
      assertEquals(error.getMessage, "Error: bad")
    }
  }

  test("listSubmissions builds the ?page=&pageSize=&status= query string") {
    withServer(200, "{}") { (client, captured) =>
      client.listSubmissions("form_1", 2, 10, Some("New"))
      val c = captured()
      assertEquals(c.method, "GET")
      assertEquals(c.path, "/api/v1/forms/form_1/submissions")
      assertEquals(c.query.split("&").toSet, Set("page=2", "pageSize=10", "status=New"))
    }
  }

  test("listSubmissions omits status when it is not supplied") {
    withServer(200, "{}") { (client, captured) =>
      client.listSubmissions("form_1")
      val c = captured()
      assertEquals(c.query.split("&").toSet, Set("page=1", "pageSize=25"))
    }
  }
