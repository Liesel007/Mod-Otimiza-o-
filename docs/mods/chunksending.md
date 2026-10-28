# Chunk Sending (`chunksending`, someaddon)

Fonte estudada: `someaddons/chunksending`, branch `1.20.1`. 7 mixins. Usa Cupboard para config.

## Problema
Quando o jogador entra no mundo, teleporta ou anda rápido, o servidor vanilla monta e envia de
uma vez todos os pacotes `ClientboundLevelChunkWithLightPacket` dos chunks em volta. Isso satura a
rede e a thread principal, principalmente com vários jogadores entrando juntos.

## Como resolve
1. **Fila de envio por jogador com orçamento** (`ChunkMapMixin` + `ServerPlayerChunkSending`):
   - `@Inject(HEAD, cancellable)` em `ChunkMap.playerLoadedChunk`: se a chamada vem de outra
     thread, reagenda com `server.execute`. Se o jogador não está enviando aquele chunk agora,
     coloca na fila `chunksToSend` e cancela o envio.
   - No fim do `ServerPlayer.tick`, ordena a fila: primeiro os chunks a até 1 chunk do jogador,
     depois por distância de um ponto **3 chunks à frente de onde o jogador olha**
     (`prioritizeDirection`). Envia até `maxChunksPerTick` (15; ×3 em singleplayer).
   - O flag `sending` evita que a chamada reentrante seja enfileirada de novo.
   - Limpa a fila ao trocar de dimensão ou desconectar, e remove chunks que o jogador deixou de ver
     (`untrackChunk`).
2. **Orçamento global** (`EventHandler.onServerTick`): se a soma do que todos querem enviar passa de
   `maxChunksPerTickAll` (80), divide igualmente entre os jogadores (mínimo 1).
3. **Cache do pacote de chunk** (`mixin/cache/*`):
   - O pacote montado fica guardado no `LevelChunk` (campo `volatile` injetado) e é reaproveitado
     para o próximo jogador que pedir o mesmo chunk.
   - Invalidação cuidadosa: qualquer `setBlockState`, `setBlockEntity`, `removeBlockEntity`,
     mudança de biomas, heightmap, luz (`sectionLightChanged`), `setUnsaved(true)` ou unload
     limpa o cache.
   - Caches expiram em 5 min; a lista usa `WeakReference` para não segurar chunks na memória.
4. **Mapas em molduras** (`ServerEntityMapMixin`): o vanilla atualiza mapas em item frames a cada
   10 ticks para todos. Aqui: jogadores próximos a cada 20 ticks e todos a cada 100. Com
   `@ModifyVariable(STORE)` anula a variável local para pular o código vanilla. O `tickCount + entityId`
   espalha as atualizações de molduras diferentes por ticks diferentes.
- `MixinConfig` (`IMixinConfigPlugin`) liga/desliga grupos de mixins pela config **antes** de
  aplicar, para facilitar compatibilidade.

## O que aprender
- **Limitar taxa + priorizar o que o jogador vê.** A sensação de velocidade vem da ordem, não só
  do total.
- **Cache com invalidação por eventos de escrita**: todo caminho que muda o dado precisa limpar o
  cache, senão o cliente recebe chunk velho.
- **Espalhar trabalho periódico com offset por entidade** (`tick + id`) em vez de todos no mesmo tick.
- `IMixinConfigPlugin.shouldApplyMixin` para desligar mixins por config.
