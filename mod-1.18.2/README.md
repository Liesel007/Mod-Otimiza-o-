# HLO: HL Otimização — porte para Minecraft 1.18.2 / Forge 40

Mesmo mod da pasta [`mod/`](../mod/) (versão 1.20.1), portado para **Minecraft 1.18.2 / Forge 40.x** (testado com
Forge 40.3.12, o da instância HL 1.18). Mesmo modId (`hlotimizacao`), mesma config (`config/hlotimizacao.properties`),
mesmos módulos, presets, comandos (`/hl`) e tela (F8).

**Jar:** [`releases/hlotimizacao-1.18.2-0.4.0.jar`](../releases/hlotimizacao-1.18.2-0.4.0.jar)

## O que mudou em relação à 1.20.1

### Adaptações de API (o comportamento é o mesmo)
- Textos: `Component.literal/translatable/empty` não existem no 1.18; viraram `compat/C.java` (`TextComponent`,
  `TranslatableComponent`).
- Tela do F8: o `GuiGraphics` só existe a partir do 1.20; `cliente/tela/GG.java` imita os métodos usados
  (`fill`, `drawString`, `renderOutline`, `enableScissor`, `renderTooltip`) em cima do `PoseStack`.
- Eventos: `LevelEvent` → `WorldEvent`, `ChunkEvent` do pacote `event.world`, `EntityJoinLevelEvent` →
  `EntityJoinWorldEvent`, `ClientPlayerNetworkEvent.LoggingOut` → `LoggedOutEvent`. O `ServerTickEvent` do 1.18
  não traz o servidor; ele vem do `ServerLifecycleHooks`.
- Registros e capabilities: `ForgeRegistries.ENTITIES` / `BLOCK_ENTITIES`, `CapabilityItemHandler.ITEM_HANDLER_CAPABILITY`,
  `Registry.DIMENSION_REGISTRY`.
- Rede: o `SimpleChannel` do 1.18 não tem `consumerMainThread`; `RedeHL.naThread` faz o mesmo
  (`enqueueWork` + `setPacketHandled`).
- Tecla F8: sem `RegisterKeyMappingsEvent`; registrada com `ClientRegistry.registerKeyBinding` no `FMLClientSetupEvent`.
- Comandos: `sendSuccess(Component, boolean)` e `isPlayer()` trocado por `getEntity() instanceof ServerPlayer`.
- Opções do cliente (`entityDistanceScaling`, `biomeBlendRadius`) são campos, não `OptionInstance`.

### Diferenças de comportamento
- **`limite_enxame`**: o `MobSpawnEvent.FinalizeSpawn` não existe no 1.18. O limite usa o
  `LivingSpawnEvent.CheckSpawn` (spawn natural e de spawner), negando o spawn com `Result.DENY`. A rede de segurança
  pelo `EntityJoinWorldEvent` continua igual.
- **`toasts_vazios`**: no 1.18 os toasts visíveis ficam num array de uma classe privada do jogo; o mixin lê esse
  array por reflexão (uma vez) em vez de `@Shadow`.
- **`diagnostico_lag`**: o `ChunkEvent.Load` do 1.18 não tem `isNewChunk()`. Chunk novo é contado como "chunk
  com tempo de visita zero", uma aproximação (um chunk antigo nunca visitado também conta).
- **`correcao_infinity_chest`**: fica sempre como "não necessário". O Infinity Chest é do **Re-Avaritia**, que não
  existe para o 1.18.2 (o `avaritia` do 1.18.2, "Avaritia 1.1x", é outro mod, sem esse baú).
- O mod de medição (`hlbench`, pasta `src/benchmark` da 1.20.1) não foi portado.

## Como compilar

`./gradlew build` (ForgeGradle, Java 17) gera `build/libs/hlotimizacao-1.18.2-0.4.0.jar`. Esse caminho não foi
testado no porte, porque o servidor de maven do Forge estava bloqueado no ambiente onde ele foi feito.

O jar publicado foi compilado sem internet, com as bibliotecas que o CurseForge já baixa para o Forge 40.3.12
(pasta `curseforge/minecraft/Install/libraries`). Os scripts estão em [`ferramentas/compilar-offline/`](ferramentas/compilar-offline/):

1. `maps.py` junta o `mcp_config-...-mappings-merged.txt` (obf → SRG) com o `client-...-mappings.txt` da Mojang
   e gera `srg2off.json` (SRG → nomes oficiais) e `off2srg.tsrg` (nomes oficiais → SRG).
2. `Remap.java deobf` cria um jar do jogo com nomes oficiais (cliente SRG + classes do Forge) para compilar contra ele.
3. `build.sh` compila com o processador de mixins (gera o refmap e o `mixin-out.tsrg` dos `@Shadow`).
4. `pack.sh` faz o reobf (nomes oficiais → SRG, seguindo a herança) com `Remap.java reobf` e monta o jar.
5. `Check.java` confere que toda referência do jar ao jogo existe no jogo em nomes SRG.

Os caminhos dentro dos scripts são os do ambiente onde o porte foi feito; ajuste a variável `L` (pasta `libraries`).
