# Exponential Power — porte para Minecraft 1.20.1 / Forge 47

**Mod original:** [Exponential Power](https://github.com/MoSadie/ExponentialPower) por **MoSadie**,
licenciado sob a **GNU LGPL v3** ([`LICENSE.txt`](LICENSE.txt), que também vai dentro do jar como
`LICENSE_ExponentialPower.txt`).

Este é um porte **não oficial** da versão 2.1.2 (Minecraft 1.19 / Forge 41) para 1.20.1 / Forge 47.4.
Não é afiliado ao autor original. Bugs deste porte devem ser relatados aqui, não ao MoSadie.

O código-fonte completo do porte está publicado nesta pasta, como a LGPL-3.0 exige. O commit
`Import Exponential Power 2.1.2 by MoSadie (LGPL-3.0), unmodified` traz o código original sem alterações,
então `git diff` a partir dele mostra cada mudança feita no porte.

**Jar:** [`releases/ExponentialPower-1.20.1-2.1.3.jar`](../releases/ExponentialPower-1.20.1-2.1.3.jar)

## Mudanças em relação à 2.1.2

### Adaptação à 1.20.1
- Build: ForgeGradle 6, Forge 47.4.0, mappings oficiais 1.20.1, Java 17, Gradle 8.8.
- Aba criativa registrada com `DeferredRegister<CreativeModeTab>` e preenchida pelo `BuildCreativeModeTabContentsEvent`.
- `BlockBehaviour.Properties.of()` sem `Material`: as mesmas cor de mapa, reação a pistão, som e dureza.
- Tela do gerador usando `GuiGraphics`; `player.level()`; `ForgeCapabilities.ENERGY`.
- Datagen com `PackOutput`, `BlockLootSubProvider` e `RecipeCategory`. As receitas, loot tables, modelos e
  traduções gerados são iguais aos da 2.1.2, só com os campos novos da 1.20.
- Pastas de código renomeadas para bater com os pacotes em minúsculas.

### Energia (limite de int e correções)
- Cada bloco vizinho recebe **no máximo `Integer.MAX_VALUE` FE por tick** (a API de energia do Forge usa `int`).
  O gerador avançado chega a `Double.MAX_VALUE` FE/t internamente, mas nunca oferece mais que isso por chamada.
- O valor que o outro mod diz ter aceitado é limitado a `[0, oferecido]`. Um mod com bug que devolva um número
  negativo ou maior que o oferecido não cria nem destrói energia.
- As leituras (`getEnergyStored` etc.) saturam em `Integer.MAX_VALUE` em vez de dar overflow para negativo.

### Bugs do original corrigidos
- `receiveEnergy` dos armazenamentos **ignorava `simulate`**: cabos que simulam antes de transferir (Mekanism,
  entre outros) faziam o bloco guardar energia duas vezes, duplicando energia. Agora respeita `simulate` e a capacidade.
- Os armazenamentos (Ender Storage / Advanced Ender Storage) **não tinham ticker** na 1.19, então nunca enviavam
  energia aos vizinhos. O ticker foi restaurado.
- As capabilities não eram invalidadas ao remover o bloco. Agora são.
- Pedidos negativos (`receiveEnergy(-5)`) eram processados. Agora devolvem 0.

## Testes
Feitos com o mod `eptest` (`src/eptest`, só para desenvolvimento) num servidor dedicado com Mekanism
1.20.1-10.4.16.80 e Draconic Evolution 1.20.1-3.1.2.621. Para rodar: `./gradlew runTestServer` e o comando `/eptest`.

Foram 35 cenários de 200 ticks cada, conferindo a cada tick que nenhum valor fica negativo ou NaN:
- Ender Generator e Advanced Ender Generator com 1, 2, 32 e 64 Ender Cells:
  - ligados por 3 cabos universais Ultimate do Mekanism a um Energy Pylon de um Energy Core (tier 1);
  - ligados direto no Energy Pylon.
- Os mesmos geradores ligados a uma "sonda" que registra cada valor recebido: nunca passou de `2147483647` por
  chamada, nem com o gerador avançado a 1,8e308 FE/t. Foi 1 chamada por tick por lado.
- Uma sonda que devolve valores inválidos (−7 e `Integer.MAX_VALUE`): a energia do gerador nunca ficou negativa.
- Gerador → Ender Storage / Advanced Ender Storage → sonda: o armazenamento agora envia energia, limitado a
  `Integer.MAX_VALUE` por chamada.
- `simulate`, pedidos negativos e leitura acima de `int` testados direto na capability.

Resultado: **40 de 40 verificações ok, nenhuma exceção no log.** Não foi testado com cliente gráfico nem dentro
do pack HL completo.

Observação: o armazenamento envia a cada 2 ticks para um vizinho que acabou de lhe mandar energia (lógica
`freezeExpend` do original, mantida).

## Compilar
```
cd exponentialpower
./gradlew build
```
O jar sai em `build/libs/`.
