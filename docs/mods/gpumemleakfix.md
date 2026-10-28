# GPU Memory Leak Fix (`gpumemleakfix`, someaddon)

Fonte estudada: `someaddons/gpumemleakfix`, branch `1.20.1`. 1 mixin.

## Problema
`RenderTarget` (framebuffer do Blaze3D) guarda três IDs de objetos OpenGL: `frameBufferId`,
`colorTextureId` e `depthBufferId`. Se um mod cria um `RenderTarget` e esquece de chamar
`destroyBuffers()`, o objeto Java vai para o GC, mas a textura/framebuffer continuam vivos na VRAM.

## Como resolve
- `RenderTargetMixin` adiciona um `finalize()` em `RenderTarget`. Quando o GC coleta um
  RenderTarget que ainda tem IDs válidos (`> -1`), os IDs são colocados numa
  `ConcurrentLinkedQueue<Vec3i>` (x=depth, y=color, z=framebuffer).
- `finalize()` roda na thread do finalizer, onde **não** há contexto OpenGL. Por isso só enfileira.
- `ClientEventHandler.onClientTick` (thread de render, com contexto GL) drena até 20 itens por tick:
  desfaz o bind (`_bindTexture(0)`, `_glBindFramebuffer(GL_FRAMEBUFFER, 0)`), libera as texturas
  com `TextureUtil.releaseTextureId` e o FBO com `_glDeleteFramebuffers`.

## O que aprender
- Padrão **"finalizer só enfileira, thread dona do recurso libera"**: recursos nativos/GL nunca
  podem ser liberados fora da thread que tem o contexto.
- Limitar o trabalho por tick (20) para não gerar pico de frame.
- `finalize()` está depreciado no Java moderno; a alternativa equivalente é `java.lang.ref.Cleaner`
  com a mesma fila.
