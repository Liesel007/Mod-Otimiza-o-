# Entity Culling (`entityculling`, tr7zw)

Fonte estudada: `tr7zw/EntityCulling`, branch `1.20` (código comum em `Shared/`, Forge em
`EntityCulling-Forge/`). 6 mixins + a biblioteca `OcclusionCulling` do mesmo autor.

## Problema
O vanilla só descarta (culling) o que está **fora do campo de visão** (frustum). Entidades e block
entities **atrás de paredes** — baús, placas, fornalhas de mods, mobs numa caverna embaixo de você —
são desenhados mesmo invisíveis. Em bases com muitas máquinas animadas, isso derruba o FPS.

## Como resolve
- **Thread separada de culling** (`CullTask`, thread "CullThread"): a cada `sleepDelay` ms, se a
  câmera mudou ou houve tick, faz **ray tracing** da posição da câmera até a caixa de cada
  entidade/block entity (até `tracingDistance`, e block entities num raio de 64 blocos / 17×17
  chunks). A biblioteca `OcclusionCullingInstance` percorre a grade de blocos (algoritmo tipo DDA)
  perguntando ao `Provider` se cada bloco é cubo opaco (`isSolidRender`). Se nenhum raio passa, o
  objeto é marcado `culled`.
- A thread **lê o mundo do cliente sem sincronização** e aceita `ConcurrentModificationException`/NPE
  como "tente de novo no próximo ciclo". É um resultado aproximado, mas a renderização nunca espera.
- `CullableMixin` adiciona a `Entity` e `BlockEntity` os campos `culled`, `outOfCamera` e um
  **timeout de 1 s**: quando algo fica visível, fica forçado visível por 1 s (evita piscar).
- **Renderização** (`WorldRendererMixin.renderEntity`, `BlockEntityRenderDispatcherMixin.render`):
  cancela o render se `culled`. Opcionalmente ainda desenha o **nametag** através da parede.
  Respeita `shouldRenderOffScreen` (beacons e afins).
- **Tick culling** (`ClientWorldMixin.tickNonPassenger`): entidades culled ou fora da câmera não
  executam o tick completo do cliente. Só um `basicTick` (posição antiga, `tickCount`, `aiStep`,
  batimento do Warden). Nunca pula o jogador, passageiros, veículos e carrinhos.
- Ignora caixas maiores que `hitboxLimit`, entidades brilhando (glowing), espectador, armor stands
  marker, e listas brancas na config. Estatísticas no F3.

## O que aprender
- **Occlusion culling na CPU** com ray tracing em grade de voxels é barato se rodar fora da thread de
  render e com resultado "eventualmente correto".
- **Histerese/timeout** para não ficar alternando visível/invisível.
- Separar "não desenhar" de "não tickar" (duas economias diferentes).
