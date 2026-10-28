# Embeddium (`embeddium`, embeddedt) — Sodium para Forge

Fonte estudada: `embeddedt/embeddium`, branch `20.1/forge`. ~500 arquivos Java, ~100 mixins. É o fork
para Forge da última versão de código livre do **Sodium** (0.5.8), com correções de compatibilidade
com mods. Os pacotes `me.jellysquid.mods.sodium.*` são o motor; `org.embeddedt.embeddium.*` são as
adições.

## Problema
O renderizador de terreno vanilla (e o immediate mode) foi feito para ser simples: muitos draw
calls, formato de vértice grande, pouca rejeição do que não aparece, e muito trabalho na CPU por
frame. Em modpacks, com muitos blocos de mods e alta distância de renderização, a CPU vira o gargalo
do FPS.

## Como resolve (núcleo)
### 1. Renderizador de terreno reescrito (`render/chunk/*`)
- **Regiões**: seções de chunk (16³) agrupadas em regiões de 8×4×8 = 256 seções
  (`RenderRegion`). Cada região tem um **único buffer de GPU** gerenciado por um alocador próprio
  (`gl/arena/GlBufferArena`, com staging buffers), em vez de um VBO por seção.
- **Multi-draw** (`DefaultChunkRenderer`): todas as seções visíveis de uma região são desenhadas com
  **um** `glMultiDrawElementsBaseVertex` (`MultiDrawBatch`), em vez de um draw call por seção.
- **Faces separadas por direção** (`ModelQuadFacing`): a malha de cada seção é dividida em 7 grupos
  (±X, ±Y, ±Z, sem direção). `getVisibleFaces` só emite os grupos que podem estar de frente para a
  câmera (ex.: não desenha as faces "de cima" de um chunk que está acima de você). Backface culling na
  CPU por grupo, praticamente grátis.
- **Formato de vértice compacto** (`CompactChunkVertex`, 20 bytes): posição em 3 `short`
  (quantizada relativa à seção), UV em 2 `short`, cor e luz empacotadas. Menos memória de GPU e banda.
  Escrita com `MemoryUtil.memPutShort` direto em memória nativa.
- **Occlusion culling por grafo** (`OcclusionCuller`): BFS a partir da seção da câmera. Cada seção
  guarda, ao ser compilada, por quais pares de faces "dá para atravessar" (`VisibilityEncoding`). A
  busca só entra num vizinho se existe um caminho de visibilidade desde a direção de onde veio, só
  anda **para fora** da câmera, e testa distância + frustum. Usa uma fila dupla (`DoubleBufferedQueue`)
  e máscaras de bits de direção, sem alocação.
- **Compilação de malhas em threads** (`compile/executor`, `compile/tasks`): rebuilds de seções
  rodam em workers com prioridade (o que está perto/visível primeiro); o upload para a GPU é feito
  em lote na thread de render.
- Ordenação de translúcidos opcional (`render/chunk/sorting`).

### 2. Immediate mode e entidades (`mixin/features/render/*`)
- `entity/fast_render`: `ModelPart`/`Cuboid` escrevem os vértices das caixas de modelos de entidade
  com matemática pré-calculada e escrita direta, em vez do caminho genérico por vértice.
- `remove_streams`: troca `Stream`s por laços em código chamado por frame.
- `immediate/buffer_builder/intrinsics`: `BufferBuilder` com escrita "intrínseca" (vértice inteiro de
  uma vez em memória nativa) para formatos conhecidos.
- `entity/cull`: culling de entidade por caixa contra o frustum mais barato.
- Partículas billboard, fontes/glifos, céu, nuvens e neblina com caminhos mais rápidos.

### 3. Texturas (`mixin/features/textures/*`)
- **Animações só quando visíveis** (`animations/tracking`): marca quais sprites animados foram
  realmente usados no frame (blocos desenhados, partículas, GUI) e só atualiza/envia para a GPU esses.
  Em modpacks com muitas texturas animadas (máquinas, fluidos), economiza muito.
- Mipmaps calculados de forma mais rápida e correta.

### 4. Opções e compatibilidade
- Tela de vídeo própria (base para o **Sodium Options API** e o **Sodium Extras**), toggles de
  render layers (folhas), clima, overlays.
- `modcompat/*` e `workarounds/*`: Fabric Rendering API integrada (Indigo), BrandonsCore (Draconic
  Evolution), criação de contexto GL, event loop.
- `MixinConfig` + `SodiumMixinPlugin`: cada grupo de mixins pode ser desligado em
  `config/embeddium-mixins.properties`, e outros mods podem desligar mixins conflitantes.

## O que aprender
- **Menos draw calls** (multi-draw por região) e **menos bytes por vértice** (quantização) são as
  duas grandes alavancas de renderização na CPU e na GPU.
- **Rejeitar cedo** em várias camadas: grafo de visibilidade → frustum → faces por direção.
- Pré-computar dados de visibilidade **na compilação** da malha (custo único) para usar todo frame.
- Escrever em memória nativa com layout fixo em vez de objetos Java.
