# Teste de antes e depois do HL Otimização

## Como foi feito

- **Ambiente**: Minecraft 1.20.1 + Forge 47.3.0, servidor dedicado e cliente de desenvolvimento,
  numa máquina na nuvem (Intel Xeon 2,1 GHz, 4 núcleos, sem placa de vídeo: o cliente usou
  renderização por software numa tela virtual Xvfb).
- **Antes** = módulos do HL Otimização desligados (`off` na config). Com o módulo desligado, nenhum
  mixin dele é aplicado, então o jogo roda o código original do Minecraft.
- **Depois** = módulos ligados (`auto`).
- Mundo novo a cada rodada, mesma seed. As medições foram feitas por um mod de teste separado
  (`mod/src/benchmark`, comandos `/hlbench`), que **não vai no jar** do HL Otimização.
- **Não** é o modpack HL completo: o pack está no seu PC. Os números mostram o efeito de cada módulo
  isolado; no pack, os ganhos dependem de quanto cada situação acontece no seu jogo.

## Resultados

| Cenário | Antes | Depois | Diferença |
|---|---|---|---|
| Thread principal parada salvando 4 MB de dados do mundo (mediana de 30 saves) | 161 ms | 2,2 ms | **~70× menos** |
| Pior caso do mesmo save | 181 ms | 14 ms | ~13× menos |
| Gatilho de conquistas com pilha esvaziada (1 milhão de chamadas, jogador com as ~1.170 conquistas vanilla pendentes) | 18.286 ns por chamada | 5,3 ns por chamada | **~3.400× menos** |
| Zumbis que pegaram item e ficaram presos para sempre (100 criados) | 100 | 0 | 100 mobs a menos acumulando |
| CPU do jogo no menu com a janela **sem foco** (média de 60 s) | 193% | 67% | **−65%** |
| FPS com a janela sem foco | 60 | 15 | limite configurado |
| CPU com a janela **em foco** | 186% | 192% | sem diferença (o módulo só atua sem foco) |

Nas contagens de zumbis, o teste mostra `102`/`2` porque o mundo de teste tinha 2 zumbis que não
fazem parte do cenário e já eram persistentes; eles aparecem iguais nas duas versões.

## Testes de correção (o mod não pode mudar o comportamento do jogo)

| Teste | Antes | Depois |
|---|---|---|
| Conquista "pelo menos 30 slots vazios", só obtida esvaziando pilhas | obtida com 30 vazios | obtida com 30 vazios |
| Zumbi que **já era** persistente pega um item | continua persistente | continua persistente |
| Arquivo salvo de forma assíncrona tem os dados e o `DataVersion` | sim | sim |
| Erros vindos do mod nos logs | nenhum | nenhum |

## O que o teste encontrou e foi corrigido no caminho

A primeira medição do gatilho de conquistas mostrou o módulo **mais lento** que o original
(18.700 → 22.900 ns). Motivo: o Minecraft tem uma conquista de receita (a do baú) que conta slots
ocupados, e a minha checagem tratava qualquer conquista com slots como "pode mudar ao esvaziar".
Correções feitas:
1. considerar só os limites que esvaziar uma pilha pode satisfazer (ocupados/cheios com máximo, vazios
   com mínimo);
2. guardar esse resultado em cada conquista e, por jogador, em cache, invalidado quando as conquistas
   pendentes do jogador mudam.

Resultado final: 18.286 → 5,3 ns, mantendo a conquista de 30 slots vazios funcionando.

## Como interpretar

- **Salvamento**: o tempo que sai da thread principal é exatamente o "engasgo" do autosave. Num pack
  com muitos mods que guardam dados (mapas, redes de AE2, Create...), esse ganho aparece a cada
  autosave.
- **Gatilho**: o número por chamada é grande, mas só vale para pilhas que ficam vazias (usar o último
  item, mover uma pilha inteira, hoppers esvaziando inventários). Mudanças que não esvaziam pilhas não
  mudam.
- **Mobs**: é o efeito acumulado. Em fazendas de mobs e áreas com itens no chão, mobs persistentes vão se
  somando por horas.
- **CPU sem foco**: o cliente usou renderização por software, então a CPU fez também o trabalho da GPU.
  Num PC com placa de vídeo, o número absoluto é outro, mas a queda de 60 para 15 quadros vale igual e
  alivia CPU e GPU.
- **Não medidos**: o módulo de vazamento de GPU (só aparece com mods que vazam framebuffers, em sessões
  longas) e o de toasts (ganho muito pequeno para medir aqui).

## Reproduzir

```
cd mod
./gradlew runServer   # no console: forceload add 0 0 48 48, depois os comandos /hlbench
```
Comandos: `/hlbench salvar <kb> <repeticoes>`, `/hlbench gatilho <chamadas>`, `/hlbench esvaziar`,
`/hlbench mobs <quantidade>` e, depois de uns segundos, `/hlbench contar`. Os resultados aparecem no log
com o prefixo `HLBENCH`. Para o "antes", coloque os módulos como `off` em
`run/config/hlotimizacao.properties`.
