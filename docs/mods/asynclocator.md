# Async Locator (`asynclocator`, thebrightspark)

Fonte estudada: `thebrightspark/AsyncLocator`, branch `1.20.x` (multiloader). 13 mixins.

## Problema
Procurar a estrutura mais próxima (`ServerLevel.findNearestMapStructure` /
`ChunkGenerator.findNearestMapStructure`) pode levar **segundos**, porque pode precisar checar
muitos chunks. O vanilla faz isso na thread principal em:
- `/locate`;
- olho de Ender (`EnderEyeItem.use`);
- mapas do tesouro de aldeões (`VillagerTrades$TreasureMapForEmeralds.getOffer`);
- mapas de exploração em baús de loot (`ExplorationMapFunction.run`).
O servidor congela até a busca terminar, e o "Can't keep up!" aparece.

## Como resolve
- `AsyncLocator`: um `FixedThreadPool` próprio (tamanho na config), criado no início do servidor e
  desligado no fim. `locate(...)` envia a busca ao pool e devolve um `LocateTask` com um
  `CompletableFuture`.
- `LocateTask.thenOnServerThread(action)`: quando o resultado chega, usa `server.submit` para
  aplicar o resultado **de volta na thread principal**.
- Para cada ponto de uso, um mixin intercepta logo antes da chamada da busca:
  - **Mapas** (loot e aldeão): devolve na hora um mapa vazio de placeholder; quando a busca
    termina, preenche o mapa (posição, decoração, nome guardado num cache Guava de 5 min). Se não
    achar nada, troca por mapa comum ou marca a oferta do aldeão como esgotada/remove. Reenvia as
    ofertas se o jogador estiver com a tela de troca aberta.
  - **Olho de Ender**: o item cria a entidade com destino `BlockPos.ZERO`; a entidade fica parada
    (`EyeOfEnderMixin` cancela o `tick` depois do `super.tick()`) até o resultado chegar.
  - **/locate**: responde no chat quando a busca terminar.

## O que aprender
- **Responder com placeholder e completar depois** é uma forma de tirar trabalho lento da thread
  principal sem mudar a interface que o jogador vê.
- Padrão "**calcular fora, aplicar dentro**" (`CompletableFuture` + `server.submit`).
- Cuidado: a busca roda fora da thread principal mas lê dados do mundo. Funciona porque ela usa
  principalmente o gerador (determinístico) e dados de referência de estruturas, mas não é
  thread-safe garantido. Por isso cada ponto pode ser desligado na config.
