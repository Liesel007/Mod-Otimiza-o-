# Dynamic FPS (`dynamic_fps`, juliand665 / LostLuma)

Fonte estudada: `juliand665/Dynamic-FPS`, branch `1.20.0` (código comum em `platforms/common`,
mappings Mojang). 12 mixins comuns + específicos de plataforma.

## Problema
O jogo renderiza no máximo de FPS mesmo **minimizado, sem foco, ou com o jogador parado AFK**,
gastando CPU, GPU e bateria, e esquentando o PC, o que afeta o desempenho quando você volta.

## Como resolve
- **Máquina de estados de energia** (`PowerState`): `FOCUSED`, `HOVERED` (mouse em cima, sem foco),
  `UNFOCUSED`, `INVISIBLE` (minimizado), `UNPLUGGED` (notebook na bateria) e `ABANDONED` (inativo).
  `checkForStateChanges0` decide o estado a partir do `WindowObserver` (callbacks GLFW de foco, hover,
  iconify), do `IdleHandler` e do `BatteryTracker`.
- Cada estado tem sua config: FPS alvo, vsync, volume por categoria de som, mostrar toasts,
  rodar `System.gc()` ao entrar no estado, e um "graphics state" (reduzir ou mínimo) que copia e
  troca as opções gráficas temporariamente (`OptionHolder`).
- **Pular frames** (`GameRendererMixin`): `@ModifyExpressionValue` no campo `Minecraft.noRender`
  dentro de `GameRenderer.render`; se ainda não passou `1000/fpsAlvo` ms desde o último frame,
  finge `noRender = true` e o frame não é desenhado. Com alvo 0, não desenha nada.
- **Não desenhar o mundo atrás de telas opacas** (`renderLevel` cancelado quando um `Screen` desenha
  fundo próprio ou durante a tela de recarregar recursos).
- **Ociosidade** (`IdleHandler`): considera "atividade" o movimento do mouse (callback GLFW
  encadeado com o anterior), abrir telas, e mudar posição/olhar do jogador; depois do timeout, entra em
  `ABANDONED`.
- Volume com transição suave (`SmoothVolumeHandler`), ignorar o primeiro clique ao voltar o foco,
  e liberar o limite de FPS de menus (vanilla trava em 60) quando focado.

## O que aprender
- A forma mais barata de renderizar um frame é **não renderizar** quando ninguém está vendo.
- **Estados explícitos** com configurações por estado deixam o comportamento previsível.
- Encadear callbacks GLFW em vez de substituí-los (para não quebrar outros mods).
