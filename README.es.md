# anota-api-scala · Cliente oficial de Scala para la API de [anota](https://anota.cloud)

**[Read me in English](README.md)** · [Referencia interactiva de la API](https://anota.cloud/developers) · [Todos los SDK](https://github.com/anotacloud/anota-api)

![CI](https://github.com/anotacloud/anota-api-scala/actions/workflows/ci.yml/badge.svg)

Crea y publica formularios, edita campos y lógica condicional, lee y escribe
respuestas, y conecta webhooks — todo lo que la API REST de anota puede hacer, desde Scala.

Construido sobre el cliente `java.net.http` del JDK, **sin dependencias de terceros**.
Es un cliente *ligero*: cada método devuelve el cuerpo JSON de la respuesta en crudo como
un `String`, y `fields`/`rules`/`answers` se pasan como cadenas JSON. Combínalo con la
biblioteca JSON que prefieras — [circe](https://github.com/circe/circe),
[uPickle](https://github.com/com-lihaoyi/upickle) o
[jsoniter-scala](https://github.com/plokhotnyuk/jsoniter-scala) — para construir las
peticiones y parsear las respuestas. Requiere Scala 3 y Java 11+.

## Instalación

Este SDK todavía no está publicado en Maven Central. Depende de él directamente desde
GitHub como una dependencia de código fuente de sbt en tu `build.sbt`:

```scala
lazy val anotaApi =
  RootProject(uri("https://github.com/anotacloud/anota-api-scala.git#v1.0.0"))

lazy val root = (project in file("."))
  .dependsOn(anotaApi)
```

O descarga y descomprime una instantánea de `main`:
[ZIP](https://github.com/anotacloud/anota-api-scala/archive/refs/heads/main.zip) ·
[Tarball](https://github.com/anotacloud/anota-api-scala/archive/refs/heads/main.tar.gz)
— el cliente es un único archivo, `src/main/scala/cloud/anota/AnotaClient.scala`, que
también puedes copiar en un proyecto existente.

## Inicio rápido

```scala
import cloud.anota.AnotaClient

@main def quickstart(): Unit =
  val client = AnotaClient(sys.env("ANOTA_API_KEY"))

  // Crea un formulario con un campo de texto y publícalo.
  val form = client.createForm("Contáctanos", """[{"type":"text","label":"Nombre","required":true}]""")
  // form es JSON en crudo: parséalo con tu biblioteca JSON para leer el id, p. ej. "frm_123".

  client.publishForm("frm_123")

  // Lee las respuestas (una cadena JSON que tú mismo parseas).
  println(client.listSubmissions("frm_123"))
```

## Autenticación

Crea una clave de API en tu workspace en https://anota.cloud/api-keys y pásala al
cliente. Las claves tienen el aspecto `anota_sk_…` y también habilitan el conector MCP de
Claude.

```scala
val client = AnotaClient(apiKey = "anota_sk_…")
// apunta a otro entorno con el segundo argumento:
val staging = AnotaClient("anota_sk_…", baseUrl = "https://staging.anota.cloud/api/v1")
```

## Todos los métodos

Cada método devuelve `String` (el cuerpo JSON de la respuesta en crudo). Los parámetros
llamados `fields`, `field`, `rules`, `rule` y `answers` son cadenas JSON que serializas
tú mismo.

| # | Método | HTTP |
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

Las formas de campo/regla/respuesta que la API espera:

- **fields / field**: `{type, label, required?, options?, rows?, columns?}`
- **rules / rule**: `{match: "all"|"any", if: [{fieldId, operator, value?}], then: [{action, targetId?, formula?, emailTo?}]}`
- **answers**: un objeto indexado por id de campo, con valores de tipo cadena o arreglo de cadenas.

Consulta una ejecución completa de principio a fin en
[`examples/EndToEnd.scala`](examples/EndToEnd.scala).

## Errores

Las respuestas que no sean 2xx lanzan `AnotaApiError`, una `case class` que lleva el
`status` HTTP y el `message` del servidor (tomado del `detail` de problem-details, con
respaldo en `title` y luego en el cuerpo en crudo). Los fallos de red se manifiestan como
la `java.io.IOException` subyacente, sin envolver.

```scala
import cloud.anota.{AnotaClient, AnotaApiError}

try client.publishForm("frm_missing")
catch
  case AnotaApiError(status, message) =>
    System.err.println(s"error de la API de anota $status: $message")
```

Nota: una vez que un formulario ha sido publicado, sus campos existentes quedan
bloqueados (`editField` / `deleteField` devuelven 400); siempre puedes usar `addFields`.

## Licencia

MIT
