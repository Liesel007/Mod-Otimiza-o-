# Connectivity (`connectivity`, someaddon)

Fonte estudada: `someaddons/connectivity`, branch `1.20.1`. 31 mixins. Usa Cupboard para config.

Não deixa o jogo mais rápido: evita **quedas de conexão** em modpacks grandes e ajuda a diagnosticar
pacotes problemáticos.

## Problemas e soluções
- **Limites de tamanho de pacote** (o vanilla derruba a conexão):
  - `PacketEncoderMixin` / `CompressionEncoderMixin`: `@ModifyConstant` troca o limite de 8 MiB
    (`8388608`) por `Integer.MAX_VALUE`.
  - `CompressionDecoderMixin`: desliga `validateDecompressed` durante o `decode` e restaura no fim.
  - `ClientBoundCustomPayloadPacketLMixin` e `ConnectionProtocolMixin`: removem o limite de 1 MiB dos
    pacotes de login/payload. O segundo **reescreve o desserializador** registrado em
    `PacketSet.addPacket` para os pacotes de query de login.
  - `FriendlyByteBufLimitMixin` + `NbtAccounterMixin`: aumenta a cota do `NbtAccounter` para
    `Long.MAX_VALUE` ao ler NBT de pacotes; `CompoundTagMixin` remove a contagem de bytes do NBT.
- **Timeouts**: `ConnectionTimeoutMixin` / `NetworkSystemMixin` trocam o `ReadTimeoutHandler` do Netty
  (30 s) por `disconnectTimeout` (60 s); `ServerLoginNetHandlerMixin` muda o timeout de login de 600
  ticks para `logintimeout*20` (120 s); `ServerPlayNetHandlerMixin` alarga o timeout de keep-alive.
- **Chat assinado**: `ClientPacketListenerMixin` não desconecta quando a validação da assinatura do
  chat falha (`disableChatVerificationDisconnect`).
- **Desconexão que trava**: `ConnectionMixin` troca `awaitUninterruptibly()` por `await(10s)`;
  depois de 20 erros de rede seguidos, fecha o canal e remove os handlers do pipeline.
- **Tráfego malformado** (`malformedtraffic/*`, `MalformedTrafficTracker`): um handler adicionado no
  **início** do pipeline Netty conta erros por IP numa janela de 60 s. Com o limiar adaptativo
  (cai quando a taxa global de erros sobe), "congela" o canal (`AUTO_READ=false`) e, com 40 erros,
  bloqueia o IP. Jogadores já logados são ignorados. Proxies e loopback só são congelados.
- **Diagnóstico**: loga pacotes grandes demais ou que falharam ao (de)codificar, com nome da classe
  (inclusive mensagens de mods via `SimpleChannel`, com a interface `IWrappedPacket` para guardar a
  mensagem original); comandos de estatísticas de rede por jogador/tipo de pacote
  (`NetworkStatGatherer`, via `PacketEncoder`).
- `ChunkSectionReadSkipMixin`: no cliente, se o pacote de chunk tem menos seções do que o mundo
  espera, pula a leitura em vez de crashar.

## O que aprender
- O pipeline do **Netty** (`ChannelPipeline.addFirst`, `ChannelInboundHandlerAdapter`) é o ponto
  certo para filtrar tráfego antes que ele chegue ao jogo.
- `require = 0, expect = 0` nos mixins: se a constante/alvo não existe (outro mod já mudou), o mixin é
  ignorado em vez de quebrar o carregamento. Importante para compatibilidade.
- **Cuidado (trade-off de segurança)**: tirar os limites de pacote e de NBT remove proteções do
  vanilla contra pacotes gigantes (consumo de memória). Em servidor público, o detector de tráfego
  malformado compensa só em parte.
