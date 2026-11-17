package com.github.euzinho.customcss

object CssPresets {
    const val DEFAULT_CSS = """/* Custom CSS para o Aliucord
 *
 * Seletores:  Tipo  #id  .classe  e descendentes (".a TextView")
 * Tipos:      TextView, ImageView, Button, RecyclerView, LinearLayout...
 * Classes:    .message-text  .message-author  .message-avatar
 * Outros ids: use o comando /cssdump para listar os ids da tela atual.
 *
 * Propriedades: color, background, background-color, opacity, font-size,
 * font-weight, font-style, font-family, letter-spacing, border-radius,
 * border, padding(-left/-right/-top/-bottom), display, visibility, tint
 */

:root {
  --accent: #5865f2;
}

.message-author {
  color: var(--accent);
  font-weight: bold;
}
"""

    val PRESETS: List<Pair<String, String>> = listOf(
        "Neon" to """/* Neon */
:root { --a: #00ffd5; --b: #ff2bd6; }

.message-author { color: var(--a); font-weight: bold; }
.message-text   { color: #e8e8ff; }
.message-avatar { border-radius: 50%; border: 2px solid var(--b); }
Button {
  background: linear-gradient(to right, var(--b), var(--a));
  color: #000000;
  border-radius: 12;
}
""",
        "AMOLED" to """/* Fundo preto verdadeiro */
RecyclerView, .list { background-color: #000000; }
.message { background-color: #000000; }
.message-text { color: #f2f3f5; }
""",
        "Arredondado" to """/* Cantos arredondados */
.message-avatar { border-radius: 50%; }
.message { border-radius: 14; padding: 8 12; }
Button { border-radius: 14; }
""",
        "Leitura" to """/* Texto maior e espaçado */
.message-text   { font-size: 17; letter-spacing: 0.02em; }
.message-author { font-size: 16; font-weight: bold; }
""",
        "Código" to """/* Fonte monoespaçada */
.message-text   { font-family: monospace; font-size: 14; }
.message-author { font-family: monospace; }
""",
        "Limpar" to "/* vazio */\n",
    )
}
