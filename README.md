# anota-api-scala · Official Scala client for the [anota](https://anota.cloud) API

**[Léeme en español](README.es.md)** · [Interactive API reference](https://anota.cloud/developers) · [All SDKs](https://github.com/anotacloud/anota-api)

![CI](https://github.com/anotacloud/anota-api-scala/actions/workflows/ci.yml/badge.svg)

Create and publish forms, edit fields and conditional logic, read and write
submissions, and wire webhooks — everything the anota REST API can do, from Scala.

Built on the JDK's `java.net.http` client with **no third-party dependencies**.
It is a *thin* client: every method returns the raw JSON response body as a `String`,
and `fields`/`rules`/`answers` are passed as JSON strings. Pair it with the JSON
library of your choice — [circe](https://github.com/circe/circe),
[uPickle](https://github.com/com-lihaoyi/upickle), or
[jsoniter-scala](https://github.com/plokhotnyuk/jsoniter-scala) — to build requests and
parse responses. Requires Scala 3 and Java 11+.

## Install

This SDK is not yet published to Maven Central. Depend on it straight from GitHub as an
sbt source dependency in your `build.sbt`:

```scala
lazy val anotaApi =
  RootProject(uri("https://github.com/anotacloud/anota-api-scala.git#v1.0.0"))

lazy val root = (project in file("."))
  .dependsOn(anotaApi)
```

Or download and unpack a snapshot of `main`:
[ZIP](https://github.com/anotacloud/anota-api-scala/archive/refs/heads/main.zip) ·
[Tarball](https://github.com/anotacloud/anota-api-scala/archive/refs/heads/main.tar.gz)
— the client is a single file, `src/main/scala/cloud/anota/AnotaClient.scala`, that you
can also drop into an existing project.

## Quickstart

```scala
import cloud.anota.AnotaClient

@main def quickstart(): Unit =
  val client = AnotaClient(sys.env("ANOTA_API_KEY"))

  // Create a form with one text field, then publish it.
  val form = client.createForm("Contact us", """[{"type":"text","label":"Name","required":true}]""")
  // form is raw JSON: parse it with your JSON library to read the id, e.g. "frm_123".

  client.publishForm("frm_123")

  // Read submissions (JSON string you parse yourself).
  println(client.listSubmissions("frm_123"))
```

## Authentication

Create an API key in your workspace at https://anota.cloud/api-keys and pass it to the
client. Keys look like `anota_sk_…` and also power the Claude MCP connector.

```scala
val client = AnotaClient(apiKey = "anota_sk_…")
// point at a different environment with the second argument:
val staging = AnotaClient("anota_sk_…", baseUrl = "https://staging.anota.cloud/api/v1")
```

## All methods

Every method returns `String` (the raw JSON response body). Parameters named
`fields`, `field`, `rules`, `rule`, and `answers` are JSON strings you serialize
yourself.

| # | Method | HTTP |
|---|---|---|
| 1 | `listForms()` | `GET /forms` |
| 2 | `createForm(title, fields, description = None)` | `POST /forms` |
| 3 | `getForm(formId)` | `GET /forms/{formId}` |
| 4 | `addFields(formId, fields)` | `POST /forms/{formId}/fields` |
| 5 | `editField(formId, fieldId, field)` | `PATCH /forms/{formId}/fields/{fieldId}` |
| 6 | `deleteField(formId, fieldId)` | `DELETE /forms/{formId}/fields/{fieldId}` |
| 7 | `publishForm(formId)` | `POST /forms/{formId}/publish` |
| 8 | `renameForm(formId, title)` | `PATCH /forms/{formId}` |
| 9 | `setPdfTemplate(formId, key)` | `PUT /forms/{formId}/pdf-template` |
| 10 | `deleteForm(formId)` | `DELETE /forms/{formId}` |
| 11 | `cloneForm(formId)` | `POST /forms/{formId}/clone` |
| 12 | `addLogicRules(formId, rules)` | `POST /forms/{formId}/logic-rules` |
| 13 | `editLogicRule(formId, ruleId, rule)` | `PUT /forms/{formId}/logic-rules/{ruleId}` |
| 14 | `deleteLogicRule(formId, ruleId)` | `DELETE /forms/{formId}/logic-rules/{ruleId}` |
| 15 | `listSubmissions(formId, page = 1, pageSize = 25, status = None)` | `GET /forms/{formId}/submissions` |
| 16 | `getSubmission(submissionId)` | `GET /submissions/{submissionId}` |
| 17 | `createSubmission(formId, answers)` | `POST /forms/{formId}/submissions` |
| 18 | `setSubmissionStatus(submissionId, status)` | `PATCH /submissions/{submissionId}/status` |
| 19 | `deleteSubmission(submissionId)` | `DELETE /submissions/{submissionId}` |
| 20 | `submissionStats(formId)` | `GET /forms/{formId}/stats` |
| 21 | `listTemplates(language = "es")` | `GET /templates` |
| 22 | `createFormFromTemplate(templateId)` | `POST /forms/from-template/{templateId}` |
| 23 | `listWebhooks(formId)` | `GET /forms/{formId}/webhooks` |
| 24 | `addWebhook(formId, url)` | `POST /forms/{formId}/webhooks` |
| 25 | `deleteWebhook(formId, webhookId)` | `DELETE /forms/{formId}/webhooks/{webhookId}` |

The field/rule/answer shapes the API expects:

- **fields / field**: `{type, label, required?, options?, rows?, columns?}`
- **rules / rule**: `{match: "all"|"any", if: [{fieldId, operator, value?}], then: [{action, targetId?, formula?, emailTo?}]}`
- **answers**: an object keyed by field id, values a string or array of strings.

See a full end-to-end run in [`examples/EndToEnd.scala`](examples/EndToEnd.scala).

## Errors

Non-2xx responses throw `AnotaApiError`, a `case class` carrying the HTTP `status` and
the server's `message` (taken from the problem-details `detail`, falling back to
`title`, then the raw body). Network failures surface as the underlying
`java.io.IOException`, not wrapped.

```scala
import cloud.anota.{AnotaClient, AnotaApiError}

try client.publishForm("frm_missing")
catch
  case AnotaApiError(status, message) =>
    System.err.println(s"anota API error $status: $message")
```

Note: once a form has been published, its existing fields are locked (`editField` /
`deleteField` return 400); you can always `addFields`.

## License

MIT
