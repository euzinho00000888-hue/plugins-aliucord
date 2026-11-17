version = "1.0.0" // Increment this to trigger an update
description = "Editor de aparência com CSS: escreva regras, veja a pré-visualização ao vivo e aplique ao app."

aliucord {
    changelog.set(
        """
        # 1.0.0
        * Editor de CSS com destaque de sintaxe, validação por linha e pré-visualização ao vivo
        * Seletores por tipo, #id, .classe e descendentes; variáveis `var(--x)`
        * Propriedades: color, background (cor/gradiente), border, border-radius, padding, opacity, fonte, display, tint
        * Modelos prontos (Neon, AMOLED, Arredondado, Leitura, Código)
        * Comandos `/cssdump` (lista ids da tela) e `/csstoggle`
        * Proteção automática caso o app feche logo após iniciar com o CSS ligado
        """.trimIndent(),
    )

    deploy.set(true)
}
