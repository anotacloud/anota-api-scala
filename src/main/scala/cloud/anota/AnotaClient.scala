package cloud.anota

import java.net.URI
import java.net.URLEncoder
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets

/** Raised when the anota API returns a non-2xx response.
  *
  * `status` is the HTTP status code; `message` is the server's message, taken from
  * the ASP.NET problem-details body (`detail`, falling back to `title`, then the raw
  * body). Network-level failures surface as the underlying `java.io.IOException` /
  * `java.lang.InterruptedException` rather than this type.
  */
case class AnotaApiError(status: Int, message: String) extends Exception(message)

/** A thin, dependency-free client for the [[https://anota.cloud anota]] REST API.
  *
  * Every method returns the raw JSON response body as a `String`; pair this SDK with
  * the JSON library of your choice (circe, uPickle, jsoniter, …) to parse it. In the
  * same spirit, `fields`, `field`, `rules`, `rule`, and `answers` parameters are
  * passed as JSON strings you serialize yourself — this mirrors the Java SDK and keeps
  * the client free of any JSON dependency.
  *
  * {{{
  * val client = AnotaClient(sys.env("ANOTA_API_KEY"))
  * val forms  = client.listForms()
  * }}}
  *
  * @param apiKey  your workspace API key (looks like `anota_sk_…`); create one at
  *                https://anota.cloud/api-keys.
  * @param baseUrl the API root; defaults to `https://anota.cloud/api/v1`.
  */
class AnotaClient(apiKey: String, baseUrl: String = "https://anota.cloud/api/v1"):
  require(apiKey.nonEmpty, "apiKey is required (create one at https://anota.cloud/api-keys)")

  private val root: String       = baseUrl.replaceAll("/+$", "")
  private val http: HttpClient   = HttpClient.newHttpClient()

  /** The shared request core: builds the URL (with query string), sets the Bearer auth
    * header and, when a body is present, the JSON content type; then maps a non-2xx
    * response to [[AnotaApiError]] and an empty 2xx body to the empty string. */
  private def request(
      method: String,
      path: String,
      body: Option[String] = None,
      query: Map[String, String] = Map.empty
  ): String =
    val queryString =
      if query.isEmpty then ""
      else
        query
          .map((k, v) => s"${encode(k)}=${encode(v)}")
          .mkString("?", "&", "")

    val builder = HttpRequest
      .newBuilder()
      .uri(URI.create(root + path + queryString))
      .header("Authorization", s"Bearer $apiKey")

    val publisher = body match
      case Some(json) =>
        builder.header("Content-Type", "application/json")
        HttpRequest.BodyPublishers.ofString(json)
      case None =>
        HttpRequest.BodyPublishers.noBody()

    val response =
      http.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString())
    val text = response.body()
    if response.statusCode() < 200 || response.statusCode() >= 300 then
      throw AnotaApiError(response.statusCode(), extractMessage(text))
    text

  // ----- forms -----
  def listForms(): String = request("GET", "/forms")

  def createForm(title: String, fields: String, description: Option[String] = None): String =
    val desc = description.map(d => s""","description":${jsonString(d)}""").getOrElse("")
    request("POST", "/forms", Some(s"""{"title":${jsonString(title)},"fields":$fields$desc}"""))

  def getForm(formId: String): String = request("GET", s"/forms/$formId")

  def addFields(formId: String, fields: String): String =
    request("POST", s"/forms/$formId/fields", Some(s"""{"fields":$fields}"""))

  def editField(formId: String, fieldId: String, field: String): String =
    request("PATCH", s"/forms/$formId/fields/${encode(fieldId)}", Some(s"""{"field":$field}"""))

  def deleteField(formId: String, fieldId: String): String =
    request("DELETE", s"/forms/$formId/fields/${encode(fieldId)}")

  def publishForm(formId: String): String = request("POST", s"/forms/$formId/publish")

  def renameForm(formId: String, title: String): String =
    request("PATCH", s"/forms/$formId", Some(s"""{"title":${jsonString(title)}}"""))

  def setPdfTemplate(formId: String, key: String): String =
    request("PUT", s"/forms/$formId/pdf-template", Some(s"""{"key":${jsonString(key)}}"""))

  def deleteForm(formId: String): String = request("DELETE", s"/forms/$formId")

  def cloneForm(formId: String): String = request("POST", s"/forms/$formId/clone")

  // ----- logic rules -----
  def addLogicRules(formId: String, rules: String): String =
    request("POST", s"/forms/$formId/logic-rules", Some(s"""{"rules":$rules}"""))

  def editLogicRule(formId: String, ruleId: String, rule: String): String =
    request("PUT", s"/forms/$formId/logic-rules/${encode(ruleId)}", Some(s"""{"rule":$rule}"""))

  def deleteLogicRule(formId: String, ruleId: String): String =
    request("DELETE", s"/forms/$formId/logic-rules/${encode(ruleId)}")

  // ----- submissions -----
  def listSubmissions(
      formId: String,
      page: Int = 1,
      pageSize: Int = 25,
      status: Option[String] = None
  ): String =
    val query = Map("page" -> page.toString, "pageSize" -> pageSize.toString) ++
      status.map(s => "status" -> s)
    request("GET", s"/forms/$formId/submissions", query = query)

  def getSubmission(submissionId: String): String = request("GET", s"/submissions/$submissionId")

  def createSubmission(formId: String, answers: String): String =
    request("POST", s"/forms/$formId/submissions", Some(s"""{"answers":$answers}"""))

  def setSubmissionStatus(submissionId: String, status: String): String =
    request("PATCH", s"/submissions/$submissionId/status", Some(s"""{"status":${jsonString(status)}}"""))

  def deleteSubmission(submissionId: String): String =
    request("DELETE", s"/submissions/$submissionId")

  def submissionStats(formId: String): String = request("GET", s"/forms/$formId/stats")

  // ----- templates -----
  def listTemplates(language: String = "es"): String =
    request("GET", "/templates", query = Map("language" -> language))

  def createFormFromTemplate(templateId: String): String =
    request("POST", s"/forms/from-template/$templateId")

  // ----- webhooks -----
  def listWebhooks(formId: String): String = request("GET", s"/forms/$formId/webhooks")

  def addWebhook(formId: String, url: String): String =
    request("POST", s"/forms/$formId/webhooks", Some(s"""{"url":${jsonString(url)}}"""))

  def deleteWebhook(formId: String, webhookId: String): String =
    request("DELETE", s"/forms/$formId/webhooks/$webhookId")

  // ----- internals -----
  private def encode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8)

  /** Extract the human-readable message from an ASP.NET problem-details body:
    * `detail` first, then `title`, then the raw body if neither is present. */
  private def extractMessage(body: String): String =
    jsonProperty(body, "detail")
      .orElse(jsonProperty(body, "title"))
      .getOrElse(body)

  /** Read a top-level string property out of a JSON object without a JSON parser —
    * enough to pull `detail`/`title` out of an error body. */
  private def jsonProperty(body: String, key: String): Option[String] =
    val pattern = ("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").r
    pattern.findFirstMatchIn(body).map(m => unescape(m.group(1)))

  /** Unescape a JSON string literal's body (the characters between the quotes). */
  private def unescape(s: String): String =
    val sb = new StringBuilder(s.length)
    var i  = 0
    while i < s.length do
      val c = s.charAt(i)
      if c == '\\' && i + 1 < s.length then
        s.charAt(i + 1) match
          case '"'                     => sb.append('"'); i += 2
          case '\\'                    => sb.append('\\'); i += 2
          case '/'                     => sb.append('/'); i += 2
          case 'n'                     => sb.append('\n'); i += 2
          case 't'                     => sb.append('\t'); i += 2
          case 'r'                     => sb.append('\r'); i += 2
          case 'b'                     => sb.append('\b'); i += 2
          case 'f'                     => sb.append('\f'); i += 2
          case 'u' if i + 5 < s.length =>
            sb.append(Integer.parseInt(s.substring(i + 2, i + 6), 16).toChar); i += 6
          case other                   => sb.append(other); i += 2
      else
        sb.append(c); i += 1
    sb.toString

  /** Serialize a Scala string as a JSON string literal (quoted, with escaping). */
  private def jsonString(value: String): String =
    val sb = new StringBuilder(value.length + 2)
    sb.append('"')
    value.foreach {
      case '"'                      => sb.append("\\\"")
      case '\\'                     => sb.append("\\\\")
      case '\n'                     => sb.append("\\n")
      case '\r'                     => sb.append("\\r")
      case '\t'                     => sb.append("\\t")
      case '\b'                     => sb.append("\\b")
      case '\f'                     => sb.append("\\f")
      case c if c < 0x20            => sb.append("\\u%04x".format(c.toInt))
      case c                        => sb.append(c)
    }
    sb.append('"')
    sb.toString
