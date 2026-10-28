# Let Me Despawn (`letmedespawn`, frikinjay)

Fonte estudada: `frikinjay/let-me-despawn`, branch `forge` (a versão instalada, 1.26.10.1, é mais
nova e usa a lib Almanac; a lógica central é a mesma). 1 mixin em `Mob`.

## Problema
No vanilla, quando um mob pega um item do chão (`setItemSlotAndDropWhenKilled`), o jogo marca
`persistenceRequired = true`. O mob nunca mais desaparece. Em modpacks com muitos itens
no chão, zumbis acumulam aos milhares e derrubam o TPS.

## Como resolve
- `@Inject(TAIL)` em `setItemSlotAndDropWhenKilled`: marca um campo novo `pickedItems = true` e
  **reescreve** `persistenceRequired = hasCustomName()` (só fica persistente se tiver nome).
- `@Redirect` na chamada `discard()` dentro de `checkDespawn`: antes de remover o mob, se ele pegou
  itens, dropa todo o equipamento (exceto itens com Maldição do Desaparecimento), para o
  jogador não perder nada.

## O que aprender
- Uma otimização às vezes é **corrigir uma regra de jogo que gera acúmulo** de entidades, não
  acelerar código.
- `@Redirect` em uma chamada específica para envolver o comportamento sem copiar o método inteiro.
