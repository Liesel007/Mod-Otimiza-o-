# Sodium Options API (`sodiumoptionsapi`, txnimc / Toni) — biblioteca

Fonte estudada: `txnimc/SodiumOptionsAPI`, branch `main`. 7 mixins. **Não otimiza nada sozinho.**

## Para que serve
A tela de vídeo do Sodium/Embeddium não foi feita para receber opções de outros mods. Esta API:
- adiciona **abas com rolagem** (`SodiumOptionsTabFrame`, `ScrollableFrame`, `TabHeaderWidget`) na tela
  de opções do Embeddium (`SodiumOptionsGuiMixin`) e compat com o layout do Reese's Sodium Options;
- dá **IDs estáveis** a páginas, grupos e opções (`OptionIdentifier`, `OptionIdGenerator`) para que um mod
  possa inserir uma opção **antes/depois** de outra existente (`OptionGroupBuilderMixin`,
  `OptionPageMixin`);
- expõe eventos de construção (`OptionGUIConstruction`, `OptionPageConstruction`,
  `OptionGroupConstruction`) que mods como o Sodium Extras usam para registrar suas páginas.

## O que aprender
- Quando vários mods precisam estender a mesma interface de outro mod, uma **API pequena com IDs e
  eventos** evita que cada um escreva mixins conflitantes na mesma tela.
