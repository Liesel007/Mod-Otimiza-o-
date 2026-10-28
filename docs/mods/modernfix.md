# ModernFix (`modernfix`, embeddedt)

Fonte estudada: `embeddedt/ModernFix`, branch `1.20`. ~200 mixins organizados em pacotes
`perf/` (desempenho), `bugfix/` (correções) e `feature/` (extras), cada pacote é uma **opção**
liga/desliga no arquivo `config/modernfix-mixins.properties`. As descrições oficiais ficam em
`assets/modernfix/lang/en_us.json`.

## Foco
Principalmente **tempo de inicialização** e **uso de memória** em modpacks grandes. Em menor grau,
TPS/FPS.

## Otimizações principais (estudadas no código)
- **`perf.resourcepacks` – cache do sistema de arquivos** (`PackResourcesCacheEngine`): a inicialização
  moderna é limitada por I/O: milhares de chamadas "este recurso existe?" e "liste esta pasta" em
  cada mod/resource pack. O ModernFix lê a **árvore de arquivos de cada pack uma vez**, guarda numa
  árvore de `Node` em memória (strings de caminho internadas, mapas imutáveis compactos no fim) e
  responde dali. Refeito a cada reload.
- **`perf.reduce_blockstate_cache_rebuilds` – cache preguiçoso**: o Forge manda reconstruir o cache
  de **todos** os BlockStates várias vezes (no "Freezing data" e ao entrar no mundo). O mixin só marca
  `cacheInvalid = true`; cada estado reconstrói o próprio cache **na primeira vez que alguém o lê**
  (`@Redirect` em todo `GETFIELD cache` do `BlockStateBase`, com `synchronized` e proteção contra
  recursão).
- **`perf.dynamic_resources` – modelos sob demanda** (*desligado por padrão*): em vez de "assar" todos
  os modelos de bloco/item na inicialização e mantê-los para sempre, troca os mapas do `ModelBakery`
  por caches Guava com `expireAfterAccess` e `maximumSize`. Modelos são criados quando aparecem na
  tela e descartados quando não usados. Grande economia de RAM e de tempo de carga em packs enormes,
  mas exige patches de compatibilidade (há subpacotes para CTM, LDLib, AE2...).
- **`perf.dynamic_dfu`**: o DataFixerUpper (conversor de dados de versões antigas) só é inicializado
  quando algo realmente precisa ser convertido.
- **`perf.cache_upgraded_structures`**: guarda a versão já convertida (pelo DFU) de arquivos de
  estrutura antigos de mods, com hash do original para invalidar.
- **`perf.cache_strongholds`**: salva a lista de fortalezas no mundo em vez de recalcular a cada load.
- **`perf.faster_texture_stitching`**, **`perf.model_optimizations`**, **`perf.mojang_registry_size`**,
  **`perf.fast_registry_validation`**, **`perf.blast_search_trees`** (usa a busca do JEI em vez de
  construir as árvores de busca do criativo): cortes de tempo de carregamento.
- Memória: `nbt_memory_usage` (mapa compacto para `CompoundTag` pequenos com chaves deduplicadas),
  `deduplicate_wall_shapes`, `compact_bit_storage`, `dynamic_sounds`, `dynamic_structure_manager`,
  `world_leaks` (solta mundos antigos do cliente presos por mods).
- Runtime: `ticking_chunk_alloc` e `worldgen_allocation` (menos alocação por tick),
  `thread_priorities` (threads do servidor/worker com prioridade menor que a do cliente),
  `fix_loop_spin_waiting` (espera sem queimar CPU), `faster_advancements`,
  `potential_spawns_alloc`.
- `perf.remove_spawn_chunks` existe mas é **desligado por padrão** fora do ambiente de dev (o Ksyxis
  já cobre isso no seu pack).

## O que aprender
- **Preguiça (lazy) bem feita**: invalidar barato e recalcular só quando alguém lê.
- **Cache de I/O** quando o mesmo dado (lista de arquivos) é consultado milhares de vezes.
- **Caches com expiração/tamanho máximo** para dados grandes que só parte é usada.
- Arquitetura: cada otimização como mixin isolado + opção na config. Fica fácil desligar a que der
  conflito e testar uma por vez.
- Dica para o seu pack: `mixin.perf.dynamic_resources=true` e `mixin.perf.faster_item_rendering=true`
  em `config/modernfix-mixins.properties` são ganhos opcionais (testar compatibilidade).
