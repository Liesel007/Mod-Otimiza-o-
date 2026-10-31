# Lag do Infinity Chest (Re-Avaritia 1.4.2)

Investigação do lag ligado ao baú infinito do **Re-Avaritia `1.20.1-1.4.2-release`** (Forge). O código lido é o
da tag `v1.4.2-release-forge` do [Nova-Committee/Re-Avaritia](https://github.com/Nova-Committee/Re-Avaritia); o
jar do Modrinth (`Re-Avaritia-forge-1.20.1-1.4.2-release.jar`, SHA-1 `d127e9ac5a00768b567aa1790710c39e16748fcd`)
tem o mesmo bytecode nos trechos citados. A correção é o módulo `correcao_infinity_chest` do HL Otimização 0.1.4.

## Causa principal: chaves que crescem a cada item com NBT

O baú guarda os itens num mapa `chave -> quantidade`. A chave é o id do item, mais `#hash` quando o item tem NBT
(`StorageUtils.getNbtItemId`). O bug está em `StorageUtils.getItem(String chave)`:

```java
Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(getBaseItemId(id)));
ID_ITEM_MAP.put(id, item);
ITEM_ID_MAP.put(item, id);   // guarda "minecraft:enchanted_book#1b2c..." como id do livro
```

`getItem` é chamado com a chave completa (com `#hash`) toda vez que a lista do baú muda (`updateItemKeys`) e ao
carregar o mundo. Daí em diante `getItemId(livro)` devolve o id **com o hash**, e o próximo livro encantado que
entra recebe a chave `id#h1#h2`, depois `id#h1#h2#h3`, e assim por diante. Consequências:

1. **Nada mais se junta.** Cada item com NBT que entra vira uma entrada nova, mesmo que seja igual a um que já
   está lá. O baú cresce sem limite.
2. **Cada entrada nova reconstrói a lista inteira** (`updateItemKeys` cria um `ItemStack` por entrada), no servidor
   e no cliente. O custo de inserir cresce com o tamanho do baú: o total é quadrático.
3. **Tudo o que percorre o baú fica mais caro**: o pacote de atualização completa (a cada 40 ticks para quem está
   com o baú aberto), `buildData` e a gravação em disco.
4. **Persiste entre sessões**: ao carregar, `updateItemKeys` chama `getItem` para todas as chaves e o cache volta
   a ficar errado.
5. Efeitos colaterais: itens sem NBT podem cair na entrada de um item com NBT (e sair com esse NBT); `takeItem`
   com a chave correta não encontra nada; o Tesseract usa o mesmo `getItemId` e também recebe chaves com hash.
6. Os caches são `HashMap` comuns e estáticos. No singleplayer, a thread do cliente e a do servidor os usam ao
   mesmo tempo.

## Causa secundária: o salvamento

`ServerChestManager.onLevelSave` responde a `LevelEvent.Save`, que o Forge dispara **uma vez por dimensão**. A
cada autosave, todos os baús são serializados, comprimidos e gravados uma vez para cada dimensão. Num pack com
dezenas de dimensões, isso multiplica o trabalho. Além disso, ele grava todos os baús mesmo sem mudança, e tudo
na thread do servidor. Com o antivírus examinando cada arquivo, é o `FileOutputStream.open0` de 2 a 13 s que
apareceu no `latest.log` do pack.

## Bugs de NBT encontrados no caminho

- `takeItem` e `saveTakeItem` apagam o NBT guardado mesmo quando só parte dos itens sai, e o que fica no baú
  perde o NBT.
- `extractItem` (usado por funis e canos) apaga o NBT antes de colocá-lo no item quando tira os últimos itens,
  e eles saem sem NBT.
- O item que sai recebe o mesmo objeto `CompoundTag` do cache. Se o jogador mexe no item (dano, nome), o NBT dos
  itens que ficaram no baú muda junto.

## Medição (servidor dedicado de teste, 3 dimensões, Re-Avaritia 1.4.2 real)

Comando `/hlbench avaritia encher 1100`: como um cano, simula e insere 1000 livros encantados (10 encantamentos
diferentes, repetidos) e 100 diamantes. O correto é o baú ficar com 11 entradas.

| | Sem o módulo | Com o módulo |
|---|---|---|
| 1ª rodada (1100 itens) | 660 ms, 1001 entradas | 57 ms, 11 entradas |
| 2ª rodada | 6 044 ms, 2001 entradas | 25 ms, 11 entradas |
| 3ª rodada | 16 390 ms, 3001 entradas, maior chave com 51 024 caracteres | 50 ms, 11 entradas |
| Autosave (`LevelEvent.Save` nas 3 dimensões) | 3 411 ms | 2,8 ms (só serializa; grava em outra thread) |
| Arquivo do baú no fim do teste | 1 085 KB | 344 bytes |

Reparo ao abrir o mundo salvo pela versão sem o módulo: `3001 entradas -> 11`, com as quantidades preservadas
(3000 livros e 300 diamantes antes e depois, conferido lendo os dois arquivos).

Teste de NBT (5 livros iguais, `takeItem` de 2, depois o jogador altera o NBT do item tirado): os 3 que ficam
mantêm o NBT e não mudam junto; o item tirado com `extractItem` sai com NBT.

## O que o módulo `correcao_infinity_chest` faz

- `StorageUtils.getItemId/getItem`: caches próprios e thread-safe; o cache item -> id só guarda o id do registro.
- Ao carregar o mundo, conserta as chaves de cada baú: chave com vários `#` e NBT guardado vira `id#hash do NBT`;
  chave com `#` sem NBT guardado vira `id`. Quantidades que caem na mesma chave são somadas, e NBT sem item é
  descartado. **Antes, copia o arquivo original para `<id>.dat.antes_hl`.**
- `takeItem/saveTakeItem` só apagam o NBT guardado quando o item sai inteiro. `extractItem` devolve o NBT
  também nos últimos itens. O item que sai recebe uma cópia do NBT.
- Salvamento: só no evento do overworld, só os baús alterados (ou sem arquivo), com gravação em outra thread (a
  fila do HL Otimização, esperada ao fechar o mundo). O arquivo de um baú removido é apagado na mesma fila.

Liga sozinho (`auto`) só se o Re-Avaritia **1.4.2** estiver instalado. Com outra versão, fica desligado, porque
os mixins podem não encaixar. `on` força.

## Limitações

- O NBT de itens com várias variantes guardadas na mesma chave errada não tem como ser separado. Por exemplo,
  diamantes sem NBT que caíram numa chave de livro encantado continuam juntos na chave consertada.
- Não muda o cálculo do hash (`gzip + SHA-256` do NBT a cada inserção, inclusive nas simulações dos canos). Mudar
  isso trocaria as chaves já salvas.
- Testado só no ambiente de desenvolvimento (nomes mapeados). O jar final usa os nomes SRG, e o refmap foi
  conferido, mas não foi aberto num cliente com o pack.
