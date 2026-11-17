version = "1.0.0" // Increment this to trigger an update
description = "Gera timestamps do Discord com /timestamp (agora, +2h, 25/12/2026 18:30...)"

aliucord {
    changelog.set(
        """
        # 1.0.0
        * Primeira versão: /timestamp com horários relativos e absolutos
        """.trimIndent(),
    )

    deploy.set(true)
}
