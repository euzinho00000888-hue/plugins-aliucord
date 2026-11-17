# Imago para Aliucord

Port do plugin **Imago** (Revenge/Vendetta) para o Aliucord. Busca imagens no Imago e envia o link
no chat, como o seletor do Tenor.

## Compilar
```
./gradlew :Imago:make
./gradlew :Imago:deployWithAdb   # instala direto no celular via adb
```
O zip final fica em `plugins/Imago/build/outputs/Imago.zip`.

## Configurar
Plugins > Imago > engrenagem: cole sua **Imago Key** (`imk_...`). O endereço padrão é
`https://imago-gamma.vercel.app`.

## Se o botão não aparecer
Abra um chat, depois Plugins > Imago > engrenagem > **Diagnóstico do botão**. Ele lista os ids/descrições
das views da barra de entrada; com isso dá para ajustar a regex `HINT` em `ButtonInjector.kt`.
