# Canary (`canary`, AbdElAziz) — port do Lithium para Forge

Fonte estudada: o **jar instalado** (`canary-mc1.20.1-0.3.3.jar`), porque o repositório
`AbdElAziz333/Canary` não está mais acessível. Li o `canary.mixins.json` (203 mixins) e inspecionei
classes com `javap`. Os detalhes de cada grupo seguem o desenho do **Lithium** (CaffeineMC), do qual o
Canary é fork declarado (`credits = "AbdlElAziz, Jellysquid, 2No2Name"`).

## Objetivo
Otimizar a **lógica do servidor** (tick de entidades, blocos, IA, colisões, chunks) **sem mudar o
comportamento** do jogo. Cada grupo pode ser desligado em `config/canary.properties`.

## Grupos de mixins (quantidade) e a ideia de cada um
- **`block.hopper` (25)** — o maior grupo. O funil vanilla, a cada tick, procura inventários acima e
  abaixo, e entidades com inventário, e tenta mover itens mesmo quando nada mudou. O Lithium:
  guarda referência ao inventário vizinho e **escuta mudanças** (`util.inventory_change_listening`,
  `util.inventory_comparator_tracking`), pula a tentativa quando nem a origem nem o destino mudaram,
  e tem acessores diretos para baús, barris, fornalhas, dispensers, shulkers, carrinhos
  (`InventoryAccessors$*`).
- **`world.block_entity_ticking` (13)**: menos overhead na lista de block entities que tickam
  (dormir block entities que não precisam tickar, remoção eficiente).
- **`entity.collisions` (9)**: colisão de entidades sem montar a lista completa de `VoxelShape`s;
  testa blocos e a borda do mundo de forma incremental e para cedo.
- **`ai.*` (≈30)**: `nearby_entity_tracking` (rastreia entidades próximas por seção em vez de
  buscar em caixa toda vez), `poi` (busca de pontos de interesse indexada), `pathing`, `task`,
  `sensor`, `raid`, `replace_streams` (troca `Stream` por laços).
- **`entity.inactive_navigations` (6)**: mobs que não estão navegando não recalculam caminho quando
  blocos mudam.
- **`shapes.*`**: formas de bloco pré-computadas e especializadas (cubo cheio, vazio), cache por
  BlockState, `shape_merging` mais barato.
- **`chunk.*`**: `no_locking` / `no_validation` (tira locks e checagens do `PalettedContainer` em
  caminhos seguros), `palette`, `serialization`, `entity_class_groups`.
- **`alloc.*`**: evitar alocações em código quente (`enum_values` cacheia `Enum.values()`,
  `chunk_random`, `nbt`, `composter`, `explosion_behavior`...).
- **`collections.*`**: troca coleções Java genéricas por coleções fastutil/especializadas (atributos,
  goals, entidades por tipo, gamerules).
- **`math.*`**: `sine_lut` (`CompactSineLUT`: tabela de seno compacta usando simetria, só um quarto do
  período armazenado), `fast_blockpos`, `fast_util`.
- **`world.*`**: `inline_block_access`, `inline_height`, `combined_heightmap_update`, `tick_scheduler`,
  `explosions`, `chunk_tickets`.
- **`gen.*`**: `cached_generator_settings`, `chunk_region`.

## Relação com outros mods do seu pack
- O **Noisiumed** detecta o Canary e usa uma versão compatível do mixin de geração.
- Lithium/Canary não mexem em renderização. Isso fica com Embeddium e companhia.

## O que aprender
- O padrão mais lucrativo do Lithium: **trocar "verificar todo tick" por "ser avisado quando muda"**
  (listeners de inventário, rastreamento de entidades por seção).
- Muitas otimizações pequenas e seguras somadas (alocação, streams, coleções, shapes) dão ganho
  grande, desde que **o resultado seja idêntico ao vanilla**.
- Organização em grupos com chave de config é essencial num mod com 200 mixins.
