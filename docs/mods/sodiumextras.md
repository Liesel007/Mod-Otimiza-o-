# Sodium Extras (`sodiumextras`, txnimc / Toni) — antigo "Embeddium Extras"

Fonte estudada: `txnimc/SodiumExtras`, branch `main` (um código-fonte para várias versões e
loaders, com **pré-processador** `#if mc>=...`). ~20 mixins. Addon do Embeddium.

## O que faz (desempenho)
- **Culling por distância de entidades e block entities** (`entitydistance/*`): em
  `EntityRenderDispatcher.shouldRender` e `BlockEntityRenderDispatcher.render`, não desenha o que
  estiver além de uma distância **horizontal e vertical** configuráveis (separadas). Uma whitelist
  por tipo (calculada uma vez e guardada num campo injetado no `EntityType`/`BlockEntityType`) exclui
  coisas que precisam aparecer de longe.
- **Neblina desligável** (`togglefog/FogMixin`): empurra o fim da neblina para muito longe.
- **Contador de FPS/GPU** na tela (`fps/*`), com histórico de mínimo/média.
- Altura das nuvens, sombra de fonte, tela cheia sem bordas (`borderless`), velocidade do fade-in de
  chunks.
- **"True Darkness"** (`darkness/*`): escurece a noite e cavernas modificando o lightmap (é visual,
  não desempenho).
- Páginas de opções dentro da tela de vídeo do Embeddium (via Sodium Options API).

## Detalhe de implementação que vale aprender
Toda opção de `ForgeConfigSpec` tem uma cópia em campo `static volatile` (`fogCache`,
`entityDistanceCullingCache`...) atualizada quando a config muda. Ler `ConfigValue.get()` dentro de
mixins chamados **por entidade, por frame** é caro; ler um campo estático é quase grátis.

## O que aprender
- **Culling por distância** é o mais simples e eficaz para mods com muitas block entities
  animadas.
- **Copiar valores de config para campos estáticos** em caminhos quentes.
- Cachear o resultado de checagens de whitelist (string/regex) no próprio objeto de tipo.
