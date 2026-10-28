# Particle Core (`particle_core`, fzzyhmstrs)

Fonte estudada: `fzzyhmstrs/pc`, branch `forge/1.20.1` (código em Kotlin + Java, mappings Yarn).
18 mixins, quase todos com `@Restriction` (Conditional Mixin) para aplicar só quando a config/os
mods presentes permitem.

## Problema
Partículas (fumaça, poções, explosões, magias de mods) são milhares de objetos pequenos que, a
cada tick e frame: calculam colisão com blocos, buscam a luz no mundo, rotacionam 4 vetores para
ficar de frente para a câmera e escrevem vértices um campo de cada vez.

## Como resolve
- **Limite de partículas**: a fila de cada "sheet" de textura vira um `EvictingQueue` com tamanho
  máximo configurável (as mais antigas são descartadas).
- **Distância de renderização de partículas** (`ParticleManagerRenderDistanceMixin`): não gera a
  geometria de partículas além da distância configurada.
- **Cache de posição e bloco** (`ParticleCachePosMixin`): no início do tick, guarda o `BlockPos`, o
  `BlockState` (lido sob demanda) e se o bloco tem colisão vazia.
- **Colisão mais barata** (`ParticleMoveAdjustMixin`): se a partícula está num bloco sem colisão (ar) e
  o movimento não sai do bloco em nenhum eixo, retorna o movimento sem calcular colisões. Se sai só em
  alguns eixos, calcula só esses (`checkX/Y/Z`).
- **Cache de luz** (`ParticleBrightnessCacheMixin` + `ParticleManagerCachedLightMixin`): um
  `ConcurrentHashMap<BlockPos, Integer>` recriado a cada tick: partículas no mesmo bloco reaproveitam a
  mesma leitura de lightmap.
- **Rotação de billboard uma vez por frame** (`ParticleManagerRotationMixin`, `BillboardParticleMixin`):
  os 4 vértices do quad virado para a câmera são rotacionados **uma vez por frame** e reaproveitados
  por toda partícula sem ângulo próprio. Desligado com Sodium/Embeddium (que já fazem algo equivalente).
- **Escrita de vértice em lote** (`BillboardParticleVertexMixin` + `BufferBuilderVertexMixin`): junta
  posição, UV, cor e luz numa estrutura e escreve os 28 bytes do vértice direto no buffer (`putFloat`,
  `putByte`, `putShort`) numa chamada só, em vez de 5 chamadas encadeadas.
- **Tick assíncrono de partículas** (`ParticleManagerAsyncMixin`, opcional): sheets com muitas partículas
  são tickadas em `parallelStream`. Se uma classe de partícula dá erro de acesso multi-thread (ex.:
  `LegacyRandomSource`), ela entra numa lista de "inseguras" e volta a ser tickada na thread principal;
  se mais de 2/3 falham, o recurso se **desliga sozinho**.
- Opção de esconder partículas de poção (próprias, de outros jogadores, de mobs).

## O que aprender
- **Fast path para o caso comum** (partícula no ar) com fallback para o caminho completo.
- **Cache por tick** compartilhado entre objetos que fazem a mesma consulta.
- Trabalho por frame em vez de por objeto (rotação da câmera).
- **Paralelismo com detecção de falha e autodesligamento** é uma forma prudente de experimentar
  multithreading em código que não foi feito para isso.
