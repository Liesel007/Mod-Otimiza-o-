# HL Otimização (mod para Forge 1.20.1)

Mod de otimização feito a partir do estudo do código dos mods do modpack HL
(ver [`docs/aprendizados.md`](../docs/aprendizados.md)). Ele reúne técnicas desses mods com
**código próprio** (sem copiar código dos outros mods, que têm licenças diferentes) e **corrige erros**
encontrados neles.

**Jar pronto:** [`releases/hlotimizacao-0.1.0.jar`](../releases/hlotimizacao-0.1.0.jar). Basta colocá-lo
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
| `relatorio_conflitos` | (diagnóstico) | — | servidor |

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

O `relatorio_conflitos` também avisa sobre Starlight, Ksyxis e Connectivity (ver `docs/aprendizados.md`).

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

## Compilar

```
cd mod
./gradlew build
```
O jar sai em `mod/build/libs/`. Requer acesso à internet na primeira vez (baixa Minecraft e Forge).
