package cloud.anota

/** End-to-end example: create a form, add a field, publish it, submit an answer, and
  * list submissions. Run it with your key in the environment:
  *
  * {{{
  * ANOTA_API_KEY=anota_sk_... sbt "runMain cloud.anota.endToEnd"
  * }}}
  *
  * This SDK is intentionally thin and returns raw JSON strings, so the example pulls
  * ids out of responses with a small regex. In real code, parse responses with a JSON
  * library (circe, uPickle, jsoniter, …) instead.
  */
@main def endToEnd(): Unit =
  val apiKey = sys.env.getOrElse(
    "ANOTA_API_KEY",
    sys.error("Set ANOTA_API_KEY (create a key at https://anota.cloud/api-keys)")
  )
  val client = AnotaClient(apiKey)

  def ids(json: String): List[String] =
    """"id"\s*:\s*"([^"]+)"""".r.findAllMatchIn(json).map(_.group(1)).toList

  println("1. Creating a form with one field…")
  val form   = client.createForm("Contact us", """[{"type":"text","label":"Name","required":true}]""")
  val formId = ids(form).headOption.getOrElse(sys.error(s"no form id in response: $form"))
  println(s"   formId = $formId")

  println("2. Adding a second field…")
  client.addFields(formId, """[{"type":"email","label":"Email"}]""")

  println("3. Publishing the form…")
  client.publishForm(formId)

  println("4. Submitting an answer…")
  // Answers are keyed by field id; reuse the first field id created above.
  val fieldId = ids(form).drop(1).headOption.getOrElse("f_1")
  client.createSubmission(formId, s"""{"$fieldId":"Ada Lovelace"}""")

  println("5. Listing submissions…")
  println(client.listSubmissions(formId))
