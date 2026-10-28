# Recipe Essentials (`recipeessentials`, someaddon)

Fonte estudada: `someaddons/recipeessentials`, branch `1.20.1`. 19 mixins. Usa Cupboard para config.

## Problemas
1. Busca linear de receitas (`RecipeManager.getRecipeFor`) — o mesmo problema do FastSuite.
2. Comparar `ItemStack`s com NBT (`isSameItemSameTags`) compara as árvores de NBT inteiras. Isso
   acontece o tempo todo em inventários, pipes, máquinas e no `matches` de receitas.
3. Livro de receitas: em modpacks grandes, o pacote com todas as receitas desbloqueadas é enorme, e o
   cliente gasta tempo processando.

## Como resolve
### Cache de receitas por "assinatura" do inventário (`recipecache/RecipeManager`)
- Substitui o `RecipeManager` (servidor via `ReloadableServerResourcesMixin`, cliente via
  `ClientPacketListenerMixin`), atribuindo um novo objeto ao campo `@Mutable @Final` no fim do construtor.
- `calcHash(inventário, tipo)`: hash de `tipo + (posição do slot, Item)` dos slots não vazios.
  **Ignora NBT e dano de propósito**, para não encher o cache com variações.
- Para cada hash, guarda a lista de receitas que já casaram (`CachedRecipeList`), ordenada pela
  ordem original do `RecipeManager` (`recipeIndexes`) para preservar a prioridade vanilla.
- Depois que uma entrada foi usada mais de 10 vezes, testa só as receitas da lista. Para não ficar
  preso a um cache errado, **de vez em quando (probabilidade 1/(usos×30)) faz a busca completa** e
  compara com o cache; se divergir, loga e reinicia o contador (`report`).
- Cache zerado em todo reload de datapack (`apply`/`replaceRecipes`).
- Compat com Polymorph (mod que deixa o jogador escolher entre receitas conflitantes).

### Comparação de NBT mais barata (`mixin/nbt/*`)
- `CompoundTag.hashCode()` passa a ser **memoizado** (`@Overwrite`), invalidado em qualquer `put*`,
  `remove`, `merge`.
- Tags filhas (`ListTag`, `IntArrayTag`, `LongArrayTag`, `ByteArrayTag`, compounds) recebem uma
  referência ao **pai** quando são lidas com `get*`. Ao mudarem, chamam `parent.markDirty()`, que
  invalida o hash para cima na árvore.
- `equals`: se é o mesmo objeto → `true`; se os hashes diferem → `false` sem percorrer a árvore; se
  esses dois objetos já foram comparados como iguais e nada mudou desde então (`equalTo`) → `true`.
- `ItemStack.isSameItemSameTags`: mesmo atalho de identidade e de hash antes do `equals`.

### Rede e livro de receitas
- `ClientBoundRecipesMixin`: muda o formato do pacote de sincronização de receitas. Em vez de
  escrever serializer+ID como `ResourceLocation` para cada receita, junta tudo numa **única string**
  `serializer;id;serializer;id;...` e depois os corpos. Menos overhead por receita.
  (Os dois lados precisam do mod.)
- Opções: desligar o livro de receitas (não envia nem salva os dados), mostrar todas as receitas,
  ordenar pelo uso.

## O que aprender
- **Memoização com invalidação hierárquica** (filho avisa o pai) é o que torna o cache de hash
  seguro em estruturas mutáveis.
- **Hash como filtro rápido** antes de `equals` profundo.
- **Cache com verificação aleatória** para se autocorrigir sem pagar o custo sempre.
- Chave de cache "grossa" de propósito (sem NBT) para manter o cache pequeno; o `matches` real ainda
  confirma.
