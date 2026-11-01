# Pelt — server-side skins for offline-mode Forge 1.12.2

Pelt gives players their skins on an `online-mode=false` Forge 1.12.2 server
(built for RLCraft Dregora) without any client mod. On join it puts a
`textures` property on the player's GameProfile **before** the tab-list and
spawn packets go out, so vanilla clients render the skin. Skins come from:

1. **Mojang**: the premium account with the player's name (signed by Mojang).
2. **Ely.by**: `skinsystem.ely.by` (falls back to Mojang for premium names).
3. **MineSkin** (optional, needs an API key): `/skin set <image URL>`, and
   re-hosting Ely.by-only skins on `textures.minecraft.net` so every vanilla
   client shows them.

Lookups run on a background thread and are cached in `config/pelt/skins.json`.
If a lookup fails or times out, the player just keeps the default skin. Pelt
never kicks anyone and never blocks the server thread.

Requirements: Forge 1.12.2 (14.23.5.x) and **FermiumBooter ≥ 1.4.1**. Pelt
uses FermiumBooter's early-mixin API (`FermiumRegistryAPI.enqueueMixin(false, "mixins.pelt.json")`).
MixinBooter is **not** needed and must not be added just for Pelt.

---

## Install (production)

1. **Back up** `mods/` and `config/` (see [Rollback](#rollback)).
2. Download `pelt-<version>.jar` from the GitHub Release (or from the
   `pelt-jar` artifact of a CI run) and drop it into `mods/`.
3. Check that `mods/` already contains FermiumBooter (`fermiumbooter-1.4.1*.jar`).
4. **Remove CustomSkinLoader from the server's `mods/`.** It is a client mod.
   On a server it does nothing useful and can interfere with profile
   properties. Players may keep it on their clients. With it, they see skins
   from CustomSkinLoader's own sources; without it, they see the skin Pelt sends.
5. **Make sure mod-director won't remove the jar.** mod-director syncs `mods/`
   to its manifest on launch and can delete jars it doesn't know about. Before
   restarting:
   - open mod-director's config (usually `config/mod-director/*.json`, or
     whatever file your launch script points it at);
   - add Pelt as a managed/kept entry, or turn off the "remove unlisted mods"
     behaviour for this jar, using whatever format your mod-director
     version uses;
   - after the first restart, confirm that `mods/pelt-*.jar` is still there
     and that `logs/latest.log` contains `[pelt]: Mixin applied`.
6. Optional: put a MineSkin API key in `config/pelt.cfg` (`mineskinApiKey`).
   Get one at <https://account.mineskin.org>.
7. Start the server and run the [manual test checklist](#manual-prod-test-checklist).

Pelt is server-only. Clients don't need it and can join without it
(`acceptableRemoteVersions="*"`).

---

## Config: `config/pelt.cfg`

Created on first start. `/skin reload` re-reads it without a restart.

| Key | Default | Meaning |
|---|---|---|
| `sources` | `mojang`, `ely` | Sources tried for player names, in order. Known: `mojang`, `ely`. |
| `cacheTtlHours` | `24` | Name-based skins older than this are re-fetched (in the background) when the player joins. URL skins never expire. |
| `missTtlMinutes` | `60` | When no source has a skin for a name, wait this long before asking again. |
| `httpTimeoutMs` | `5000` | Connect/read timeout per HTTP request. |
| `mineskinApiKey` | *(empty)* | MineSkin API key. Without it, URL skins and Ely re-hosting are off. |
| `useUsernameByDefault` | `true` | Players who never used `/skin set` get the skin of the account with their username. |
| `setCooldownSeconds` | `30` | Per-player cooldown for `/skin set`. Holders of `pelt.skin.other` bypass it. |
| `allowUrlSkins` | `true` | Allow `/skin set <http(s) url>` (needs `mineskinApiKey`). |
| `rehostNonMojangSkins` | `true` | Re-upload skins not hosted on `textures.minecraft.net` (Ely.by-only skins) through MineSkin, so vanilla clients show them. Only active with an API key. |
| `debug` | `false` | Verbose logging. |

Cache: `config/pelt/skins.json` (uuid → input, source, texture value/signature,
fetch time, whether it was set explicitly). Writes are atomic. A corrupt file is
renamed to `skins.json.corrupt-<time>` and Pelt starts with an empty cache.

---

## Commands

| Command | What it does | Permission |
|---|---|---|
| `/skin set <name>` | Use the skin of the Mojang/Ely.by account `<name>`. | `pelt.skin.set` |
| `/skin set <url>` | Use a skin image URL (PNG, 64×64 or 64×32), uploaded via MineSkin. | `pelt.skin.set` |
| `/skin set <player> <name\|url>` | Same, for another online player. | `pelt.skin.other` |
| `/skin clear` | Remove your custom skin and go back to the default (your username's skin, or Steve/Alex). | `pelt.skin.set` |
| `/skin clear <player>` | Same, for another online player. | `pelt.skin.other` |
| `/skin reload` | Re-read `config/pelt.cfg` and re-fetch skins of all online players, ignoring cache age. | `pelt.skin.reload` |
| `/skin reload <player>` | Same, for one player. | `pelt.skin.reload` |

After a change, **other players** see the new skin right away: Pelt re-sends
the tab-list entry and respawns the player entity for everyone tracking them.
The player **sees their own new skin after they rejoin**. Vanilla 1.12 clients
cache their own skin, and the respawn trick that works around this is risky with
RLCraft's dimension mods, so Pelt doesn't use it.

## Permissions

These are registered with Forge's `PermissionAPI`, so permission mods
(LuckPerms-Forge, FTB Utilities ranks, …) can grant or deny them.

| Node | Default |
|---|---|
| `pelt.skin.set` | everyone |
| `pelt.skin.other` | ops (level 2+), also bypasses the cooldown |
| `pelt.skin.reload` | ops |

The server console and RCON can run every sub-command. Command blocks need op level 2.

---

## Manual prod test checklist

Run this during downtime on prod or on a copy of it. Use two clients, **A**
and **B**, ideally vanilla with no CustomSkinLoader, plus one client that has it.

**Boot**

- [ ] 1. The server starts normally. Startup time is about the same as before.
- [ ] 2. `logs/latest.log` contains:
      - `[pelt]: Enqueued mixins.pelt.json with FermiumBooter for early mixin application`
      - `FermiumBooter adding "mixins.pelt.json" for early mixin application`
      - `[pelt]: Mixin applied: pelt.mixin.MixinPlayerList -> ...PlayerList`
      - `[pelt]: Mixin applied: pelt.mixin.EntityTrackerAccessor -> ...EntityTracker`
- [ ] 3. No `Mixin`-related `ERROR`/exception mentions `pelt` in `logs/latest.log` or `logs/fermiumbooter.log`.
- [ ] 4. `mods/pelt-*.jar` is still present after mod-director ran, and CustomSkinLoader is gone from the server's `mods/`.
- [ ] 5. `config/pelt.cfg` was created with the defaults above.

**Join**

- [ ] 6. A joins with a **premium** name (e.g. a real Mojang account name). B sees A's real skin on the first join (wait ~1–5 s, then it refreshes). A sees it in F5 after rejoining.
- [ ] 7. A rejoins: the skin is there **immediately** (cached, applied before the join packets).
- [ ] 8. A player with a **non-premium name that has an Ely.by skin** joins: others see the Ely skin. Without a MineSkin key, some vanilla clients may show Steve for Ely-hosted textures. With a key, the log shows source `ely+mineskin`.
- [ ] 9. A player with a name that exists nowhere joins: Steve/Alex, no errors, no kick. Rejoining within `missTtlMinutes` makes no new lookup (no WARN lines).

**Commands**

- [ ] 10. A (non-op): `/skin set Notch`. A gets "Skin set from mojang ('Notch')…" and B immediately sees Notch's skin on A.
- [ ] 11. A immediately runs `/skin set jeb_` again and gets the cooldown message.
- [ ] 12. *(MineSkin key set)* A: `/skin set https://<a direct PNG skin URL>`. B sees it. The source is `mineskin`.
- [ ] 13. A: `/skin set https://...` **without** a MineSkin key gives a clear error message, and nothing changes.
- [ ] 14. A: `/skin clear`. A goes back to the username skin (or Steve), and B sees it.
- [ ] 15. A (non-op): `/skin set B Notch` → "You don't have permission (pelt.skin.other)".
- [ ] 16. Op: `/skin set A jeb_` works, and so does `/skin clear A`.
- [ ] 17. Console: `skin reload` logs `[pelt]: Reload complete: config re-read (...), refreshing N player(s)`, and online players keep or refresh their skins.
- [ ] 18. Edit `config/pelt.cfg` (e.g. `sources` to only `ely`) and run `skin reload`. The new setting takes effect without a restart. Revert it afterwards.
- [ ] 19. Bad input such as `/skin set "a b"` or `/skin set ftp://x` gives an error message and makes no network request.

**Persistence and failure modes**

- [ ] 20. Restart the server: skins are still applied on join (`config/pelt/skins.json` exists and has entries).
- [ ] 21. With outbound HTTP blocked or a bad DNS entry (or `httpTimeoutMs=500` plus an unreachable source), players still join normally, keep their cached or default skin, and the log shows one WARN per lookup. There's no kick and no TPS drop.
- [ ] 22. With 5+ players joining at once, there's no TPS spike (check with `/forge tps`).
- [ ] 23. A client **with** CustomSkinLoader still joins and renders skins. It may prefer its own sources.
- [ ] 24. Dimension change (Nether/End/RLCraft dimensions) and death/respawn don't lose the skin for others.

---

## Rollback

1. Stop the server.
2. Delete `mods/pelt-*.jar`.
   Optional: delete `config/pelt.cfg` and `config/pelt/`. They are inert without the jar.
3. If anything else changed, restore `mods/` and `config/` from the backup taken
   before install. Put CustomSkinLoader back if you had it on the server before.
4. If mod-director's config was edited for Pelt, restore that file too.
5. Start the server. Player data, worlds and inventories are not touched by
   Pelt: it only changes the in-memory GameProfile and its own cache file.

---

## How it works

- `pelt.core.PeltLoadingPlugin` (`FMLCorePlugin` in the manifest) enqueues
  `mixins.pelt.json` with FermiumBooter as an **early** mixin config, because
  the targets are vanilla classes. It does this in its constructor, or in
  `injectData`, which runs before FermiumBooter's (sorting index 500 < 990).
  If FermiumBooter is missing, it logs an error, and Pelt still loads without
  mixins.
- `MixinPlayerList` injects at the head of `PlayerList#initializeConnectionToPlayer`
  and copies the cached texture onto the profile (a cache read only).
- After `PlayerLoggedInEvent`, a stale or missing entry is fetched in the
  background. The result is applied on the server thread, and the player is
  refreshed for everyone else (tab-list REMOVE/ADD, then destroy/spawn for
  tracking players, through the `EntityTrackerAccessor` mixin).
- `mixins.pelt.json` has a mixin plugin that logs `Mixin applied: …` for each
  mixin. The CI smoke test relies on these lines.

## Building

The build uses RetroFuturaGradle 2.x and Gradle 9. Gradle must **run on JDK 25**, and it needs a Java 8 toolchain to compile:

```bash
./gradlew build -Porg.gradle.java.installations.fromEnv=JAVA_HOME_8_X64
```

The release jar is `build/libs/pelt-<version>.jar`. Compile-only dependencies:
FermiumBooter 1.4.1 (CurseMaven file `7279112`, pinned in `gradle.properties`) and
MixinBooter. MixinBooter is used only for the Mixin annotations and annotation
processor (refmap). Neither is bundled.

## CI

`.github/workflows/build.yml`:

- **build**: Temurin 8 + 21, Gradle and RFG caches, `./gradlew build` (unit
  tests with mocked HTTP for Mojang/Ely.by/MineSkin and the cache). It checks
  the jar's manifest, mixin config and refmap, and that Mixin/FermiumBooter are
  not bundled, then uploads the `pelt-jar` artifact.
- **smoke**: installs a fresh Forge 1.12.2-14.23.5.2860 dedicated server
  (cached), adds FermiumBooter 1.4.1 (version checked). It runs once for **each** CurseForge upload of 1.4.1, the original `7212281` and the "(Fix)" `7279112`, since either may be on prod and the built jar, sets
  `online-mode=false`, waits for `Done`, sends `skin reload` on stdin, then
  `stop`. It fails if a pelt marker line is missing, or if the log has a mixin
  error, an `ERROR`/`FATAL` line, an exception or a crash report. Logs are
  uploaded as `smoke-logs`.
- **release**: on a `v*` tag, attaches the jar to a GitHub Release.
