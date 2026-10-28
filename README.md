# Mod Otimização — HL 1.20.1

Levantamento dos mods de otimização instalados no modpack **HL** para **Minecraft 1.20.1** (CurseForge). A instância fica na pasta `HL 1.18 (1)`; o nome da pasta não corresponde à versão do jogo.

## Como gerar a lista de mods

No Windows, rode o script no PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\listar-mods.ps1
```

Por padrão ele lê `%USERPROFILE%\curseforge\minecraft\Instances\HL 1.18 (1)\mods`.
Se a instância estiver em outro lugar, passe o caminho:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\listar-mods.ps1 -Instancia "D:\caminho\outra-instancia"
```

O resultado é salvo em `mods-instalados.txt`.

## Mods de otimização instalados

Mods de otimização encontrados na pasta `mods`, separados por área.

### Renderização e FPS (cliente)

| Mod | Arquivo | Função |
|---|---|---|
| Embeddium | `embeddium-0.3.31+mc1.20.1.jar` | Motor de renderização otimizado (port do Sodium para Forge) |
| Sodium Extras | `sodiumextras-forge-1.0.7-1.20.1.jar` | Opções extras de desempenho e visual para o Embeddium |
| ImmediatelyFast | `ImmediatelyFast-Forge-1.5.5+1.20.4.jar` | Acelera a renderização de HUD, texto e entidades |
| Entity Culling | `entityculling-forge-1.11.3-mc1.20.1.jar` | Não renderiza entidades e block entities fora da visão |
| More Culling | `moreculling-0.24.6-all.jar` | Culling adicional de blocos (folhas, vidro etc.) |
| BadOptimizations | `BadOptimizations-2.4.1-1.20.1.jar` | Micro-otimizações no cliente (lightmap, céu, cache) |
| Particle Core | `particle_core-0.3.3+1.20.1+forge.jar` | Otimiza a renderização de partículas |
| Dynamic FPS | `dynamic-fps-3.11.4+minecraft-1.20.0-forge.jar` | Reduz o FPS quando o jogo está minimizado ou em segundo plano |
| GPU Memory Leak Fix | `gpumemleakfix-1.20.1-1.8.jar` | Corrige vazamento de memória de vídeo |

### Memória, inicialização e uso geral

| Mod | Arquivo | Função |
|---|---|---|
| ModernFix | `modernfix-forge-5.27.91+mc1.20.1.jar` | Inicialização mais rápida e menos uso de RAM |
| FerriteCore | `ferritecore-6.0.1-forge.jar` | Reduz o uso de memória |
| AllTheLeaks | `alltheleaks-1.1.4+1.20.1-forge.jar` | Corrige vazamentos de memória de vários mods |
| Ksyxis | `Ksyxis-1.4.6.jar` | Carregamento de mundo mais rápido (não carrega os chunks de spawn) |

### Servidor, lógica do jogo e geração de mundo

| Mod | Arquivo | Função |
|---|---|---|
| Canary | `canary-mc1.20.1-0.3.3.jar` | Otimizações gerais de lógica do servidor (port do Lithium) |
| Starlight | `starlight-1.1.2+forge.1cda73c.jar` | Motor de iluminação reescrito, muito mais rápido |
| Noisium (noisiumed) | `noisiumed-forge-3.0.6+mc1.20-1.20.1.jar` | Acelera a geração de terreno |
| Structure Layout Optimizer | `structure_layout_optimizer-forge-1.0.11.jar` | Acelera a geração de estruturas (jigsaw) |
| Async Locator | `async-locator-forge-1.20-1.3.0.jar` | Busca de estruturas (mapas, `/locate`, olho do ender) sem travar o servidor |
| AI Improvements | `AI-Improvements-1.20-0.5.2.jar` | Otimiza a IA dos mobs |
| Let Me Despawn | `letmedespawn-forge-1.20.1-1.26.10.1.jar` | Permite que mobs que pegaram itens desapareçam, evitando acúmulo |
| Clumps | `Clumps-forge-1.20.1-12.0.0.4.jar` | Junta orbes de XP, reduzindo entidades |
| FastSuite | `FastSuite-1.20.1-5.1.2.jar` | Busca de receitas mais rápida |
| Recipe Essentials | `recipeessentials-1.20.1-4.7.jar` | Otimiza o processamento de receitas |
| Icterine | `Icterine-forge-1.20.0-1-1.3.0.jar` | Reduz o custo das checagens de conquistas ao mudar o inventário |
| Create: Threaded Trains | `createthreadedtrains-forge-1.20.1-1.0.0.jar` | Processa os trens do Create em uma thread separada |

### Chunks e salvamento

| Mod | Arquivo | Função |
|---|---|---|
| Better Chunk Loading | `betterchunkloading-1.20.1-5.4.jar` | Carregamento de chunks mais rápido |
| Chunk Sending | `chunksending-1.20.1-4.1.jar` | Envio de chunks ao cliente mais eficiente |
| Smooth Chunk | `smoothchunk-1.20.1-4.4.jar` | Espalha o salvamento de chunks para evitar picos de lag |
| Fast Async World Save | `fastasyncworldsave-1.20.1-2.6.jar` | Salva o mundo de forma assíncrona |
| Chunky | `Chunky-1.3.146.jar` | Pré-gera o mundo para evitar lag ao explorar |

### Rede

| Mod | Arquivo | Função |
|---|---|---|
| Connectivity | `connectivity-1.20.1-7.7.jar` | Corrige timeouts e limites de pacotes que derrubam a conexão em modpacks grandes |

### Bibliotecas de suporte (não otimizam sozinhas)

- `sodiumoptionsapi-forge-1.0.10-1.20.1.jar`: API de menus de opções usada pelos addons do Embeddium
- `cupboard-1.20.1-4.2.jar`: biblioteca usada por Better Chunk Loading, Chunk Sending, Smooth Chunk e outros mods do mesmo autor

## Estudo do código-fonte

O código de cada mod de otimização foi lido e analisado:

- [`docs/aprendizados.md`](docs/aprendizados.md): técnicas de otimização encontradas, como os mods
  alteram o Minecraft (Mixin), riscos e interações entre os mods deste pack.
- [`docs/mods/`](docs/mods/): uma anotação por mod (problema, como resolve, o que aprender).
