# FerriteCore (`ferritecore`, malte0811)

Fonte estudada: `malte0811/FerriteCore`, branch `1.20.0` (vale para 1.20.1; multiloader `Common` +
`NeoForge`/`Fabric`). 21 mixins. O arquivo `summary.md` do repositório explica cada otimização com a
memória economizada medida em modpacks reais.

## Problema
Com centenas de mods, existem **milhões** de `BlockState`s e modelos. Cada um carrega estruturas
genéricas (tabelas do Guava, `ImmutableMap`, lambdas, `String`s duplicadas) que somam **gigabytes** de
RAM. Mais memória ocupada = mais pausas do Garbage Collector = travadas.

## O que ele faz (pacotes de mixin) e quanto economiza (medido pelo autor)
1. **`fastmap` – vizinhos de BlockState (~600 MB)**: o vanilla guarda em **cada** estado uma
   `Table<Property, Valor, Estado>` para implementar `state.setValue(prop, v)`. O FerriteCore guarda
   **uma** `FastMap` por bloco: uma lista usada como matriz multidimensional. O estado vira um índice
   inteiro, e o vizinho é achado com aritmética (`índice` trocando o "dígito" daquela propriedade,
   via máscaras de bits na versão padrão).
2. **Propriedades do estado (~170 MB)**: o `ImmutableMap<Property, Valor>` de cada estado vira uma
   implementação própria apoiada na mesma `FastMap`.
3. **`predicates` – modelos multipart (300–400 MB)**: os predicados "mostrar esta parte se
   `north=true`" eram criados de novo para cada modelo. Agora são **cacheados e reaproveitados** pela
   chave (propriedade, valor); `And/Or` pela lista de predicados filhos. Cabos e canos com
   `north/south/...` passam a compartilhar as mesmas instâncias.
4. **`mrl` – `ModelResourceLocation` (~300 MB)**: evita converter RL→String→RL e **deduplica** a
   string `variant` (interning).
5. **`dedupmultipart` (~200 MB)**: modelos multipart com a mesma lista de partes passam a ser a mesma
   instância (de ~200 mil para ~1,5 mil).
6. **`blockstatecache` (~200 MB)**: formas de colisão/renderização iguais (`VoxelShape`) são
   deduplicadas; até os "internos" de shapes que mods guardam por conta própria são trocados pelo
   canônico.
7. **`dedupbakedquad` (~150 MB)**: quads com os mesmos `int[]` de vértices compartilham o array (só em
   `SimpleBakedModel`, por segurança).
8. **`threaddetec`** (desligado por padrão): troca o `ThreadingDetector` de cada `PalettedContainer`
   por um `byte` + `synchronized` no próprio container.
- Tudo configurável (`config`), cada grupo de mixins liga/desliga separadamente.

## O que aprender
- **Medir a memória com heap dump** e atacar as maiores classes primeiro.
- **Deduplicação/interning** de objetos imutáveis iguais (predicados, shapes, strings, arrays).
- **Trocar estruturas genéricas por representações compactas** específicas do problema (o estado como
  índice numa matriz em vez de tabela de hash).
- Só deduplicar o que é imutável na prática; declarar o risco quando não é garantido (quads).
