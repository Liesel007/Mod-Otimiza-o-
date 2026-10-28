# Ksyxis (`ksyxis`, VidTu)

Fonte estudada: `VidTu/Ksyxis`, branch `main` (um único jar para Forge, NeoForge, Fabric, Quilt e
versões de 1.12 a 1.21). 7 mixins.

## Problema
Ao abrir ou criar um mundo, o vanilla carrega a área de **spawn chunks** (quadrado de 21×21 = 441
chunks na 1.20.1) com um ticket de nível alto, e **espera todos ficarem prontos** antes de deixar o
jogador entrar (`MinecraftServer.prepareLevels`, a tela de "Preparando área de spawn"). Em modpacks
com worldgen pesado isso leva muito tempo.

## Como resolve
- `MinecraftServerMixin`:
  - `@ModifyConstant` no nível do ticket de spawn (`11` → `0`): o ticket ainda é adicionado, mas
    praticamente não carrega nada em volta.
  - `@ModifyConstant` na quantidade de chunks esperada (`441` → `0`): o laço de espera termina na
    hora. Se o ModernFix já remove os spawn chunks (opção `perf.remove_spawn_chunks`), informa `441`
    para não conflitar (`CompatHacks`).
  - Versões antigas: altera as constantes de raio do laço (`-192/192`) para que ele não execute.
- `ServerLevelMixin` / `LevelMixin`: o raio de spawn chunks passa a ser 0 e `isSpawnChunk` sempre `false`.
- `MinecraftMixin` (cliente): remove a espera fixa do cliente na tela de carregamento.
- Os mixins usam `@Pseudo`, `remap = false` e listas de nomes de método em **todos os mappings**
  (Mojang, SRG, MCP, Yarn, Intermediary...), com `require = 0, expect = 0`. Assim um único jar funciona
  em qualquer versão/loader.

## Efeito colateral importante
Os spawn chunks deixam de ficar sempre carregados. **Fazendas e máquinas construídas no spawn
param** quando não há jogador por perto.

## O que aprender
- Às vezes o maior ganho é **não fazer** um trabalho que o jogador nem precisa (pré-carregar 441
  chunks antes de entrar).
- Técnica de compatibilidade multi-versão: mixin por nome alvo em vários mappings + `@Pseudo`.
