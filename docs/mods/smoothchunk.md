# Smooth Chunk Save (`smoothchunk`, someaddon)

Fonte estudada: `someaddons/SmoothChunkSave`, branch `1.20.1`. 6 mixins. Usa Cupboard para config.

## Problema
O vanilla tenta salvar chunks modificados em rajadas: `ChunkMap.processUnloads` percorre
**todos** os chunks visíveis e salva os que estão sujos, e `ServerChunkCache.save(false)` (autosave)
faz o mesmo. Em mundos grandes, isso vira um pico de vários ms por tick.

## Como resolve
- **Salvar espalhado no tempo** (`ChunkMapMixin`):
  - `@Redirect` no iterator de `visibleChunkMap.values()` dentro de `processUnloads`, devolvendo um
    iterator vazio (desliga o loop vanilla).
  - A cada 64 ticks varre os chunks e, para cada chunk sujo, agenda um horário de save
    `agora + chunkSaveDelay(300s) + random(0..19)s` e põe numa fila FIFO `ArrayDeque<PosTimeEntry>`.
    O horário fica gravado no próprio chunk por um campo injetado (`ChunkAccessMixin`, interface
    `IChunkTimeSave`).
  - Todo tick salva **no máximo 10** chunks cujo horário já passou.
  - O jitter aleatório evita que todos os chunks vençam no mesmo tick.
- **Autosave não força tudo** (`ServerChunkCacheMixin`): em `save(force=false)` cancela logo depois de
  `runDistanceManagerUpdates()`. Só o save forçado (`/save-all`, fechar o mundo) grava tudo.
- **Não salvar proto-chunks** (`disableProtoSave`): em `ChunkMap.save`, se o chunk não é
  `LEVELCHUNK` (geração incompleta), retorna `false`. Eles são regenerados se necessário.
- **Reparo de POI** (`PoiRepairMixin`): como pular proto-chunks pode deixar POIs (camas, postos de
  trabalho) apontando para blocos que não existem, em `checkConsistencyWithBlocks` compara cada
  registro com o bloco real e remove/recria.
- **Descarregar devagar** (`ChunkMapSlowUnload`): troca o limite vanilla de 200 descarregamentos por
  tick por `chunkUnloadLimit(20) + toDrop.size()/200` (escala se a fila crescer), aumenta o limite de
  2000 para 20000 e troca `toDrop` por um `LongLinkedOpenHashSet` (ordem de inserção).
- `PersistentEntitySectionManagerMixin`: envolve `processUnloads` de entidades em try/catch, para um
  mod quebrado não derrubar o servidor.

## O que aprender
- **Amortização**: transformar trabalho em rajada em trabalho constante e pequeno por tick, com
  orçamento fixo (10 saves/tick) e jitter.
- `@ModifyConstant` para ajustar números mágicos do vanilla sem copiar o método.
- Adicionar estado a classes do jogo com campo `@Unique` + interface "duck" (`IChunkTimeSave`).
- Trade-off consciente: atrasar o save aumenta o que se perde num crash (mitigado porque o save
  forçado ainda grava tudo).
