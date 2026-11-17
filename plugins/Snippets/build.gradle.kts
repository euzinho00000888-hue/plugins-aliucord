version = "1.0.0" // Increment this to trigger an update
description = "Salve textos reutilizáveis e envie com /snip. Comandos: /snipsave /snip /sniplist /snipdel"

aliucord {
    changelog.set(
        """
        # 1.0.0
        * Primeira versão: snipsave, snip, sniplist e snipdel
        """.trimIndent(),
    )

    deploy.set(true)
}
