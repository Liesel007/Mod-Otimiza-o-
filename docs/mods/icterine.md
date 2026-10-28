# Icterine (`icterine`, Mephodio)

Fonte estudada: `Mephodio/Icterine`, branch `1.20.1` (multiloader). 5 mixins.

## Problema
A conquista "inventário mudou" (`InventoryChangeTrigger`) roda **a cada mudança de slot** do
jogador. Para cada conquista ainda não obtida com esse gatilho, o vanilla testa os predicados de
item contra o inventário inteiro. Com centenas de conquistas de mods, coletar itens, usar hoppers
ou fazer crafting em massa vira um custo alto no servidor.

## Como resolve (cada item é uma opção da config)
- `ItemStackMixin`: guarda no `ItemStack` o tamanho anterior da pilha (`previousStackSize`),
  preenchido por `AbstractContainerMenuMixin.triggerSlotListeners` quando o item do slot é o mesmo.
- `InventoryChangeTriggerMixin` (cancela o gatilho cedo):
  - pilha esvaziada → ignora (perder item não dá conquista);
  - pilha diminuiu → ignora;
  - pilha aumentou mas **não cruzou nenhum limiar** de quantidade usado por alguma conquista → ignora.
    Os limiares (`StackSizeThresholdManager`, um `TreeSet`) são extraídos dos JSONs de conquistas
    num reload listener de datapack (`count.min > 1`).
- `InventoryChangeTriggerInstanceMixin`: se a conquista tem vários predicados e **nenhum** casa com o
  item que mudou, retorna `false` sem varrer o resto do inventário.
- `ItemPredicateMixin.fasterMatches`: checa a contagem (inteiro, barato) **antes** do `matches`
  completo (que compara item, tags, NBT, encantamentos).
- `AbstractContainerMenuMixin.addSlot`: inicializa `lastSlots` com o item real em vez de vazio, para não
  disparar gatilhos falsos ao abrir um menu.
- `MixinPlugin`: aplica cada mixin só se a opção correspondente estiver ligada.

## O que aprender
- **Curto-circuito**: descartar cedo os casos que nunca podem mudar o resultado.
- **Pré-computar a partir dos dados** (limiares lidos dos datapacks) para tornar o teste em tempo
  de jogo trivial.
- Ordenar condições da mais barata para a mais cara.
