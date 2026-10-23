# NoNick

NoNick is a client-side Fabric mod for Minecraft 26.3. It shows the real
Minecraft account name behind a server nickname.

Nicknames usually live in one of three places:

1. The tab list display name. The player info packet contains both the
   account profile and an optional display name. NoNick reads the profile.
2. The profile name itself. Some servers rewrite it so the nickname also
   appears above the player's head. NoNick asks Mojang which account owns the
   UUID and uses that name instead.
3. The chat type's name component. NoNick rewrites that component before the
   message is drawn, so the message text itself is untouched.

NoNick resolves names in steps rather than trusting one source:

1. The profile name in the player info packet.
2. The tab list display name, which shows whether the profile name is being
   replaced.
3. The name Mojang embeds in the skin texture, when its profile ID matches the
   player. This is used only when the profile name is not a valid username,
   because the skin name can be old.
4. Mojang's session server.
5. Mojang's profile lookup service at `api.minecraftservices.com`.
6. Mojang's profile lookup service at `api.mojang.com`.
7. The local on-disk cache.

The UUID check tries the three official endpoints in order. If one host is
down or rate-limited, the next one gets a chance. `/nonick who <name>` also
shows the name found in the skin texture when it differs from the name being
drawn.

## What it changes

- Names above players' heads, with team colors and prefixes kept by default.
- Names in the tab list.
- The sender name in player chat.
- Nicknames in join, leave, and death messages.
- Optional chat note when a nickname is first detected.

Everything happens on your client. No packets are sent to the server except
the optional Mojang UUID lookup described below.

## Before you use it

Some servers forbid revealing staff nicknames or hidden identities. NoNick
only changes your own screen, but using it can still break that server's rules.
Check the rules before you join.

## Download

- Modrinth: https://modrinth.com/mod/nonick
- GitHub releases: https://github.com/MohenjoMC/nonick/releases

## Install

1. Install Fabric Loader 0.19.5 or newer for Minecraft 26.3.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) and
   [Cloth Config](https://modrinth.com/mod/cloth-config) in your `mods` folder.
3. Put the jar for your Minecraft version, for example `nonick-1.0.0+26.3.jar`, in the same folder.
4. Mod Menu is optional. Install it if you want a settings button in the mod
   list.

## Supported versions

The build matrix covers every Minecraft 26.x release:

| Minecraft | Fabric API | Cloth Config |
| --- | --- | --- |
| 26.1 | 0.155.3+26.1.2 | 26.1.154 |
| 26.1.1 | 0.155.3+26.1.2 | 26.1.154 |
| 26.1.2 | 0.155.3+26.1.2 | 26.1.154 |
| 26.2 | 0.161.0+26.2 | 26.2.155 |
| 26.3 | 0.164.0+26.3 | 26.3.159 |

Each build produces its own jar, named `nonick-1.0.0+<minecraft>.jar`.

## Controls

| Key | Action |
| --- | --- |
| `F6` | Turn NoNick on or off |
| `F7` | Open the settings screen |
| unbound | Copy the real name of the player in your crosshair |

All three keys can be changed in Minecraft's controls screen under the NoNick
category.

## Commands

`/nonick` and `/nonick status` show whether NoNick is on, how many players are
using a nickname, and how many UUID answers are cached.

`/nonick toggle` flips the master switch.

`/nonick list` lists the nicknamed players on the server, up to 20 at a time.

`/nonick who <name>` shows the real name, visible name, and UUID for one
player. You can search by real name, profile name, or nickname.

`/nonick lookup <name>` asks Mojang again for that player's account name.

`/nonick copy` copies the real name of the player in your crosshair.

`/nonick config` opens the settings screen.

`/nonick clear` removes the cached names and the current player table.

## Settings

The settings screen has four groups.

**General** turns the whole mod on or off.

**Where names appear** controls nametags, the tab list, player chat, and system
messages. `Keep server decorations` keeps prefixes, suffixes, team colors, and
click actions. Turn it off if you want a plain real name. `Show the nickname
too` appends the nickname in gray, for example `Steve (xX_Nick_Xx)`.

**Detection** controls the join note, the Mojang UUID check, and the cache
lifetime. The UUID check is on by default and only runs for online-mode UUIDs.
It sends the UUID to `sessionserver.mojang.com` and reads the account name from
the response. Answers are stored in `config/nonick-names.json` for 24 hours by
default. Set the cache to a lower value if you change accounts often, or a
higher value on a stable server.

**Advanced** turns on debug logging. Use it when a server hides names in a way
the mod does not recognize.

## What it cannot do

A server can change more than the name. NoNick cannot recover the real name
when:

- The server changes the player's UUID as well as the name. The UUID is the
  only link to the Minecraft account, so a new UUID points at nothing.
- The server runs in offline mode and generates its own UUIDs. Mojang has no
  account for those UUIDs, and the profile name is the only name the client
  receives.
- A nickname plugin draws names with armor stands, text displays, or a custom
  chat format that does not use the player's name component. Those are not
  player names from the client's point of view, so the mod leaves them alone.
- The server sends a nickname that is identical to the real name in every
  packet. There is nothing to compare against.

On a server that only changes the tab list display name, the profile name is
already the real one. The UUID check is not needed, but it still runs so the
mod can catch the profile-name case on other servers.

## Privacy

The mod sends one HTTPS request per online-mode UUID to Mojang's public session
server. It sends the UUID and a user agent string, nothing else. The response is
cached locally. Turn off `Check UUIDs with Mojang` if you do not want those
requests.

NoNick does not send chat contents, server addresses, or player names anywhere.

## Build

Java 25 is required.

```sh
./gradlew build
```

The remapped jar is in `build/libs/nonick-1.0.0+26.3.jar` by default.

To build another version, override the Minecraft and dependency versions:

```sh
./gradlew build \
  -Pminecraft_version=26.1.2 \
  -Pfabric_api_version=0.155.3+26.1.2 \
  -Pcloth_config_version=26.1.154 \
  -Pmodmenu_version=18.0.2 \
  -Pminecraft_dependency=~26.1 \
  -Pcloth_config_dependency='>=26.1.0'
```

GitHub Actions runs the full 26.x matrix on every push and uploads one artifact
per Minecraft version.

Run the unit tests with:

```sh
./gradlew test
```

The unit tests cover nickname parsing and whole-token replacement. A client
game test also opens the settings screen, runs the status command, and rewrites
a fake nickname in chat and the tab list:

```sh
./gradlew runClientGameTest
```

That task needs a desktop session because it starts a real Minecraft window.

## License

CC0-1.0. See `LICENSE`.
