---
name: nonick-testing
description: "Testing loop for the NoNick client-side Fabric mod (MohenjoMC/nonick) — unit-test the name parser, run the Loom client gametest and read back screenshots, verify mixin targets against decompiled Minecraft sources, and build the whole 26.x matrix with -P overrides. Use when building, testing or debugging NoNick, adding a mixin or resolution step, porting to a new 26.x release, or when a gradle run misbehaves on the Mac or e3 build hosts."
version: 1.0.0
author: MohenjoMC
license: CC0-1.0
platforms: [linux, macos]
metadata:
  hermes:
    tags: [minecraft, fabric, testing, gametest, mixin, screenshots, loom]
    category: minecraft-modding
---

# nonick-testing

NoNick rewrites player names on the client: nametags, tab list, chat, server
messages. Three test layers answer three different questions, in order. Run the
cheapest layer first; a layer only earns its boot-cost once the layer above it
is green.

1. **Unit** — does the parser agree with itself on plain strings?
2. **Gameload** — do the mixins still reach the real client after a world loads?
3. **Pixels** — does the rewrite land where a player actually reads it?

## Layer 0 — aim the mixins before you write them

Every mixin target is checked against decompiled Minecraft sources first.

```sh
./gradlew genSources                      # writes mapped sources to loom cache
find .gradle ~/.gradle/caches/fabric-loom -name "*sources*.jar" -path "*minecraft*"
```

Unpack a copy into a scratch dir (history used `/tmp/opencode/mcsrc*`) and grep
the exact class, method name and signature before pinning a mixin. A mismatch
fails at mixin-apply time with a log line naming the class — that means the
mapping moved, so re-grep and fix the target. `ClientCompat` keeps the
version-drift shims (e.g. `InputConstants.Type` KEYBOARD vs KEYSYM, screen
accessors); extend it rather than hardcoding a version check at the mixin.
Done when: the mixin applies and the gamelog shows no apply errors.

## Layer 1 — unit tests on pure logic

`NickParser`, `TokenReplacer` and friends deliberately carry **no Minecraft
classes**, so they run in plain JUnit with no client boot:

```sh
./gradlew test
```

Write tests with real-shaped inputs, not strings you invented twice:

```java
NickParser.parse("Steve", "Steve", "[VIP] xX_Nick_Xx")   // rank prefix + nick -> nicked, primary "xX_Nick_Xx"
NickParser.parse("xX_Nick_Xx", "Steve", "xX_Nick_Xx")    // profile name rewritten -> nicked
NickParser.parse("Steve", "Steve", "Steve [Team Red]")   // tag after the name -> not nicked
NickParser.parse("Steve", "Steve", "Alex Taylor")        // two candidates -> nicked, no primary guess
```

Cover: whole-token replacement, no replacement inside a word (`Nickname`),
punctuation boundaries, aliases with spaces, and the username rule
(3–16 chars of `[A-Za-z0-9_]`). Done when: `test` exits 0 with every case
represented.

## Layer 2 — client gametest in a real world

The harness lives in `build.gradle` and `src/gametest/`:

```gradle
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "nonick-gametest"
        enableGameTests = false
        enableClientGameTests = true
        eula = true
    }
}
```

The test class implements `FabricClientGameTest#runTest(ClientGameTestContext)`
and boots a genuine singleplayer world — that is what loads the packet listener
and entity renderers the mixins hook into:

```sh
./gradlew runClientGameTest
```

**No fake server.** Two injections cover every rewrite path without one:

```java
// nickname a real player: reach into the packet table the mixin reads
context.runOnClient(minecraft -> {
    var info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
    info.setTabListDisplayName(Component.literal("xX_Nick_Xx"));
});

// server text that carries the nickname, straight from the integrated server
singleplayer.getServer().runCommand("say xX_Nick_Xx joined the game");
```

`minecraft.getConnection().getPlayerInfo(...)` is the exact table the
player-info packet handler fills, so the mixins are exercised on live data. The
integrated server makes chat flows without any second process.

Sequencing rules that keep the test honest:

- `context.waitTicks(n)` after every mutation — render code needs real ticks.
- Prefer `context.computeOnClient(...)` for assertions that need a value.
- Drive the keyboard for the tab list:
  `context.getInput().holdKey(o -> o.keyPlayerList)` → `waitTicks(5)` →
  screenshot → `releaseKey`.
- Screens are checked by truth, not by trust:
  `computeOnClient(minecraft -> ClientCompat.currentScreen(minecraft) != null)`
  throws an `AssertionError` on false.
- Turn `NoNickConfigManager.get().debugLog = true` at the start when hunting a
  miss; the debug log says why a name was skipped.

Done when: the run exits 0 with no apply or assertion errors.

## Layer 3 — read the screenshots back

`runClientGameTest` writes PNGs into the gametest client's game dir:

```
build/run/clientGameTest/screenshots/0000_<name>.png
```

The `000N_` prefix is the sequence of `context.takeScreenshot("name")` calls.
Pull them off the build host and actually look at each one — a passing exit
code only proves nothing *threw*, not that text rendered correctly:

```sh
tar czf - -C ~/nonick-build/build/run/clientGameTest screenshots \
  | ssh e3 'tar xzf - -C /tmp'    # or a plain scp; then open the PNGs
```

Expected pairs, and the failure each one exposes:

| screenshot | proves |
|---|---|
| `nonick-settings` | the Cloth Config screen opens at all |
| `nonick-status` | client commands run in a loaded world |
| `nonick-chat` | chat + server-message rewriting with a live nick |
| `nonick-tab` | tab-list rendering while `keyPlayerList` is held |

Done when: every screenshot shows the rewritten text a human would read.

## Layer 4 — the 26.x matrix

One source tree builds every version through `-P` overrides; the matrix lives
in `.github/workflows/build.yml`:

```sh
for v in 26.1 26.1.1 26.1.2 26.2 26.3; do
  case $v in
    26.2) api=0.161.0+26.2; cloth=26.2.155; menu=20.0.3; mcdep='~26.2'; cdep='>=26.2.0';;
    26.3) api=0.164.0+26.3; cloth=26.3.159; menu=21.0.0; mcdep='~26.3'; cdep='>=26.3.0';;
    *)    api=0.155.3+26.1.2; cloth=26.1.154; menu=18.0.2; mcdep='~26.1'; cdep='>=26.1.0';;
  esac
  ./gradlew build -Pminecraft_version=$v -Pfabric_api_version=$api \
    -Pcloth_config_version=$cloth -Pmodmenu_version=$menu \
    -Pminecraft_dependency=$mcdep -Pcloth_config_dependency=$cdep
done
```

Split the work: gametests need a display host (the Mac has one; CI needs
xvfb), CI compiles the rest of the matrix. First compile error after bumping a
version names the API that moved — check `ClientCompat` there first. Done
when: all five versions build clean and drop `build/libs/nonick-1.0.0+<mc>.jar`.

## Jar forensics

Before publishing, confirm the artifact says what you think it says:

```sh
unzip -p jar assets/nonick/icon.png | sha1sum        # icon actually embedded
sha1sum jar                                          # then match it against
                                                     # the Modrinth cdn file
```

## Build hosts

Heavy runs go on the Mac (`ssh mac`, checkout `~/nonick-build`, Java via
`export JAVA_HOME=$(/usr/libexec/java_home -v 25)`; the tree is piped over with
`tar czf - --exclude=./.gradle --exclude=./build --exclude=./.git | ssh mac
'tar xzf - -C ~/nonick-build'`). The local box and e3 are fallbacks; loom caches
per box, so the first run on a host is the slow one.
