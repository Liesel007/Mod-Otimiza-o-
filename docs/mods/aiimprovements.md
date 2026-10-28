# AI Improvements (`aiimprovements`, BuiltBroken / QueenOfMissiles)

Fonte estudada: `BuiltBrokenModding/AI-Improvements`, branch `1.20`. **Nenhum mixin**: usa só um
evento do Forge e um access transformer.

## Problema
Cada mob roda vários `Goal`s por tick (olhar para o jogador, olhar em volta, nadar, fugir...).
O `LookControl` calcula `Math.atan2` duas vezes por tick para cada mob que está olhando para algo, o
que é caro com milhares de mobs.

## Como resolve
- **Access transformer** (`META-INF/accesstransformer.cfg`) torna públicos
  `GoalSelector.availableGoals`, `Mob.lookControl` e campos de `LookControl`. Alternativa a mixins
  quando só é preciso acesso.
- `ModifierSystem.onEntityJoinWorld` (`EntityJoinLevelEvent`): quando a entidade entra no mundo,
  passa por uma árvore de filtros (Mob → peixe, lula, vaca, galinha, porco, ovelha) que:
  - **troca o `LookControl`** por `FixedLookControl` (só se o mob usa o controle vanilla; ligado por
    padrão);
  - **remove goals** escolhidos na config (olhar para o jogador, olhar em volta, nadar, pânico,
    procriar...). Quase todos vêm **desligados** por padrão, porque mudam o comportamento.
- `FixedLookControl` usa `FastTrig.atan2`: uma **tabela pré-calculada** de 256×256 valores de `atan2`
  (8 bits por eixo). Reduz ao primeiro quadrante, normaliza pela maior componente e indexa a tabela.
  Troca precisão (~0,2°) por velocidade.
- Os filtros guardam um contador de acertos e **sobem na lista** os que mais casam ("bubbling"),
  para que os casos mais comuns sejam testados primeiro.
- Tem uma ferramenta de benchmark (`MainPerformanceTest`) comparando o vanilla com a versão nova.

## O que aprender
- **Tabelas de consulta (LUT)** para funções matemáticas caras quando precisão total não importa.
- Modificar entidades no evento de entrada no mundo, sem mixin.
- Listas de regras auto-ordenadas por frequência de uso.
