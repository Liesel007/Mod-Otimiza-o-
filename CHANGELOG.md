# Changelog

HLO: HL Optimization, for Forge 1.20.1. The detailed notes (in Portuguese, with test results) are in
[`mod/README.md`](mod/README.md#histórico-de-versões).

## 0.4.0

Stutter-focused release.

- **New `monitor_gc`** (both sides): logs GC pauses with collector, cause and heap, and detects whole-process
  freezes with no GC (OS swap, antivirus, power saving). Suggests JVM arguments (`-XX:+DisableExplicitGC`, G1 flags
  for large packs, `-Xmx` too large with integrated graphics) in the warnings tab, as text only.
- **New `diagnostico_fps`** (client): frame spikes with their likely mod and game phase, sampled only during slow
  frames (hooks cost ~6 us per frame). New **FPS tab**: average FPS, 1% and 0.1% lows, spikes per minute, frame time
  graph, top causes, last spikes with stacks, and a "what can be adjusted" list.
- **New `render_enxame`** (client): with many mobs of one type loaded, draws only the nearest 24 and none beyond
  24 blocks. Automatic only for non-hostile mobs; never hides bosses, named, tamed, targeted or fighting mobs.
- **New `distancia_block_entities`** (client): block entity renderers are skipped beyond 32 blocks (beacons, end
  gateways, conduits, chests and signs keep their vanilla distance).
- **New `salvamento_gradual`** (server): the autosave spreads chunk serialization over several ticks within a
  per-tick budget. Closing the world, `/save-all` and `/save-all flush` stay complete. Test: autosave tick 737 -> 54 ms.
- `limite_enxame` now also blocks mobs created without the spawn event (`EntityType.create` + `addFreshEntity`, like
  Chaos Persists ant hills). Babies, named, persistent and mounted mobs are never blocked. Counters per type
  include zeros. New `/hl enxame reduzir <type> [confirmar]`.
- Stalls without samples now name the JVM pause that caused them; the same tick is no longer reported twice.
- `tooltip_seguro` names the mod whose `ItemTooltipEvent` listener added the null line.
- JEI start time and failed JEI plugins are read from the log, with a suggestion when JEI takes over 20 s.
- Time on the main thread per class with its own `save(File)`, in the summary and the panel.
- Log format: existing prefixes and the `Slow tick:`, `Server stalled for`, `Likely cause:` and
  `Summary since the game started:` lines are unchanged. `Likely cause:` can now say `whole JVM stopped N ms by
  garbage collection (...)`; new optional line `JVM pause in the same interval:`; new prefixes `[monitor_gc]` and
  `[diagnostico_fps]`.

## 0.3.1

- **Panel redesign**: custom-drawn tabs, pill buttons, bars, section headers, striped lists, a draggable scrollbar and
  a footer bar.
- **Config tab without broken values**: numbers are edited with sliders limited to the range the server accepts
  (logarithmic for wide ranges, with ‹ › for fine steps and an AUTO switch for values that follow the preset);
  true/false values are switches; lists are checked as you type and turn red when invalid. Changed values are marked,
  each one has its own Undo, and the footer has Undo and Save (N). Nothing is sent while a value is invalid.
- The server now validates every editable key against the same ranges (it used to accept any number and
  unchecked lists).
- Network channel `hlotimizacao:tela`, which accepts any HLO version: different panel formats show a "update HLO"
  message instead of being refused or decoded wrongly.

## 0.3.0

- **Display name** is now "HLO: HL Optimization" (mod id unchanged: `hlotimizacao`). Generic English description.
- **Presets**: `preset=auto|leve|padrao|agressivo|personalizado` (`light|default|aggressive|custom` also work).
  Auto picks by mod count: up to 120 light, up to 250 default, more aggressive. Light turns off the modules that
  change gameplay (activation range, swarm limit, item cleanup, fast insertion) when they are on `auto`. The
  preset only sets defaults; values written in the config win.
- **English by default** for the log, `/hl`, warnings and config comments. Portuguese when Java runs in Portuguese
  or with `log.idioma=pt_br`.
- **Safe mode**: a module that throws during play falls back to vanilla for that call and turns itself off until
  restart after 3 errors (log, `/hl status avisos` and the panel say so).
- **HLO cost** in ms/tick in `/hl status desempenho` and on the panel's Performance tab.
- The panel shows the active preset. Its network channel is now `hlotimizacao:painel2`, so a 0.2.x client and a
  0.3.0 server (or the other way around) still connect; the panel just says the server does not have it.
- `limite_enxame.lista` is empty by default (it used to ship the HL pack's list).
- `/hl limpar`: the 10-second warning now reaches every player, not only whoever ran the command.
- Neruina is detected (informational: it complements safe mode).
- `update.json` with `updateJSONURL`, plus issue tracker and homepage links in `mods.toml`. MIT `LICENSE` in the
  repository and in the jar.
- Tested: a production Forge client **without** HLO on a server with HLO; a production client with HLO on the same
  server; singleplayer with only HLO; a 0.2.2 client on a 0.3.0 server.

## 0.2.2

- Insertion into large inventories: Re-Avaritia Infinity Chest shortcut (no slot scan), `diagnostico_insercao`
  (who is pushing into what during a stall), optional `insercao_rapida` with a cache and an optional per-tick budget.

## 0.2.1

- `alcance_ativacao` (activation range) and `limite_enxame` (per-type swarm limit), `/hl entidades`.
- Load test up to 40,000 entities. Fixed the shutdown hook error in async saving.

## 0.2.0

- In-game panel: `/hl` or F8, with tabs for overview, modules, performance, lag, cleanup, warnings and config.

## 0.1.x

- 0.1.6: item cleanup timed in real time (it stalled when the server was behind), lag blame for wait patterns,
  heavy dimension warning.
- 0.1.5: `/hl status` split into pages (overview, modules, performance, lag, warnings).
- 0.1.4: Re-Avaritia Infinity Chest fix, item cleanup and the `/hl` command.
- 0.1.3: lag diagnostics improvements and the status summary.
- 0.1.2: `tooltip_seguro`, `diagnostico_lag`, more startup checks in the conflict report.
- 0.1.1: async saving fix for SavedData names that are invalid file names on Windows.
- 0.1.0: first version (async saving, despawn with items, inventory trigger, GPU leak, unfocused FPS, empty toasts,
  conflict report).
