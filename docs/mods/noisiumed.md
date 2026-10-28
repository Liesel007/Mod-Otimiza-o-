# Noisiumed (`noisiumed`, fork de Noisium)

Fonte estudada: `imbavirus/noisiumed`, branch `1.20-1.20.1` (Architectury, mappings Yarn:
`ChunkSection` = `LevelChunkSection`, `NoiseChunkGenerator` = `NoiseBasedChunkGenerator`).
5 mixins pequenos. O próprio README diz que gera **mundos idênticos ao vanilla**.

## Problema
Na fase `NOISE` da geração, o vanilla preenche cada bloco do terreno com
`LevelChunkSection.setBlockState(x, y, z, state, lock)`, que para **cada bloco**:
- adquire o lock do `PalettedContainer` (thread-safety);
- lê o estado antigo e atualiza contadores comparando antigo × novo;
- passa pela paleta e pelo `BitStorage` com várias chamadas indiretas.
São ~100 mil chamadas por chunk.

## Como resolve
- `NoiseChunkGeneratorMixin` (`@Redirect` no `setBlockState` dentro de `populateNoise`):
  - escreve **direto** no `BitStorage` da paleta: `palette.idFor(state)` +
    `storage.set(index(x,y,z), id)`, **sem lock e sem ler o valor antigo** (a seção é nova e só esta
    thread a acessa durante a geração);
  - atualiza os contadores (`nonEmptyBlockCount`, `nonEmptyFluidCount`,
    `randomTickableBlockCount`) incrementando, porque sabe que a seção começou vazia.
- Variante para quando Lithium/Canary/Radium estão presentes (`LithiumNoiseChunkGeneratorMixin`):
  faz a mesma escrita direta e recalcula os contadores uma vez no fim (`calculateCounts`), para não
  brigar com as mudanças do Canary nessa área. A escolha é feita no `NoisiumMixinPlugin` olhando
  quais mods estão carregados.
- `ChunkSectionMixin.populateBiomes`: preenche os biomas 4×4×4 com `swapUnsafe` (sem lock).
- `ChainedBlockSourceMixin.sample`: troca o iterador/stream por um `for` indexado (menos alocação
  num método chamado por bloco).

## O que aprender
- **Remover sincronização onde o objeto ainda é privado da thread** (objeto recém-criado,
  ainda não publicado) é uma das otimizações mais seguras e eficazes.
- Atualização incremental de contadores quando se conhece o estado inicial.
- Em código chamado milhões de vezes, até **iterador vs. for indexado** importa.
- Detectar mods concorrentes no plugin de mixin e aplicar a variante compatível.
