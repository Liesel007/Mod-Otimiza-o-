# HL Otimização (mod para Forge 1.20.1)

Mod de otimização feito a partir do estudo do código dos mods do modpack HL
(ver [`docs/aprendizados.md`](../docs/aprendizados.md)). Ele reúne técnicas desses mods com
**código próprio** (sem copiar código dos outros mods, que têm licenças diferentes) e **corrige erros**
encontrados neles.

**Jar pronto:** [`releases/hlotimizacao-0.1.5.jar`](../releases/hlotimizacao-0.1.5.jar). Basta colocá-lo
na pasta `mods` da instância.

## Como ele convive com os mods do pack

Cada módulo tem três modos em `config/hlotimizacao.properties` (criado na primeira execução):

- `auto` (padrão): **liga, a menos que o mod original que faz a mesma coisa esteja instalado**.
  Assim não duplica nem conflita com o que já está no pack.
- `on`: força ligado (avisa no log se o mod original também estiver instalado).
- `off`: desliga.

O log mostra, na inicialização, o estado de cada módulo e o motivo
(`Modulo despawn_itens: desligado automaticamente: letmedespawn ja faz isso`).

## Módulos

| Módulo | Inspirado em | Desliga sozinho se houver | Lado |
|---|---|---|---|
| `despawn_itens` | Let Me Despawn | `letmedespawn` | servidor |
| `gatilho_inventario` | Icterine | `icterine` | servidor |
| `salvamento_assincrono` | Fast Async World Save | `fastasyncworldsave` | servidor |
| `vazamento_gpu` | GPU Memory Leak Fix | `gpumemleakfix` | cliente |
| `fps_sem_foco` | Dynamic FPS | `dynamic_fps` | cliente |
| `toasts_vazios` | BadOptimizations | `badoptimizations` | cliente |
| `correcao_fastasyncworldsave` | (correção) | só liga se `fastasyncworldsave` estiver instalado | servidor |
| `relatorio_conflitos` | (diagnóstico) | — | servidor e cliente |
| `correcao_infinity_chest` | (correção) | só liga se o `avaritia` (Re-Avaritia) estiver instalado | servidor e cliente |
| `limpeza_itens` | ClearLag | `clearlag`, `clearlagg`, `itemclearer`, `clear_lag` | servidor |
| `tooltip_seguro` | (correção) | — | cliente |
| `diagnostico_lag` | (diagnóstico) | — | servidor |

## Erros dos outros mods que este mod corrige

| Mod original | Erro encontrado no código | Como o HL Otimização faz |
|---|---|---|
| Let Me Despawn | Tira a persistência de **qualquer** mob que pega um item, inclusive mobs que já eram persistentes por outro motivo (spawners de estruturas, outros mods). | Lembra se o mob já era persistente antes de pegar o item e só desfaz a persistência causada pelo item. |
| Let Me Despawn | Ao desaparecer, solta **todo** o equipamento, inclusive o que o mob já tinha ao nascer (gera lixo de itens). | Solta só o que o mob pegou (chance de drop > 1, marcada pelo próprio Minecraft). |
| Let Me Despawn | Guarda "pegou itens" num campo que **não é salvo**: depois de recarregar o mundo, o mob some levando os itens. | Usa a chance de drop, que o Minecraft já salva no NBT do mob. |
| Icterine | Ignora o gatilho quando a pilha esvazia, mas conquistas do tipo "ter N slots vazios" podem ser obtidas **justamente** ao esvaziar. | Só ignora quando o jogador não tem nenhuma conquista pendente que possa ser satisfeita esvaziando uma pilha (vazios com mínimo, ocupados/cheios com máximo). A resposta fica em cache por jogador. |
| Fast Async World Save | Grava numa thread daemon e **nunca espera** ela terminar: fechar o jogo logo depois de salvar pode perder o último save. | Espera as gravações pendentes quando o servidor para. Com o FAWS instalado, o módulo `correcao_fastasyncworldsave` faz isso **para a fila dele**. |
| Fast Async World Save | Chama `save(CompoundTag)` direto e **ignora** mods cujo `SavedData` sobrescreve `save(File)` (formato próprio). | Detecta essas classes e as salva do jeito delas. |
| Fast Async World Save | Entrega para a outra thread o mesmo `CompoundTag` que o mod devolveu; se o mod continua alterando esse tag, a gravação pode sair corrompida. | Copia o tag na thread principal antes de gravar. |
| GPU Memory Leak Fix | Usa `finalize()`, depreciado no Java e mais lento para o GC. | Usa `java.lang.ref.Cleaner`, e só enfileira os IDs; a liberação acontece na thread de render. |
| FastSuite + Recipe Essentials | Os dois substituem o `RecipeManager`; só o do Recipe Essentials fica ativo e a busca paralela do FastSuite não é usada. A opção `cacheRecipes` do Recipe Essentials também não desliga essa troca. | `relatorio_conflitos` mostra no log qual gerenciador está ativo e recomenda remover um dos dois. |

O `relatorio_conflitos` também avisa sobre Starlight, Ksyxis e Connectivity (ver `docs/aprendizados.md`), e
desde a 0.1.2 verifica na inicialização:
- arquivos cujo nome indica outra versão do Minecraft; vira alerta só se o mod não declarar uma faixa que inclua
  a 1.20.1 (quando declara, sai como informação "provavelmente ok", porque o Forge já confere essa faixa);
- dependências opcionais instaladas fora da versão exigida (ex.: Polymorph com JEI mais antigo que o pedido);
- End Enchanting Table junto com Apotheosis ou Tool Leveling+;
- Rechiseled: Create sem o Rechiseled.

## Novos módulos da 0.1.2

**`tooltip_seguro`** — protege contra o crash `NullPointerException` em `StringDecomposer.iterateFormatted`
(`"p_14312_" is null`), causado por uma linha de tooltip `Component.literal(null)`. A linha vira uma linha vazia e
o log avisa **uma vez por item**, com o id do item e a origem:
- `o proprio item (mod X)`: a linha nula veio do próprio item (caso do Silent Gear `blueprint_package`);
- `outro mod, por um ouvinte do ItemTooltipEvent` ou `pelo RenderTooltipEvent.GatherComponents`: veio de outro
  mod. O Forge não informa qual ouvinte adicionou a linha, então nesses casos não dá para dizer o mod exato.

**`diagnostico_lag`** — quando o servidor fica parado mais que `diagnostico_lag.limite_ms` (padrão 2000):
1. **durante** o travamento, registra a pilha da thread do servidor (mostra qual código está segurando o tick,
   por exemplo a geração de chunks de uma dimensão);
2. **ao fim** do tick lento, registra por dimensão: jogadores, chunks prontos e parciais, chunks carregados e
   **gerados durante o travamento**, entidades (total e as 5 mais comuns) e block entities que tickam (total e as
   5 mais comuns).

Também detecta travamentos **entre** ticks (tarefas de chunk executadas enquanto o servidor espera o próximo tick).
Ignora o jogo pausado no singleplayer. No máximo 1 registro a cada `diagnostico_lag.intervalo_s` (padrão 30).

## Comando `/hl` (0.1.4)

`/hlotimizacao` continua funcionando como apelido. Se outro mod já usar `/hl`, fica só `/hlotimizacao` e o log avisa.

| Comando | Quem pode | O que faz |
|---|---|---|
| `/hl status` | todos | visão geral: TPS, MSPT, memória, sessão, módulos, limpeza, lag e avisos (cores, dicas e cliques) |
| `/hl status modulos` | todos | uma linha por módulo, com o motivo dos desligados; detalhes ao passar o mouse |
| `/hl status desempenho` | todos | TPS, MSPT (mín/máx sem os ticks de salvamento), último salvamento, memória, pausas de GC, dimensões, Java |
| `/hl status lag` | todos | travamentos da sessão: total, pior, mods mais culpados e os últimos 3 |
| `/hl status avisos` | todos | todos os avisos do relatório de conflitos e os que aparecem durante o jogo, com a correção ao passar o mouse |
| `/hl recarregar` | operador | relê da config os limites, listas e intervalos (ligar/desligar módulo exige reiniciar) |
| `/hl limpar` | operador | limpa os itens do chão daqui a 10 s (`limpeza_itens`) |
| `/hl limpar cancelar` | operador | pula a próxima limpeza |
| `/hl limpar status` | operador | tempo até a próxima limpeza e quantos itens há no chão |

## Comando e resumo (0.1.3)

- `/hlotimizacao status` mostra o que cada módulo fez desde que o jogo abriu (mobs liberados e itens devolvidos
  pelo `despawn_itens`, framebuffers/texturas liberados pelo `vazamento_gpu`, itens corrigidos pelo `tooltip_seguro`,
  arquivos gravados fora/dentro do thread principal pelo `salvamento_assincrono`, travamentos registrados).
- O mesmo resumo vai para o log ao fechar o mundo (`Resumo desde que o jogo abriu:`).

## Como foi testado

Teste de antes e depois com números: [`docs/benchmark.md`](../docs/benchmark.md).


- Compilado com ForgeGradle 6 contra Forge 1.20.1-47.3.0; o jar final foi reobfuscado para nomes SRG.
- **Servidor dedicado** de teste: todos os mixins de servidor aplicados sem erro. Testes com comandos:
  - um zumbi não persistente que pegou uma espada continuou não persistente (`PersistenceRequired: 0b`,
    no vanilla seria `1b`), com chance de drop `2.0` na espada;
  - um zumbi que **já era** persistente pegou uma espada e continuou persistente (`1b`), ou seja, o erro do
    Let Me Despawn não acontece;
  - `data modify storage` + `save-all flush`: o arquivo foi gravado pela thread assíncrona, com os dados e o
    `DataVersion`, sem arquivo temporário sobrando; o servidor parou normalmente.
- **Cliente** (tela virtual Xvfb, render por software): os 3 mixins de cliente aplicados, o jogo chegou ao
  carregamento de recursos e rodou ~10 minutos sem nenhum erro vindo do mod.
- **Não testado**: o drop dos itens ao desaparecer (exige um jogador conectado), o limite de FPS na prática
  e o funcionamento junto com o pack HL completo. Teste num mundo de cópia antes de usar no mundo principal.

## Histórico de versões

### 0.1.5
`/hl status` reorganizado em páginas (ver a tabela de comandos acima):
- **Visão geral** que cabe numa tela de chat: TPS, MSPT e memória com cores (verde/amarelo/vermelho), tempo de
  sessão, módulos ligados/desligados, próxima limpeza de itens, resumo do lag e dos avisos. Cada linha leva à
  página de detalhes com um clique.
- **Módulos** com nome amigável, ✔/✘/– e o motivo dos desligados ("desligado: Icterine já faz isso"); ao passar o
  mouse, a descrição, a linha da config e o texto completo. No servidor dedicado, os módulos só de cliente mostram
  "(só no cliente)".
- **Pico de MSPT sem os ticks de salvamento:** os ticks em que o mundo foi salvo aparecem à parte ("último
  salvamento: 2,1 s às 05:59:31"), para um único autosave lento não parecer um problema grave.
- **Lag:** o `diagnostico_lag` guarda os últimos 20 travamentos e o tempo atribuído a cada mod na sessão.
- **Avisos:** os avisos do relatório de conflitos (que antes só iam para o log da inicialização) ficam guardados,
  junto com os que aparecem durante o jogo (classes síncronas, arquivos lentos, pausas longas de GC).
- Novo: pausas da coleta de lixo acima de 500 ms são contadas (`GarbageCollectorMXBean`).
- Números em português (`1.204`, `38,2`), tempos legíveis (`1h 27min`, `6min 12s`), linhas de até ~55 caracteres.
- No console do servidor, a mesma saída sem `✔ ✘ ⚠ ━ · …`.

Testes (servidor dedicado): o `/hlbench painel` monta as 5 páginas no modo chat e no modo console com contadores
falsos e grandes: 92 linhas, nenhuma acima de 60 caracteres, nenhum `null`, nenhum símbolo no console, 30 partes
clicáveis e 142 com dica. As páginas foram conferidas no console com dois travamentos reais, um `save-all` e os
avisos da inicialização. Não testado aqui: o chat de um cliente de verdade (cores, dicas e cliques foram conferidos
nos componentes, não na tela).

### 0.1.4
Feita a partir do documento de ideias para a 0.1.4 (log de 31/10 e save "New World").

**Novo módulo `correcao_infinity_chest`** (Re-Avaritia 1.4.2; liga sozinho só se ele estiver instalado).
Li o código do Re-Avaritia 1.4.2 (MIT, `Nova-Committee/Re-Avaritia`, tag `v1.4.2-release-forge`) e
conferi com o jar do Modrinth. Causa das chaves `id#hash#hash#...`: `StorageUtils.getItem(chave)` grava a
chave inteira no cache item → id; depois `getItemId(item)` devolve `id#hash` e `getNbtItemId` acrescenta
outro `#hash`. Cada leitura do baú pode fazer a chave crescer de novo, o mesmo item vira várias entradas e o
estado enviado ao abrir o baú cresce sem limite (o travamento de 89,9 s em `S2CInfinityChestStatePack.write`).
O bug depende da ordem em que o cache é preenchido, por isso aparece em uns baús e em outros não. Correções:
- caches próprios e thread-safe para item ↔ id (o original usa `HashMap` comum, acessado pelas threads do
  cliente e do servidor ao mesmo tempo no singleplayer);
- ao carregar o mundo, cada chave é refeita como `id#hash(NBT)` a partir do id puro; entradas que ficam
  iguais são somadas; itens sem NBT guardados com `#hash` voltam para a chave `id`. Antes, uma cópia
  `<arquivo>.dat.hlbak` do original (só na primeira vez) e uma linha no log por baú corrigido;
- **outro bug encontrado:** ao tirar só parte de uma pilha de itens com NBT, o `takeItem`/`saveTakeItem`
  apagava o NBT guardado, e o resto da pilha saía depois **sem** NBT (nome, encantamentos). Agora o NBT só
  é apagado quando a entrada inteira sai;
- a lista de itens do baú é reconstruída só quando é lida depois de mudar, e não a cada item novo.
- Não feito: enviar o estado do baú em páginas (item 5 do documento, mais arriscado).

**Novo módulo `limpeza_itens`** (ligado em `auto`; desliga se outro mod de limpeza estiver instalado):
a cada `limpeza_itens.intervalo_min` (10) minutos apaga os itens soltos no chão, com avisos no chat aos
60, 30, 10 e 5 s. Nunca apaga: itens com menos de `idade_minima_s` (30) s no chão, drops da morte de
jogadores (marcados no `LivingDropsEvent`; o Corpse usa entidade própria e não é afetado), a lista
`itens_protegidos`, ovos de spawn, shulker boxes, itens encantados, com nome ou com conteúdo, e itens sobre
funis ou blocos que recebem itens. Remove no máximo `max_por_tick` (500) por tick, com `discard()`.
Não feito: proteção de áreas do FTB Chunks.

**`relatorio_conflitos`, novos detectores:**
- mixins de qualquer mod que falharam ao aplicar, com explicação e, quando conhecida, a correção (ex.:
  Better Chunk Loading × Canary → `mixin.world.chunk_access=false` em `config/canary.properties`);
- arquivo feito para uma versão **mais nova** que a 1.20.1 (ex.: `ImmediatelyFast-Forge-1.5.5+1.20.4`) vira
  aviso mesmo quando o mod aceita carregar; versões mais antigas que declaram aceitar a 1.20.1 continuam
  como informação;
- argumentos da JVM (`-Xms` muito menor que `-Xmx`, nenhuma opção de GC) e Java 17 anterior ao 17.0.12;
- antivírus: abrir arquivos para gravar levando mais de 500 ms três vezes (medido no `salvamento_assincrono`
  e nas amostras do `diagnostico_lag`).

**`diagnostico_lag`:** linha `Tipo:` quando as amostras estão abrindo arquivos (disco/antivírus) ou
carregando classes (acontece uma vez, normalmente logo depois de entrar no mundo).

**Comando `/hl`** (tabela acima), com `/hl status` mostrando MSPT médio e pico.

Testes (servidor dedicado com o Re-Avaritia 1.4.2 do Modrinth, mundo novo em cada modo):

| Teste | Módulo desligado (Re-Avaritia original) | Módulo ligado |
|---|---|---|
| mesmo papel com nome colocado 20 vezes no baú | 20 entradas, chave com até 355 caracteres | 2 entradas (com nome e sem nome) |
| papel sem nome colocado depois | guardado com chave `#hash` errada | chave `minecraft:paper` |
| tirar 3 de 10 papéis com nome, depois o resto | o resto sai sem o nome | o resto mantém o nome |
| baú sintético: 600 entradas (100 itens com chave inflada 3 vezes) | 600 entradas, pacote de 112 KB, 10,7 ms para montar | 400 entradas, pacote de 41 KB, 2,0 ms; `.hlbak` criado |

- `limpeza_itens`: 5.000 itens em 100 chunks (com 100 de cada tipo protegido) e 20 recém-largados:
  4.161 pilhas comuns apagadas (38.217 itens) em 33 ms no total, espalhados em 10 ticks (máximo de 8,1 ms
  num tick); sobraram todos os encantados, com nome, nether stars, drops de morte e recém-largados. Avisos de
  10 e 5 s, `/hl limpar cancelar` e `/hl limpar status` conferidos.
- Detector de mixins: um mixin de teste que falha de propósito foi listado com o motivo. Detector de JVM
  conferido (o ambiente de teste não tem `-Xms` nem opções de GC). Java antigo: não aparece aqui (o
  ambiente tem Java 17 recente).
- `/hl status`, `/hl recarregar` e `/hlotimizacao status` conferidos.
- Não testado aqui: o baú real do seu save (não tenho o arquivo), o jogo com cliente e o detector de
  antivírus com um antivírus de verdade (o ambiente é Linux).
- Observação do teste: drops de morte que se juntam com itens comuns do mesmo tipo podem perder a marca
  (16 de 100 no teste, ao se fundirem com pedregulho ao lado). Drops de morte reais caem juntos, então isso
  deve ser raro.

### 0.1.3
Feita a partir do relatório do `latest.log` de uma sessão de 1h18 com ~440 mods.

`diagnostico_lag`:
- **Pausa e servidor ocioso não contam mais.** Fora do tick, só conta o tempo em que a thread do servidor está
  trabalhando sem parar; parada esperando (jogo pausado, servidor ocioso) zera a contagem. Na 0.1.2, ficar parado
  e depois trabalhar 1 s gerava "travado há 45 s / tick de 58 s" (os maiores números do log eram esses).
- **Abrir e fechar o mundo não contam mais.** Nada é registrado entre o `ServerStoppingEvent` e o
  `ServerStartedEvent`, nem durante `diagnostico_lag.tolerancia_inicio_s` (padrão 30) depois de abrir o mundo e
  depois do primeiro jogador entrar.
- **Provável causa:** tira uma amostra da pilha a cada 250 ms enquanto o travamento durar (até 20) e mostra o mod
  dono do primeiro frame que não é do Minecraft, Forge ou Java, com a porcentagem
  (`Provavel causa: pehkui 85% (virtuoel.pehkui...ScaleData.getScale), avaritia 15% (...)`). Handlers de mixin
  injetados em classes do Minecraft contam para o mod do mixin.
- Marca quando o travamento aconteceu durante um salvamento do mundo ("Saving and pausing game" ou automático),
  porque aí a causa costuma ser gravação em disco.
- Relatório compacto: só as dimensões com jogadores, chunks ou entidades; as outras viram `+N dimensao(oes) vazia(s)`.
  Block entities "dormindo" do Canary (`<canary_sleeping>`) aparecem pelo tipo real, com um contador `(N dormindo)`.

`salvamento_assincrono`:
- Avisa uma vez por classe que cai na gravação síncrona por sobrescrever `save(File)` (ex.: `ConduitSavedData` do
  EnderIO), para o usuário saber onde o módulo não atua.
- Nova opção `salvamento_assincrono.serializar_classes` (vazia por padrão): classes listadas passam a ser
  serializadas com `save(CompoundTag)` e gravadas em outra thread, **ignorando** o `save(File)` do mod. Só use se
  souber que esse `save(File)` apenas chama o do Minecraft.

Outros:
- Resumo ao fechar o mundo e comando `/hlotimizacao status` (acima).
- `relatorio_conflitos` aponta o link do projeto em vez de um arquivo do repositório.

Testes (servidor dedicado, com comandos que simulam cada caso):
- pausa simulada (thread parada 15 s, depois 1 s de trabalho, entre ticks): a 0.1.2 registrou
  "travado há 15604 ms / Tick lento: 16000 ms"; a 0.1.3 não registrou nada;
- travamento de 3 s nos primeiros 30 s depois de abrir: não registrado;
- 3 s de trabalho entre ticks: registrado ("ocupado por pelo menos 2758 ms entre ticks"), causa `hlbench 100%`;
- 3 s dentro do tick: registrado, causa `hlbench 100% (TesteLag.travar)`;
- `save(File)` lento durante `save-all`: registrado como "durante um salvamento do mundo", causa `hlbench 80%`; a classe
  foi avisada uma vez como síncrona; a classe da lista `serializar_classes` foi gravada em outra thread sem chamar o
  `save(File)` dela;
- resumo ao fechar e `/hlotimizacao status` com os contadores.
- Não testado aqui: a pausa real do singleplayer num cliente (testada pela simulação acima, que reproduz o mesmo
  comportamento da thread) e o Canary (não está no ambiente de teste).
- Não feito: mixins para o Avaritia (`salvamento_assincrono_extra`), ver abaixo.

Sobre os travamentos do Avaritia e FTB Essentials ao salvar: `FileOutputStream.open0` demorando de 2 a 13 s só para
abrir um arquivo costuma ser o antivírus examinando cada arquivo do save. Colocar a pasta da instância nas exclusões
do Windows Defender deve reduzir bastante esses travamentos.

### 0.1.2
- Novo módulo `tooltip_seguro` e novo módulo `diagnostico_lag` (acima).
- `relatorio_conflitos`: verificação de versões no nome dos arquivos, dependências opcionais fora da versão,
  End Enchanting Table + Apotheosis/Tool Leveling+, Rechiseled: Create sem Rechiseled.
- `salvamento_assincrono`: gravações pendentes também são esperadas (até 10 s) se o processo fechar sem
  `ServerStoppedEvent` (crash, `System.exit`), com aviso de quantas ficaram. Um "finalizar processo" forçado pelo
  sistema não executa esse código. Fechar a janela do jogo normalmente já passava pelo `ServerStoppedEvent`.
  A correção do Fast Async World Save ganhou a mesma proteção.
- Log de debug quando a verificação de `save(File)` próprio falha.
- `vazamento_gpu`: não vincula mais framebuffer/textura 0 ao liberar; o estado vinculado fica intacto.

Testes (servidor dedicado e cliente em tela virtual, no ambiente de teste):
- tooltip: sem o módulo, os 3 casos (item próprio, ItemTooltipEvent, GatherComponents) reproduzem o crash do
  pack (`"p_14312_" is null`); com o módulo, 9 de 9 chamadas sem erro, um aviso por item com a origem certa;
- lag: travamento controlado de 3 s e geração síncrona de 169 chunks novos (13 s) registrados com a pilha
  (`ServerChunkCache.getChunk`) e o relatório ("169 chunks carregados, dos quais 169 gerados agora"); o limite de 1
  registro a cada 30 s foi respeitado;
- nomes: 26 nomes reais do pack, incluindo `1.20.[0-4]`, `1.18-1.20.4` e `1.18x-1.21x`, reconhecidos como compatíveis;
- desligamento: 5 gravações pendentes concluídas em 0,4 s; com 37 gravações grandes, esperou 10 s e avisou que 9
  não terminaram.
- Não testado aqui: um crash real do cliente e o caso exato do Windows.

### 0.1.1
Correção no módulo `salvamento_assincrono` (relatado no Windows com o Goblin Traders):
- SavedData com nome que o `java.nio` não aceita (ex.: `goblintraders:goblin_trader_spawner`, com `:`, no
  Windows) gerava `InvalidPathException` na thread de gravação a cada save. Agora o caminho é validado
  ainda na thread do servidor e esses dados são salvos do jeito vanilla (síncrono), como o vanilla e o
  Fast Async World Save faziam.
- Toda a gravação assíncrona fica dentro do `try`; qualquer erro é registrado com o nome do dado.
- Antes, um dado cuja gravação falhava ficava marcado como "limpo" e não era regravado (perda silenciosa).
  Agora, se a gravação falhar, o dado volta a ser marcado como sujo na thread do servidor e é regravado no
  próximo save.
- O arquivo temporário recebe `fsync` antes de substituir o arquivo antigo.

Testado num servidor dedicado (Linux, usando um nome com caractere NUL, que o `java.nio` rejeita no Linux
como rejeita `:` no Windows): nenhuma exceção não tratada; o dado com falha de gravação foi remarcado e
gravado no save seguinte; dados normais continuam assíncronos (mediana de ~2 ms na thread principal para
4 MB). O caso exato do Windows com `:` não pôde ser executado aqui (o ambiente de teste é Linux).

### 0.1.0
Primeira versão.

## Compilar

```
cd mod
./gradlew build
```
O jar sai em `mod/build/libs/`. Requer acesso à internet na primeira vez (baixa Minecraft e Forge).
