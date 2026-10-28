# Mod Otimização — HL 1.20.1

Levantamento dos mods de otimização instalados no modpack **HL** (CurseForge). A instância fica na pasta `HL 1.18 (1)`.

## Como gerar a lista de mods

No Windows, rode o script no PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\listar-mods.ps1
```

Por padrão ele lê `%USERPROFILE%\curseforge\minecraft\Instances\HL 1.18 (1)\mods`.
Se a instância estiver em outro lugar, passe o caminho:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\listar-mods.ps1 -Instancia "D:\caminho\outra-instancia"
```

O resultado é salvo em `mods-instalados.txt`.

## Mods de otimização instalados

_Pendente: a lista é preenchida a partir de `mods-instalados.txt`._
