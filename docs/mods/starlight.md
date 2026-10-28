# Starlight (Forge) (`starlight`, Spottedleaf / PaperMC)

Fonte estudada: `PaperMC/Starlight`, branch `forge`. 14 mixins + ~3.500 linhas de motor próprio
(`common/light/*`). Os documentos `README.md` e `TECHNICAL_DETAILS.md` do repositório explicam o
algoritmo em detalhe.

## Problema (até 1.19)
O motor de luz vanilla recalcula a luz de cada posição **a partir dos 6 vizinhos** e guarda o estado
em `HashMap`s e `LongLinkedOpenHashSet`s. No teste do autor: para a mesma atualização, o vanilla fazia
~7× mais leituras de luz e 6–44× mais leituras de bloco.

## Como resolve
- **Reescrita completa**: os mixins em `LevelLightEngine` e `ThreadedLevelLightEngine` são quase todos
  `@Overwrite`, e delegam para `StarLightInterface` → `BlockStarLightEngine` / `SkyStarLightEngine`.
- **Algoritmo de propagação BFS "empurrando" para os vizinhos** (em vez de "puxar"): cada entrada da
  fila carrega o nível de luz; só lê o bloco vizinho se ele **poderia** receber mais luz
  (`currentLevel < lightValue - 1`). Não volta para o vizinho de onde veio.
- **Fila como `long[]` reaproveitado** (sem objetos): cada `long` empacota coordenada (28 bits), nível
  (4 bits), direções a propagar (6 bits) e flags (por ex. "este bloco tem transparência por lado,
  precisa checar a forma"). Zero alocação por atualização.
- **Cache local de seções** indexado por coordenadas relativas ao chunk central (`encodeOffset`), em vez
  de procurar a seção num mapa a cada leitura.
- **Luz do céu**: começa da seção não vazia mais alta e desce, marcando fontes diretamente; não
  propaga para chunks ainda não iluminados.
- **Dados de luz presos ao chunk** (`ChunkAccessMixin`, `ExtendedChunk`): cada chunk guarda seus
  `SWMRNibbleArray` (Single-Writer Multi-Reader): uma cópia "em atualização" e uma "visível"
  (`volatile`), trocadas ao final da atualização. Leitores de outras threads nunca veem estado parcial.
  Arrays de bytes reaproveitados por um pool `ThreadLocal`.
- `BlockStateBaseMixin`: pré-calcula por `BlockState` se é "condicionalmente opaco" e a opacidade.
- Formato próprio no save (marca a seção como "não iluminada" para o vanilla; assim, sair do mod força o
  vanilla a reiluminar e nada quebra).

## Situação na 1.20.1 (importante)
O próprio `TECHNICAL_DETAILS.md` diz que **o motor de luz vanilla da 1.20 copiou basicamente tudo do
Starlight**, e que na 1.20+ as diferenças "são pequenas o suficiente para não importar". Ainda há
ganho em atualizações de luz grandes, mas o mod troca o motor inteiro e o autor avisa que **quebra mods
que mexem direto no motor de luz**. Num modpack 1.20.1 vale testar com e sem ele.

## O que aprender
- O ganho real veio de **reduzir leituras de memória por passo** do algoritmo, medido com contadores.
- **Empacotar dados em primitivos** (`long[]`) para filas quentes elimina alocação e GC.
- **Double-buffering com `volatile`** para ler dados de outra thread sem lock.
- Benchmark reproduzível (LightBench) antes de afirmar ganho.
