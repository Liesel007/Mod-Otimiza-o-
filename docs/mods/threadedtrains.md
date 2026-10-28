# Create: Threaded Trains (`createthreadedtrains`, MisterJulsen)

Fonte estudada: `MisterJulsen/Create-Threaded-Trains`, branch `1.20.1` (Architectury). 10 mixins
**no Create** (não no Minecraft).

## Problema
O `GlobalRailwayManager` do Create calcula todo tick o grafo de trilhos, sinais e movimento de
**todos os trens**, na thread principal, dentro do tick do Overworld. Em bases grandes isso ocupa
boa parte dos 50 ms do tick.

## Como resolve
- **Tira o tick dos trens da thread principal**:
  - `CommonEventsMixin` anula a chamada `GlobalRailwayManager.tick(level)` do Create.
  - `MinecraftServerMixin` (início de `tickServer`) chama `preTick`, que manda
    `Create.RAILWAYS.tick(overworld)` para uma thread dedicada ("Train Worker",
    `newSingleThreadExecutor`).
  - Modo sincronizado (padrão, `SYNC_WITH_SERVER_TICK`): no fim de `tickChildren`, `postTick` espera
    (`future.get()`) a tarefa dos trens terminar. Ou seja: **trens e mundo rodam em paralelo dentro
    do mesmo tick**, com uma barreira no final. Modo dessincronizado: a próxima rodada começa assim
    que a anterior termina (mais rápido, menos determinístico).
- **Estruturas concorrentes no lugar das originais**: troca os `HashMap`/`ArrayList` internos do
  Create (`RailwaySavedData`, `TrackGraph`, `EdgePointStorage`, `SignalEdgeGroup`,
  `GlobalRailwayManager`) por `NullSafeConcurrentMap` (um `ConcurrentHashMap` que aceita `null`
  usando objetos sentinela) e `ConcurrentList` (lista com `StampedLock`, leitura otimista e iteração
  sobre snapshot). Isso é feito no `<init>` e redirecionando os `PUTFIELD` no `load`.
- **O que mexe no mundo volta para a thread principal** (`runSafelyOnMain` = `server.execute`):
  `Carriage.manageEntities` (entidades dos vagões) e explosões em colisões de trens.
- Salvamento do grafo (`SavedDataUtilMixin`) em uma thread de I/O separada, com escrita em arquivo
  temporário + `safeReplaceFile`.
- Comandos `/ctt lastTickTime`, `avgTickTime` para medir.

## O que aprender
- **Paralelismo por subsistema**: um subsistema relativamente isolado (o grafo de trilhos) pode rodar
  ao lado do tick do mundo com uma barreira no fim.
- Para isso, todos os dados compartilhados precisam ser trocados por estruturas thread-safe, e toda
  escrita no mundo precisa voltar para a thread dona.
- `StampedLock` com leitura otimista é barato quando há muitas leituras e poucas escritas.
- Mixins também servem para otimizar **outros mods** (`remap = false` para alvos fora do Minecraft).
