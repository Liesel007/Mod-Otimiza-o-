# Structure Layout Optimizer (`structure_layout_optimizer`, TelepathicGrunt)

Fonte estudada: `TelepathicGrunt/StructureLayoutOptimizer`, branch `1.20.1` (multiloader). 4 mixins.
Usa MixinExtras (`@WrapOperation`, `@ModifyExpressionValue`, `@Local`).

## Problema
Estruturas "jigsaw" (vilas, bastiões, cidades antigas e muitas de mods como Dungeons Arise e
Towns and Towers) são montadas peça por peça em `JigsawPlacement.Placer.tryPlacingChildren`.
Para cada peça candidata, o vanilla:
1. testa colisão com as peças já colocadas usando um `VoxelShape` que vai sendo unido
   (`Shapes.join`/`joinUnoptimized`) a cada peça. Esse custo cresce com o tamanho da estrutura;
2. percorre uma lista de candidatos com **duplicatas** (o pool lista a mesma peça várias vezes por
   causa do peso);
3. ao colocar o template no mundo, itera **todos os blocos** do template, mesmo os fora do chunk
   sendo gerado.

## Como resolve
- **Octree de caixas no lugar de VoxelShape** (`BoxOctree` + `TrojanVoxelShape`):
  - O mixin cria um `TrojanVoxelShape`, uma subclasse "cavalo de Troia" de `VoxelShape` que só
    carrega um `BoxOctree`, e o coloca na variável onde o vanilla esperava o shape (assim a
    assinatura dos métodos não muda).
  - Em vez de `Shapes.join`, cada peça colocada vira um `AABB` adicionado à octree (subdivide em 8
    quando passa de 10 caixas, até profundidade 3).
  - O teste "cabe aqui?" (`Shapes.joinIsNotEmpty`) vira
    `withinBoundsButNotIntersectingChildren(aabb)`: busca só nas octantes que cruzam a caixa.
  - `joinUnoptimized` passa a devolver `Shapes.empty()` (desliga a montagem cara).
- **Pular conectores bloqueados**: se a peça é rígida e o ponto de conexão está fora do limite ou
  dentro de uma peça existente, devolve lista vazia de jigsaws antes de tentar cada um.
- **Deduplicar candidatos**: `getShuffledTemplates` → `ObjectLinkedOpenHashSet` (mantém a ordem
  aleatória, remove repetidos). Opção alternativa que preserva o consumo do RNG (para gerar o mesmo
  mundo): `TrojanArrayList` lembra o que já foi testado e só "gasta" as chamadas aleatórias.
- `canJigsawsAttach` reescrito: compara a orientação **antes** de ler o NBT, e lê `StringTag` direto
  sem criar objetos.
- **Colocar só o que está no chunk** (`StructureTemplateMixin`): filtra a lista de blocos do template
  pelo `BoundingBox` do placement. Não filtra se algum processor sobrescreve `finalizeProcessing`
  (detectado por reflection e cacheado), porque ele pode precisar da lista completa.

## O que aprender
- **Estrutura de dados certa** (octree / índice espacial) muda a complexidade do algoritmo.
- O truque da **subclasse "trojan"**: passar dados novos pelo mesmo tipo que o código original já
  usa, sem reescrever o método inteiro.
- Cuidar do **determinismo**: otimizações de worldgen não podem mudar o resultado para a mesma seed
  (por isso o modo que preserva as chamadas ao RNG).
- MixinExtras (`@WrapOperation`, `@Local`) é mais compatível com outros mods que `@Redirect`.
