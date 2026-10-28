# Cupboard (`cupboard`, someaddon) — biblioteca

Fonte estudada: `someaddons/cupboard`, branch `1.20.1`. 5 mixins. É a base dos mods do someaddon
(Smooth Chunk Save, Chunk Sending, Better Chunk Loading, Connectivity, Recipe Essentials...).

## O que fornece
- **`CupboardConfig<C>`**: config em JSON com `serialize()/deserialize()` escritos à mão.
  - Grava cada `"desc:"` como comentário `//` no arquivo (o Gson lê em modo lenient).
  - Se faltar uma chave, completa com os valores padrão e salva de novo (migração automática).
  - **Recarrega sozinha**: registra a pasta num `WatchService` do Java e, no fim de cada tick do
    servidor, faz `poll()` (no máximo 1×/s). Ao detectar mudança, espera 7 polls (debounce) e
    recarrega. Ignora o evento gerado pelo próprio `save()` (`saveCounter`).
- Utilitários: `BlockSearch.findAround` (busca em espiral só em chunks carregados, sem forçar carga),
  `RegistryLookup`, `MathUtil`, `VectorUtil`.

## Mixins (robustez, não velocidade)
- `ServerAddEntityMixin`: se um mod chama `ServerLevel.addEntity` fora da thread do servidor
  (causa clássica de `ConcurrentModificationException`), a entidade vai para uma
  `ConcurrentLinkedQueue` e é adicionada na próxima chamada feita na thread certa.
- `EntityLoadMixin`: entidade com posição/rotação `NaN`/infinita no NBT é movida para 0,70,0; opção
  de pular entidades que lançam exceção ao carregar, em vez de crashar o mundo.
- `ChunkLoadDebug`: opção que loga um stack trace sempre que algo força carregar um chunk FULL
  pela thread principal. **Ferramenta de diagnóstico** para achar o mod que causa lag.
- `ThreadingDetectorMixin`: no erro "accessing from multiple threads", imprime as pilhas de todas as
  threads.
- `CommandExceptionLoggingMixin`: mostra o erro completo quando um comando falha.
- Opção `forceHeapDumpOnOOM`: liga `HeapDumpOnOutOfMemoryError` via `HotSpotDiagnosticMXBean`.

## O que aprender
- Uma lib própria com config simples e recarregável economiza muito em mods pequenos.
- Instrumentação (log de carga de chunk síncrona, dump de threads) é o primeiro passo para otimizar:
  **medir antes de mudar**.
- Mover trabalho para a thread certa com fila concorrente (mesmo padrão do GPU Memory Leak Fix).
