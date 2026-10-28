# FastSuite (`fastsuite`, Shadows_of_Fire)

Fonte estudada: `Shadows-of-Fire/FastSuite`, branch `1.20`. 4 mixins (só 3 classes de lógica).

## Problema
`RecipeManager.getRecipeFor(type, inv, level)` percorre **todas** as receitas do tipo, uma por uma,
chamando `recipe.matches(inv, level)`. Num modpack com dezenas de milhares de receitas de crafting,
cada clique numa mesa de trabalho, fornalha ou máquina que procura receita custa caro.

## Como resolve
- **Troca o `RecipeManager` inteiro** por uma subclasse (`AuxRecipeManager`), com um truque de
  mixin: `@Shadow private final RecipeManager recipes = new AuxRecipeManager(...)` em
  `ReloadableServerResources` (servidor) e em `ClientPacketListener` (cliente). Declarar o
  inicializador do campo no mixin **substitui o inicializador original**.
- `AuxRecipeManager.getRecipeFor/getRecipesFor`:
  - Tipos com menos de 100 receitas (ou na lista negra da config) seguem o caminho vanilla.
  - Para os outros, usa uma `CachedRecipeList` por tipo que separa as receitas em **paralelas**
    (classe de receita do pacote `net.minecraft.world.item.crafting.` e ingredientes vanilla/Forge,
    ou seja, código conhecido como thread-safe) e **seriais** (receitas de mods, que podem ter
    estado).
  - As paralelas são testadas com `parallelStream().filter(matches).findFirst()` num `ForkJoinPool`
    próprio (`max(4, núcleos-4)` threads), com **timeout** (25 s) e fallback vazio. Se nada casar,
    testa as seriais em sequência.
  - Modo de depuração: "trava" os `ItemStack` de entrada (`ItemStackMixin` lança exceção em
    `setCount`) para pegar mods que modificam a entrada durante o `matches`.
- `IngredientMixin`: `@Overwrite` em `getStackingIds` com `synchronized`, porque agora várias threads
  podem calcular o cache preguiçoso ao mesmo tempo.

## O que aprender
- **Paralelismo só onde é seguro**: classificar o que é thread-safe por pacote/classe e manter o
  resto serial. Sempre com timeout e fallback.
- Pool próprio (não usar o `commonPool` do Java, que outros mods também usam), com
  `ContextClassLoader` correto para o modlauncher.
- Caches preguiçosos lidos por várias threads precisam de sincronização.
- Ferramenta de benchmark embutida (`test`, 10.000 buscas, média em ns) para provar o ganho.
