# AllTheLeaks (`alltheleaks`, Uncandango / All The Mods)

Fonte estudada: o **jar instalado** (`alltheleaks-1.1.4+1.20.1-forge.jar`), porque o repositório não
estava acessível. Li `alltheleaks.mixins.json` (90 mixins comuns + 50 de cliente) e inspecionei as
classes com `javap`.

## Problema
**Vazamento de memória**: um objeto grande (um mundo antigo, o jogador antigo, um chunk, uma tela)
continua referenciado por um campo estático ou cache de algum mod depois que deveria ter sido
descartado. O GC não pode liberá-lo. Em modpacks, isso acontece muito ao **trocar de dimensão, morrer
e renascer (o `ServerPlayer` é recriado), sair e entrar em mundos**. A RAM vai enchendo até o jogo
travar ou dar `OutOfMemoryError`.

## Como funciona
- Cada correção é uma classe "issue" anotada com `@Issue(modId, versionRange, mixins, description)`
  em `leaks/{common,client}/mods/<mod>/` (108 no total: Forge, Minecraft, JEI, AE2, Create, Curios,
  Twilight Forest, Mowzie's Mobs, Iceberg, Xaero's Minimap, GeckoLib e outros).
- O `ATLMixinPlugin` (`IMixinConfigPlugin`) só aplica os mixins de uma issue **se o mod alvo estiver
  carregado e na faixa de versão** em que o bug existe. Quando o autor do mod corrige o vazamento numa
  versão nova, a correção do AllTheLeaks desliga sozinha. `@CompatibleHashes` permite amarrar a correção
  ao hash exato do jar do mod.
- Os consertos típicos:
  - atualizar ou anular referências ao **jogador antigo** quando o jogador é clonado
    (ex.: JEI `RecipeTransferButton`, `PlayerMixin`, `ServerPlayerMixin`);
  - limpar referências ao **`ClientLevel` antigo** ao trocar de mundo (`MinecraftMixin.updateLevels`);
  - trocar caches estáticos com chave forte por estruturas que não seguram o objeto;
  - `ClearLeakedLevelChunks`: remove block entities "tickando" de chunks já descarregados.
- Ferramentas extras: `MemoryMonitor` (estatísticas e GC de diagnóstico), relatórios (`report/`),
  comandos, e deduplicação opcional de `Ingredient`/`ResourceLocation` (`IngredientDedupe` — o autor
  avisa que é **frágil na 1.20.1 e não deve ser ligada**).

## O que aprender
- Vazamentos em Java quase sempre são **referências esquecidas** em estáticos, listeners, caches e
  lambdas que capturam `this`.
- Para encontrar: heap dump depois de trocar de dimensão algumas vezes, e procurar quantas instâncias
  de `ClientLevel`/`ServerPlayer` existem (deveria haver uma).
- Correções condicionadas à versão do mod alvo evitam aplicar patch onde o bug já foi corrigido.
