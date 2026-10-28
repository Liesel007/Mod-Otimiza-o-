# More Culling (`moreculling`, FX - PR0CESS / fxmorin)

Fonte estudada: `FxMorin/MoreCulling`, branch `1.20.1` (código em mappings Yarn, de quando o mod era
só Fabric; o jar instalado `moreculling-0.24.6-all.jar` é a versão multiloader, mas a lógica é a
mesma). 44 mixins.

## Problema
O Minecraft decide se desenha **cada face de bloco** comparando com o vizinho
(`Block.shouldDrawSide`). As regras vanilla são conservadoras: qualquer bloco que não seja cubo
opaco perfeito faz os vizinhos desenharem faces que nunca aparecem (folhas, blocos com textura
recortada, molduras, escadas de mão, portas...). Isso gera mais geometria no mesh dos chunks e mais
trabalho para a GPU.

## Como resolve
- **Analisa as texturas na carga** (`Sprite_opacityMixin`, `BakedQuad_cacheMixin`): lê os pixels da
  imagem não mipmapeada de cada sprite e descobre se ela tem **transparência** (alfa 0) ou
  **translucidez** (alfa parcial), inclusive só na área usada por cada quad (`QuadBounds`). Resultado
  em cache por quad.
- **Cache por BlockState** (`BasicBakedModel_cacheMixin` e similares para `Multipart`, `Weighted`,
  `Builtin`): para cada estado, guarda em bits **quais lados têm quads** e se a textura tem
  translucidez. Também um "cull shape" (`cullshape/*`) que pode ser declarado no JSON do modelo.
- **`shouldDrawSide` próprio** (`Block_drawSideMixin`, prioridade 2500): se o estado pode ser culled,
  usa `CullingUtils.shouldDrawSideCulling`, que permite esconder faces encostadas em blocos que,
  pela textura, cobrem a face (mesmo não sendo cubo opaco). Tag `DONT_CULL` para exceções.
- **Folhas** (`LeavesBlock_typesMixin`): modos de culling configuráveis (FAST, VERTICAL, STATE, CHECK,
  GAP, DEPTH, RANDOM): por exemplo, esconder as faces internas entre folhas mesmo no gráfico "Fancy".
- **Molduras e mapas** (`ItemFrameEntityRenderer_cullMixin`): renderer próprio que não desenha a face
  de trás nem o fundo atrás de um mapa opaco, e só desenha o mapa se a câmera está do lado da frente.
- Block entities: não desenhar texto de placas longe ou de costas, raio do beacon/end gateway com
  frustum culling, shulker/end gateway.
- **Compatibilidade com Sodium/Embeddium** (`compat/BlockOcclusionCache_sodiumMixin`): aplica a mesma
  lógica dentro do cache de oclusão do Sodium, que substitui o do vanilla.
- Itens: `ItemRenderer_faceCullingMixin` não desenha faces de itens em GUI que não aparecem.

## O que aprender
- **Pré-processar dados estáticos na carga** (pixels de texturas) para tomar decisões baratas em tempo
  de jogo.
- Guardar informações por BlockState em bits (`DirectionBits`).
- Quando outro mod substitui o sistema (Sodium), é preciso um mixin de compatibilidade no sistema
  dele também.
