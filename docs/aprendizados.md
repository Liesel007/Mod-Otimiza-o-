# O que os mods de otimização do HL 1.20.1 ensinam

Resumo do estudo do código-fonte dos 31 mods de otimização (e 2 bibliotecas) do modpack.
As anotações detalhadas de cada mod estão em [`docs/mods/`](mods/).

## 1. Como eles alteram o Minecraft

Quase todos usam **Mixin** (SpongePowered), que modifica o bytecode das classes do jogo quando
elas são carregadas. As ferramentas que apareceram, da mais "educada" para a mais agressiva:

| Ferramenta | O que faz | Exemplos |
|---|---|---|
| `@Accessor` / `@Invoker` / access transformer | Ler/escrever campos privados ou chamar métodos privados | Clumps, Async Locator, AI Improvements (AT) |
| `@Unique` + interface "duck" | Adicionar campos e métodos novos a uma classe do jogo | Smooth Chunk (`IChunkTimeSave`), Entity Culling (`Cullable`), Icterine |
| `@Inject(HEAD, cancellable)` | Executar código antes e, se quiser, cancelar o original | Chunk Sending, Let Me Despawn, Dynamic FPS |
| `@ModifyConstant` | Trocar um número mágico do código | Smooth Chunk (200→20 unloads), Ksyxis (441→0 chunks), Connectivity (8 MiB) |
| `@ModifyArg` / `@ModifyVariable` | Mudar um argumento ou variável local | Better Chunk Loading (view distance 3), Fast Async World Save |
| `@Redirect` | Trocar uma chamada específica por outra | Noisiumed (`setBlockState`), Fast Async World Save (`forEach`) |
| `@WrapOperation` / `@ModifyExpressionValue` (MixinExtras) | Envolver uma chamada mantendo compatibilidade com outros mods | Structure Layout Optimizer, Particle Core, ImmediatelyFast |
| `@Overwrite` | Substituir o método inteiro | Starlight (motor de luz), FerriteCore, BadOptimizations |
| Trocar o objeto inteiro | Substituir um sistema por uma subclasse | FastSuite e Recipe Essentials (`RecipeManager`), ImmediatelyFast (`BufferSource`) |

Boas práticas que se repetem:
- **`IMixinConfigPlugin.shouldApplyMixin`** para ligar/desligar grupos de mixins pela config ou pela
  presença de outros mods (Chunk Sending, Icterine, Noisiumed, AllTheLeaks, ModernFix, Embeddium).
- **`require = 0, expect = 0`** para não crashar se outro mod já alterou o mesmo ponto (Connectivity,
  Ksyxis).
- Prefixar membros injetados com o id do mod (`smoothchunk$`, `bo$`, `clumps$`) para evitar colisão.
- Mixins também podem mirar **outros mods** (`remap = false`): Create: Threaded Trains (Create),
  AllTheLeaks (JEI, AE2, Curios...), ModernFix (CTM, KubeJS...).

## 2. Catálogo de técnicas de otimização

### Fazer menos trabalho
- **Não fazer o que ninguém vê**: Dynamic FPS (não renderiza sem foco), Entity Culling (atrás de
  paredes), More Culling (faces escondidas), Embeddium (faces de costas, grafo de visibilidade),
  Sodium Extras (distância), Particle Core (distância de partículas), Ksyxis (spawn chunks).
- **Curto-circuito / caminho rápido**: Icterine (descarta gatilhos que não podem dar conquista),
  Particle Core (partícula no ar não calcula colisão), Recipe Essentials (hash antes de `equals`).
- **"Dirty checking"** — só recalcular se algum insumo mudou: BadOptimizations (lightmap, cor do céu),
  Lithium/Canary (funis escutam mudanças de inventário).
- **Lazy (sob demanda)**: ModernFix (cache de BlockState, modelos, DFU), Embeddium (só anima texturas
  visíveis).

### Fazer o trabalho de forma mais barata
- **Cache / memoização**: FastSuite e Recipe Essentials (receitas), Chunk Sending (pacote do chunk),
  Recipe Essentials (hash de NBT com invalidação pai→filho), Particle Core (luz por bloco por tick),
  ModernFix (lista de arquivos dos resource packs).
- **Tabelas pré-calculadas (LUT)**: AI Improvements (`atan2`), Canary (seno).
- **Estrutura de dados melhor**: Structure Layout Optimizer (octree no lugar de VoxelShape),
  FerriteCore (estado como índice numa matriz), Starlight (fila `long[]` empacotada).
- **Menos alocação / GC**: Starlight, Embeddium (memória nativa), Canary (`alloc.*`), Noisiumed
  (`for` indexado).
- **Deduplicação / interning**: FerriteCore (shapes, predicados, quads, strings), ModernFix,
  AllTheLeaks (opcional).
- **Agrupar (batching)**: ImmediatelyFast (draw calls por camada, atlas de mapas), Embeddium
  (multi-draw por região), Particle Core (vértice em uma escrita).
- **Quantizar dados**: Embeddium (vértice de 20 bytes com `short`).

### Espalhar ou mover o trabalho
- **Amortizar no tempo** com orçamento por tick + jitter: Smooth Chunk (10 saves/tick), Chunk Sending
  (15 chunks/jogador/tick, 80 no total), Better Chunk Loading (ritmo ajustado pelo MSPT), Chunk Sending
  (mapas em molduras com `tick + id`).
- **Outra thread, resultado aplicado na principal**: Async Locator (`CompletableFuture` +
  `server.submit`), Fast Async World Save e Threaded Trains (I/O), Entity Culling (ray tracing).
- **Paralelismo por subsistema com barreira**: Create: Threaded Trains.
- **Paralelismo só no que é seguro** + timeout + fallback: FastSuite. Com autodetecção de falha:
  Particle Core.
- **Antecipar (pré-computar)**: Chunky (pré-geração), More Culling (análise das texturas na carga),
  Icterine (limiares lidos dos datapacks), ModernFix (estruturas e fortalezas em cache).
- **Priorizar o que importa**: Chunk Sending (chunks na direção do olhar), Better Chunk Loading
  (previsão de movimento), Embeddium (compila primeiro o que está perto).

### Remover sincronização desnecessária
Noisiumed (seção recém-criada), BadOptimizations (`SynchedEntityData` no cliente), Canary
(`chunk.no_locking`). Só é seguro quando o objeto **ainda não foi publicado** para outras threads ou
quando só uma thread o usa.

### Memória
FerriteCore e ModernFix (estruturas compactas, dedup, lazy), AllTheLeaks e GPU Memory Leak Fix
(vazamentos), Let Me Despawn e Clumps (menos entidades acumuladas).

### Medir antes de otimizar
Starlight (contadores de leituras, LightBench), FastSuite (benchmark embutido), AI Improvements
(`MainPerformanceTest`), Threaded Trains (`/ctt avgTickTime`), Entity Culling e BadOptimizations (F3),
Cupboard (log de carga síncrona de chunk), Connectivity (estatísticas de rede).

## 3. Riscos e trade-offs vistos no código

| Mod | Trade-off |
|---|---|
| Smooth Chunk | Atrasa saves (até ~5 min); num crash, perde mais progresso. Não salva proto-chunks. |
| Fast Async World Save | Thread daemon: se o processo morrer antes de terminar a escrita, o último save se perde. |
| Ksyxis | Spawn chunks deixam de ficar carregados: fazendas no spawn param. |
| Connectivity | Remove limites de tamanho de pacote e de NBT (proteção contra pacotes gigantes). |
| BadOptimizations | Lock no-op em `SynchedEntityData`: quebra se algum mod acessar fora da thread do cliente. |
| Async Locator | Busca de estrutura fora da thread principal não é thread-safe garantido. |
| Starlight | Substitui o motor de luz inteiro; na 1.20+ o próprio autor diz que o ganho sobre o vanilla é pequeno. |
| Entity Culling | Resultado aproximado (thread sem sincronização), mitigado por timeout de 1 s. |
| AI Improvements | `atan2` aproximado (~0,2°); remoção de goals muda o comportamento (desligada por padrão). |

## 4. Interações entre os mods do seu pack

- **FastSuite × Recipe Essentials — provável conflito.** Os dois substituem o `RecipeManager`.
  O FastSuite troca o inicializador do campo em `ReloadableServerResources`/`ClientPacketListener`;
  o Recipe Essentials atribui o seu **no fim do construtor** (`@Inject` em `RETURN`), que roda depois.
  Pelo código, só o gerenciador do Recipe Essentials fica ativo e a busca paralela do FastSuite não é
  usada. Para confirmar: se o `latest.log` **não** tiver linhas `Constructed recipe list for ...` do
  FastSuite ao abrir uma mesa de trabalho, ele está inativo.
- **Ksyxis × ModernFix**: os dois mexem nos spawn chunks. O Ksyxis detecta a opção do ModernFix
  (`perf.remove_spawn_chunks`, desligada por padrão) e se ajusta. Sem conflito.
- **Noisiumed × Canary**: o Noisiumed detecta o Canary e usa a variante compatível. Sem conflito.
- **Particle Core × Embeddium**: o Particle Core desliga sua otimização de rotação quando o Embeddium
  está presente. Sem conflito.
- **More Culling × Embeddium**: o More Culling tem mixin de compatibilidade para o cache de oclusão do
  Embeddium. Sem conflito.
- **Entity Culling + Sodium Extras**: complementares (oclusão × distância).
- **Better Chunk Loading + Chunk Sending + Smooth Chunk + Cupboard**: mesmo autor, desenhados para
  funcionar juntos (carregar → enviar → salvar).
- **Recipe Essentials × AllTheLeaks**: os dois têm mixins em `ByteArrayTag`, `IntArrayTag`,
  `LongArrayTag` e `CompoundTag`. Não encontrei conflito direto, mas é um ponto a observar se aparecer
  crash ligado a NBT.

## 5. Ajustes opcionais que o estudo sugere (testar antes)

- `config/modernfix-mixins.properties`: `mixin.perf.dynamic_resources=true` (menos RAM e carga mais
  rápida em packs grandes) e `mixin.perf.faster_item_rendering=true`.
- Pré-gerar o mundo com Chunky antes de jogar (ex.: `/chunky radius 3000` e `/chunky start`).
- Se notar travadas ao salvar, o `chunkSaveDelay` do Smooth Chunk (`config/smoothchunk.json`) controla
  o atraso.

## 6. Técnicas usadas nos módulos da 0.1.4

- **`correcao_infinity_chest` (Re-Avaritia 1.4.2, licença MIT):** o problema era um cache que guardava
  uma chave derivada (`id#hash`) onde deveria guardar só o id. A correção é o cache só aceitar a forma
  canônica, recalculada a partir do registro, e usar `ConcurrentHashMap` (o cache original é usado por
  duas threads no singleplayer). Os dados que já foram salvos errados são consertados ao carregar
  (normalizar e somar duplicatas), com cópia `.hlbak` antes. A lista de chaves passou a ser reconstruída
  só quando é lida depois de mudar (*dirty checking*, como no BadOptimizations e no Canary), e não a
  cada item novo.
- **Detector de mixins com falha:** `Mixins.registerErrorHandlerClass` recebe toda falha de aplicação de
  qualquer mod, sem precisar ler o log. Uma tabela de correções conhecidas transforma a exceção numa
  instrução para o jogador.
- **`limpeza_itens` (ideia dos plugins ClearLag):** a remoção é espalhada em lotes por tick (como o Smooth
  Chunk faz com os salvamentos) e usa `discard()`, sem som nem partículas. Os drops da morte de jogadores
  recebem uma marca (`LivingDropsEvent`, NBT persistente da entidade) para nunca serem apagados.
