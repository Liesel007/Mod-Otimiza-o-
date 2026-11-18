# HL Otimização: relatório para a 0.5.1 (correções da 0.5.0)
### Base: sessão de 16/11/2026, 03:37–03:54, HLO 0.5.0, 426 mods, -Xmx 6144 MB

A 0.5.0 carregou sem erro e os módulos novos apareceram no resumo. Esta sessão mostrou **três bugs de lógica** nos itens novos e alguns ajustes. É uma versão de correção: nada de módulo novo.

---

## Bug 1: a sugestão de memória manda **aumentar** o heap quando o problema é falta de RAM (prioridade alta)
Resumo do fechamento:
```
monitor_gc: 45 GC pause(s) >= 100 ms (1 full, 0 from System.gc()), 38 process freeze(s) without GC, longest pause 4835 ms; 27 of 38 freezes happened with free system RAM below 1 GB
monitor_gc: memory: uses ~4.2 GB, reserved 6.0 GB, suggested 7.0 GB (RAM 11.8 GB, integrated GPU) - at the RAM limit: close other programs or remove mods
```
Três erros juntos:
1. **"uses ~4.2 GB" está superestimado.** O valor veio do heap depois de coletas *Young* (5.955→4.103, 5.942→4.087, 5.900→4.054 MB), que ainda contêm lixo da geração velha. A **única coleta completa** da sessão, às 03:55:00, deixou **3.559 MB**: esse é o conjunto vivo.
   - **Correção:** quando houver Full ou Mixed na sessão, usar o **máximo pós-Full/Mixed**. Pós-Young só como estimativa quando não houver nenhuma, marcando "(estimado)". Nunca misturar os dois.
2. **O teto não foi aplicado.** Com GPU integrada o teto é 50% de 11,8 GB ≈ 5,9 GB, mas a sugestão saiu 7,0 GB. O `clamp` precisa acontecer **depois** do arredondamento, e o valor final nunca pode passar do teto. Se `conjunto_vivo × 1,6` passar do teto, a sugestão é o teto, com o aviso "no limite".
3. **Contradição com a RAM livre.** 27 de 38 congelamentos aconteceram com menos de 1 GB livre no sistema. Nessa situação, **nunca** sugerir aumentar o `-Xmx`; isso piora a paginação.
   - Regra: se mais de 30% dos congelamentos foram com RAM livre < 1 GB, a sugestão é `min(atual, sugerido)` e a mensagem principal vira "feche outros programas: o Windows está sem RAM livre".
   - Mostrar no painel o que mais ocupava RAM, se possível. Na Windows API dá para listar processos via `ProcessHandle` + `OperatingSystemMXBean`. Se não der, só o total livre.

**Com os dados desta sessão, o certo seria:** `uses ~3.6 GB, reserved 6.0 GB, suggested 6.0 GB (keep) - 27 of 38 freezes with free RAM below 1 GB: close other programs (browser, Discord) while playing`.

**Teste no `HLBench`:** alimentar valores fixos (Young pós-GC 4.103, Full pós-GC 3.559, RAM 11.800, GPU integrada, 27/38 congelamentos com pouca RAM) e conferir "suggested 6.0 GB (keep)".

## Bug 2: `salvamento_dimensoes_vazias` considera todas as dimensões ativas (prioridade alta)
```
salvamento_dimensoes_vazias: 0 dimension save(s) skipped in total; last save: 78 dimensions, 78 active, 0 skipped on the last save
salvamento_assincrono: main thread time of classes with their own save(File): ConduitSavedData 1344 ms (156x, longest 73 ms)
```
O jogador ficou só no Overworld, mas as 78 dimensões foram marcadas como ativas. O `ConduitSavedData` continua sendo salvo uma vez por dimensão (156 = 2 salvamentos × 78).

**Suspeita principal:** a marcação de "atividade" usa `SavedData.isDirty()` da dimensão, e o `ConduitSavedData` do Ender IO (e talvez outros) volta sujo sempre. A dimensão aparece como ativa **por causa do próprio dado que queríamos pular**, um ciclo. Outras causas possíveis:
- chunks forçados (tickets de outros mods)
- o Overworld e as dimensões de "carregamento de spawn"
- o contador de chunks carregados incluindo chunks em `ChunkHolder` que já não estão em uso

**Pedido:**
1. **Log de diagnóstico** (uma vez por sessão, nível INFO): para cada dimensão marcada ativa, o **motivo**:
   `deepfield:pluto active: dirty SavedData [enderio:conduits]`, `lantea:abydos active: 4 forced chunk(s) [lantea]`, `minecraft:overworld active: player present`.
2. **"Sujo sempre" não pode contar como atividade.** Para a decisão de pular a dimensão, ignorar SavedData que estava sujo em **todos** os últimos N salvamentos e cujo CRC do NBT não mudou (o hash do item 3).
3. **CRC por SavedData com `save(File)` próprio** (pedido na 0.5 e ausente no resumo): se o NBT for igual ao da última gravação, não escrever o arquivo, e contar `ConduitSavedData: N of 156 writes skipped (unchanged)`. Se já estiver implementado, o contador não está aparecendo no resumo.
4. **Pronto quando:** com o jogador parado no Overworld, o resumo mostra algo como `78 dimensions, 2 active, 76 skipped`, e o Esc trava menos de 500 ms. Ao fechar o mundo, todas continuam gravadas.

## Bug 3: o relatório de inicialização não acha o tempo do JEI (prioridade média)
```
[relatorio_inicializacao] Startup: game 286.1 s, menu to world 98.5 s, total 384.7 s, JEI ?. Slowest:
```
A linha existe, mas **em minutos**: `[03:39:21] Starting JEI took 1.063 minutes`. Quando passa de 60 s, o JEI troca a unidade de `seconds` para `minutes` (na sessão de 15/11 foi `1.490 minutes`). O parser do HLO só reconhece `seconds`.

**Pedido:**
- Aceitar as unidades `milliseconds`, `seconds` e `minutes`, e converter para segundos (nesta sessão, 63,8 s).
- Se a linha não existir, calcular **do `Starting JEI...` até a última `Sending Runtime`**, marcando "(medido pelo log)".
- Melhor ainda, ouvir os eventos do JEI quando o plugin do HLO estiver ativo.
- Somar ao top 10 os plugins mais lentos em `Registering recipes: <plugin> took X` e `Registering categories: <plugin> took X`. Hoje só aparece `ModifyBakingResult`.

---

## Ajustes menores
1. **`relatorio_conflitos`: dois mods de mapa.** Xaero's Minimap/World Map e FTB Chunks estão juntos. O minimapa do FTB Chunks está desligado (`local/ftbchunks/client-config.snbt`, `minimap.enabled: false`), mas ele continua mapeando o mundo e gravando regiões **na thread de render**:
   `Slow frame: 1815 ms. Likely cause: ftbchunks (client tick) - MapRegionData.write [204 sample(s)]`.
   Detectar "Xaero + FTB Chunks" e avisar: "FTB Chunks also builds its own map (writes on the render thread). If you only use it for claims, lower its map work." Citar `rerender_queue_ticks` e `region_release_time` só se forem as opções certas: conferir no jar do FTB Chunks antes de sugerir.
2. **AllTheLeaks:**
   - A coleta completa ao fechar caiu para 4,8 s e foi identificada corretamente ("Probably AllTheLeaks, which does this when the server stops").
   - Também houve `Slow frame: 613 ms ... alltheleaks - Trackable$2.trim` durante o jogo.
   - A config dele (`config/alltheleaks.json`) tem `logIntervalInMinutes: 10`. Conferir no jar se essa varredura periódica é o que causa o `trim` no cliente e, se for, sugerir no painel aumentar o intervalo ou desligar (0, se o mod aceitar).
3. **Infinity Chest (Avaritia) ao fechar: 8,0 s**, `compression while saving 30%, requested by avaritia (ServerChestManager.lambda$save$6)`. Na sessão anterior foram 4,1 s.
   - O baú tem cerca de 1.331 entradas (`correcao_infinity_chest`). Medir e mostrar no resumo o tamanho do arquivo do baú e o tempo de gravação.
   - Se o tempo crescer de sessão para sessão, avisar: "Infinity Chest is getting big (N entries, X MB)".
4. **Sophisticated Backpacks:** travamento de 2,5 s em `BackpackStorage$$Lambda...<init>` no tick, às 03:39:30, logo depois de entrar no mundo. Só registrar a causa no resumo, como "first load of backpack storage". É do mod; não tentar corrigir.
5. **RAM livre abaixo de 1 GB já no carregamento** (`[03:35:19] System free RAM below 1 GB (810 MB)`, antes de entrar no mundo). Repetir essa linha no resumo do fechamento ("free RAM was below 1 GB for X% of the session"). Hoje ela só aparece uma vez, no meio do log.

## Critérios de pronto (0.5.1)
1. Sugestão de memória: os valores desta sessão geram "suggested 6.0 GB (keep)" com aviso de fechar programas (teste no `HLBench`).
2. Dimensões vazias: log com o motivo de cada dimensão ativa; com o jogador parado, a maioria pulada; contador de gravações iguais puladas no resumo.
3. Tempo do JEI aparece também quando o log escreve em `minutes` (63,8 s nesta sessão), e os plugins mais lentos entram no top 10.
4. Nada muda fora do HLO sozinho: configs do FTB Chunks, AllTheLeaks e `-Xmx` continuam sendo **sugestões**.
