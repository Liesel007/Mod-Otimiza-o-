# BadOptimizations (`badoptimizations`, thosea)

Fonte estudada: `ItsThosea/BadOpitmizations`, branch `1.20.1` (multiloader `common`/`forge`/`fabric`).
24 mixins. O nome é brincadeira: são **micro-otimizações** no cliente que somam.

## Otimizações
- **Lightmap só quando muda** (`tick/MixinLightTexture`): o vanilla recalcula a textura de luz
  (16×16 combinações de luz de bloco/céu, com matemática de vetores) e envia para a GPU **todo tick**.
  O mixin cancela o `tick()` a menos que algo relevante tenha mudado: passou o tempo de atualização
  configurado (ciclo dia/noite), visão noturna (e o piscar no fim), escuridão do Warden, conduit,
  dimensão, escurecimento do céu (chefes), gamma, embaixo d'água, ou um mod pediu (`CacheHooks`).
- **Cor do céu em cache** (`tick/MixinClientWorld.getSkyColor`): o vanilla amostra 216 posições de
  bioma (`CubicSampler`) **todo frame**. O mixin só faz o cálculo completo se a câmera estiver entre
  biomas de cores diferentes (testa dois cantos); senão usa uma fórmula direta e guarda em cache por tick.
- **Renderer direto no tipo** (`renderer/*`): `EntityRenderDispatcher.getRenderer` e
  `BlockEntityRenderDispatcher.getRenderer` (`@Overwrite`) pegam o renderer de um campo guardado no
  `EntityType`/`BlockEntityType` (preenchido no reload) em vez de procurar num `HashMap` a cada
  entidade, a cada frame.
- **SynchedEntityData sem lock** (`entitydata/MixinSynchedData`): troca o `ReadWriteLock` por um lock
  que não faz nada (no cliente os dados só são acessados por uma thread) e avisa a entidade de qual
  dado mudou (`bo$refreshEntityData`) para ela atualizar caches.
- **Toasts**: não entra no render de toasts se não há nenhum visível nem na fila.
- **Debug renderers** (abelhas, game events, testes, aldeões): só executam se o debug está ativo e há
  dados.
- Partículas e outros pequenos ajustes (`MixinParticleManager`, `MixinWorldRenderer`,
  `MixinTutorial`).
- `BOMixinPlugin` desliga opções automaticamente quando detecta mods incompatíveis, e outros mods podem
  se declarar incompatíveis ou registrar hooks para invalidar os caches.

## O que aprender
- **"Dirty checking"**: guardar os insumos de um cálculo e só refazer quando algum muda.
- Trocar busca em mapa por **campo no objeto** em caminhos chamados milhares de vezes por frame.
- Remover sincronização que só existe para um caso que não ocorre naquele lado (cliente).
- Oferecer **hooks** para outros mods em vez de só se declarar incompatível.
