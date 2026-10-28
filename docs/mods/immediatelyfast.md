# ImmediatelyFast (`immediatelyfast`, RaphiMC / RK_01)

Fonte estudada: `RaphiMC/ImmediatelyFast`, branch `1.20` (o jar instalado
`ImmediatelyFast-Forge-1.5.5+1.20.4` cobre 1.20.1–1.20.4; código comum em `common/`, mappings Yarn).
~33 mixins agrupados por recurso (cada pasta é uma opção da config).

## Problema
Tudo que **não** é terreno é desenhado em "immediate mode": entidades, block entities, partículas,
texto, HUD, telas, mapas. O vanilla faz um **draw call** (e às vezes troca de estado/textura) a cada
pequena mudança de `RenderLayer`, e reenvia dados para a GPU com `glBufferData`, que realoca o buffer
toda vez. Com muitas entidades, isso deixa a CPU presa no driver.

## Como resolve
- **Batching** (`core/MixinVertexConsumerProvider`, `BatchableBufferSource`): troca o
  `VertexConsumerProvider.Immediate` por uma versão que **mantém um `BufferBuilder` por `RenderLayer`**
  (vindos de um pool) e acumula tudo. No `draw()`, desenha cada camada **uma vez**, ordenada por
  `getLayerOrder` para preservar a ordem visual que importa (armaduras de cavalo, camadas
  translúcidas, "renderlast"). Resultado: em vez de 1000 draw calls para 1000 vacas, poucas.
- **HUD e telas em lote** (`hud_batching/*`, `screen_batching/*`): barras de chefe, chat, lista de
  jogadores, F3, hotbar, inventários são desenhados num único batch (com compat para Highlighter,
  ArmorChroma, ItemBorders).
- **Mapas num atlas** (`map_atlas_generation/*`, `MapAtlasTexture`): em vez de uma textura 128×128 por
  mapa (cada uma um bind + draw), põe até 1024 mapas numa textura 4096×4096. A posição no atlas é
  codificada num `int` (id do atlas, x, y). Muitos mapas em molduras passam a custar poucos draws.
- **Upload de buffer mais rápido** (`fast_buffer_upload/MixinVertexBuffer`): se os dados novos cabem no
  buffer existente, usa `glBufferSubData` (só copia) em vez de `glBufferData` (realoca).
- **Texto**: busca de glifos mais rápida (`fast_text_lookup`), atlas de fonte redimensionável, e texto
  de placas pré-processado (`sign_text_buffering`).
- `disable_error_checking/MixinGL11`: remove chamadas `glGetError` desnecessárias (cada uma força
  sincronização com o driver).
- Compatibilidade explícita com Iris (shaders), que exige outro formato de vértice.

## O que aprender
- **Reduzir draw calls agrupando por estado** (material/textura/camada) é a otimização clássica de
  renderização.
- **Atlas de texturas** troca muitos binds por um.
- **Reaproveitar buffers da GPU** (`SubData`) em vez de realocar.
- Toda reordenação de desenho precisa respeitar a ordem que afeta o resultado visual (translucidez).
