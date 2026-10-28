# Clumps (`clumps`, Jaredlll08 / BlameJared)

Fonte estudada: `jaredlll08/Clumps`, branch `1.20.1` (multiloader: `common`, `forge`, `fabric`). 2 mixins.

## Problema
Fazendas de XP geram centenas de `ExperienceOrb`. Cada orbe é uma entidade com tick, física e
colisão. O vanilla só junta orbes de **mesmo valor** e com uma regra aleatória (`id % 40`).

## Como resolve
- `MixinExperienceOrb` guarda um `Map<Integer,Integer> clumpedMap` (valor → quantidade) em cada orbe.
  Assim um único orbe representa orbes de valores diferentes.
- `canMerge` (as duas sobrecargas) passa a aceitar qualquer orbe vivo, sem olhar o valor.
- `tryMergeToExisting` (ao spawnar XP) procura um orbe numa caixa de 1×1×1 e soma o novo valor no mapa
  dele, em vez de criar uma entidade nova.
- `merge` soma os mapas dos dois orbes e descarta o segundo.
- `playerTouch`: remove o `takeXpDelay` (o jogador absorve tudo no mesmo tick) e, para cada
  (valor, quantidade), dispara eventos de valor/reparo (para mods como Mending) e chama
  `repairPlayerItems`. O reparo foi reescrito para ser **recursivo**: o XP que sobra de um item
  reparado vai para o próximo, e o resto vira XP do jogador.
- Persistência: `addAdditionalSaveData`/`readAdditionalSaveData` gravam o mapa no NBT.
- `ExperienceOrbAccess`: interface `@Accessor` para ler/escrever campos privados (`age`, `count`).

## O que aprender
- **Reduzir o número de entidades** agregando o estado em uma só (o mesmo princípio de juntar
  stacks de itens no chão).
- `@Accessor` para expor campos privados sem reflection.
- Estrutura **multiloader**: lógica e mixins em `common`, só eventos/plataforma em `forge`/`fabric`,
  ligados por `ServiceLoader` (`Services.load`).
