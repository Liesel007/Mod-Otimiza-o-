# Fast Async World Save (`fastasyncworldsave`, someaddon)

Fonte estudada: `someaddons/fastasyncworldsave`, branch `1.20.1`. 2 mixins.

## Problema
Ao salvar, o vanilla serializa **e** grava em disco (compressão GZIP + I/O) os `SavedData`
(dados de mapa, raids, dados de mods via `DimensionDataStorage`) e o `level.dat`, tudo na thread
principal do servidor. Com mods que guardam muitos dados, cada autosave gera um pico de lag.

## Como resolve
- Um único `ExecutorService` de thread única, daemon, chamado `fastasyncworldsave`.
  Thread única garante que as escritas ficam em ordem.
- `DimensionDataStorageMixin`: `@Redirect` no `Map.forEach` dentro de `save()`.
  - Na thread principal: percorre os `SavedData`, pula os que não estão sujos, chama
    `savedData.save(new CompoundTag())` (a serialização precisa ser na thread principal porque lê
    estado do jogo), `setDirty(false)`.
  - Na thread de I/O: adiciona `DataVersion`, grava num `tmp_<nome>` comprimido e faz
    `Files.move(..., ATOMIC_MOVE)` (com fallback para `REPLACE_EXISTING`). Escrita atômica
    evita arquivo corrompido se o jogo fechar no meio.
  - Bônus: `@ModifyVariable` em `getDataFile` troca `:` por `_` no Windows/macOS
    (IDs com namespace quebravam o nome do arquivo).
- `LevelStorageSourceMixin`: em `saveDataTag`, captura o `CompoundTag` já montado (via
  `LocalCapture`), cancela o resto do método e faz a escrita do `level.dat` no executor, usando
  `Util.safeReplaceFile` (mantém `level.dat_old`).

## O que aprender
- Divisão clássica: **snapshot na thread dona dos dados, I/O em outra thread.** Nunca serialize
  estado do jogo fora da thread principal.
- Escrita em arquivo temporário + rename atômico.
- Risco: se o processo morrer antes do executor terminar, o último save se perde (a thread é daemon).
