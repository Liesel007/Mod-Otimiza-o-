# Better Chunk Loading (`betterchunkloading`, someaddon)

Fonte estudada: `someaddons/betterchunkloading`, branch `1.20.1`. 13 mixins. Usa Cupboard para config.

## Problema
O vanilla carrega chunks em volta do jogador num quadrado do tamanho da view distance, com tickets
`PLAYER` geridos pelo `DistanceManager`. Ele não sabe para onde o jogador está indo, então
quem anda rápido (elytra, cavalo) chega a chunks não carregados e o servidor **trava a thread
principal esperando** a geração (`ServerChunkCache.getChunk` bloqueia).

## Como resolve
1. **Substitui o carregamento por jogador** (`ChunkMapViewDistanceFixed*`): `@ModifyArg` força a
   view distance que o vanilla usa para os tickets de jogador a **3**. O resto da área passa a ser
   carregado pelo mod, com tickets próprios por jogador (`bcl_player_<nome>`, timeout 20 min).
2. **Carregamento gradual e ordenado** (`PlayerChunkData.ChunkLoadingTask`):
   - Monta um disco (raio = view distance) de chunks, ordenado pela distância até um ponto
     **3 chunks à frente na direção do movimento**.
   - Adiciona um ticket por vez (pula rápido os que já estão carregados). Entre tickets usa um
     `cooldownCounter` que cresce com o número de chunks já carregados e é ajustado pelo **MSPT
     do servidor** (`tpsMod`: 4× mais rápido se o tick leva ≤40 ms, mais lento acima de 60 ms).
     Ou seja: **controle adaptativo pela carga do servidor**.
   - Ao trocar de tarefa, `syncWithLastTask` reaproveita tickets que continuam válidos (só renova
     o `createdTick`) em vez de remover e adicionar de novo. Ao remover, em vez de tirar o ticket,
     faz ele **expirar em ~30 ticks** (`setCreatedTick`), evitando descarregar e recarregar se o
     jogador voltar.
3. **Previsão de movimento** (`checkPrediction`): guarda as 6 últimas posições do jogador num
   buffer circular, calcula o vetor (média das 3 novas − média das 3 velhas), projeta à frente e
   carrega um disco (`predictionarea` = 7) lá, com ticket próprio (timeout 1 min).
4. **Não andar para dentro de chunk não carregado** (`PlayerMovementNoUnloaded`): em
   `handleMovePlayer`, projeta a posição (4× o deslocamento, limitado a 16 blocos); se cai num chunk
   que não existe, ignora o pacote de movimento. O jogador "esbarra" em vez de travar o servidor.
5. **Pathfinding não força carga** (`PathRecomputeChunkLoadPrevention`): `recomputePath` é cancelado
   se o alvo está em chunk não carregado.
6. **Pós-processamento atrasado** (`LevelChunkPostProcessMixin` + `EventHandler`): o
   `postProcessGeneration` (atualizar fluidos e formas de blocos na borda) precisa dos vizinhos e
   força carregá-los. O mod detecta se algum bloco realmente depende de vizinho; se sim, guarda os
   dados, zera a lista original e só aplica quando os 8 vizinhos já existem (máximo 10 chunks por
   tick, para se `!server.haveTime()`), com timeout de 60–90 s.
7. **Fila de tarefas de chunk** (`ChunkTaskPriorityQueueMixin`): troca `add`/`addAll` por inserção
   no **início** da lista (tarefas novas primeiro, LIFO) e, se a thread principal está esperando um
   chunk (`loadingChunk`), move todas as tarefas desse chunk para a fila de prioridade 1.
   `ServerChunkCacheMixin` reagenda o future do chunk esperado com prioridade e combina com
   `CompletableFuture.anyOf`.
8. Menores: cache de `RegionFile` abertos de 256 para 1024 (`@ModifyConstant`); `canConvertToSource`
   da água/lava com `@Overwrite` lendo um booleano em cache (o vanilla consulta as gamerules toda vez).

## O que aprender
- O sistema de **tickets** do `DistanceManager` é a alavanca para controlar o que fica carregado.
  Nível do ticket = `ChunkLevel.byStatus(FULL) - área`.
- **Priorização por previsão** + **ritmo adaptativo ao MSPT**.
- Evitar "thrashing" (carregar/descarregar o mesmo chunk) deixando tickets expirarem devagar.
- Bloquear a causa (movimento para área vazia, pathfinding) é mais barato que acelerar a carga.
