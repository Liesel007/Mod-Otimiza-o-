# Chunky (`chunky`, pop4959)

Fonte estudada: `pop4959/Chunky`, branch `master` (código comum em `common/` + adaptadores
`forge/`, `fabric/`, `bukkit/`, `paper/`...). A versão instalada (1.3.146) é anterior, mas a lógica
central é a mesma.

## Problema
Gerar chunks novos é a operação mais cara do servidor. Quando o jogador explora, a geração acontece
**durante o jogo**, competindo com o tick, e causa lag.

## Como resolve (pré-geração)
Não acelera a geração em si: **antecipa** a geração para quando ninguém está jogando (comando
`/chunky start`), para que depois o jogo só precise **carregar** chunks já prontos do disco (bem mais
barato).

- `GenerationTask` roda numa thread própria e percorre as coordenadas com um `ChunkIterator`
  (padrões `concentric`, `spiral`, `loop`, `region`, curva de **Hilbert** (`Hilbert.java`), que mantém
  chunks vizinhos próximos no tempo e melhora o uso de cache e de arquivos de região).
- Filtra pela forma escolhida (círculo, quadrado, elipse, polígonos...) e pula chunks já gerados,
  consultando um `RegionCache` próprio e, no Forge, **lendo só o campo `Status` do NBT** do chunk em
  disco (`chunkScanner().scanChunk` com `FieldSelector`), sem carregar o chunk inteiro.
- **Controle de concorrência** com `Semaphore(MAX_WORKING_COUNT = 50)`: no máximo 50 chunks em
  geração ao mesmo tempo, para não estourar a memória nem a fila de tarefas.
- `ForgeWorld.getChunkAtAsync`: na thread do servidor, adiciona um ticket `CHUNKY` de raio 0, força
  `runDistanceManagerUpdates`, pede o future do chunk `FULL` e, quando termina, **remove o ticket**
  para o chunk poder descarregar. Contorna um vazamento do vanilla esperando a
  `pendingFullStateConfirmation` (com timeout).
- Progresso com taxa média (amostras numa janela de 30 s), tempo estimado, pausa/retomada salvando o
  estado (`TaskLoader`), integração com bordas de mundo.

## O que aprender
- **Mover trabalho caro para fora do horário de uso** (pré-computação) é uma otimização válida.
- **Limitar trabalho em andamento** (semáforo) mantém o sistema estável sob carga.
- Ler **só o campo necessário** de um arquivo grande.
- Ordem de travessia importa (localidade): curvas de preenchimento de espaço como Hilbert.
- Dica para o pack: pré-gerar um raio razoável (ex.: 3–5 mil blocos) em volta do spawn antes de jogar
  reduz muito o lag de exploração, principalmente com Dungeons Arise, Towns and Towers e outros mods de
  estrutura.
