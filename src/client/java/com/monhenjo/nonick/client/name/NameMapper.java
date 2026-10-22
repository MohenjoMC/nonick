package com.monhenjo.nonick.client.name;

import com.mojang.authlib.GameProfile;
import com.monhenjo.nonick.NoNick;
import com.monhenjo.nonick.client.config.NoNickConfig;
import com.monhenjo.nonick.client.config.NoNickConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the UUID -> visible name -> real name table up to date and hands
 * rewritten components to the render and chat mixins.
 */
public final class NameMapper {
	private final MojangNameCache cache;
	private final Map<UUID, KnownPlayer> players = new ConcurrentHashMap<>();
	private final Map<UUID, String> announced = new HashMap<>();
	private ClientPacketListener connection;
	private int tickCounter;

	public NameMapper(Path configDir) {
		this.cache = new MojangNameCache(configDir);
	}

	public void tick(Minecraft minecraft) {
		NoNickConfig config = NoNickConfigManager.get();
		drainLookups(minecraft, config);

		ClientPacketListener current = minecraft.getConnection();
		if (current == null) {
			if (connection != null) {
				connection = null;
				players.clear();
				announced.clear();
			}
			return;
		}

		if (current != connection) {
			connection = current;
			players.clear();
			announced.clear();
			tickCounter = 0;
		}

		if (++tickCounter % 20 == 0) {
			scan(current, config);
		}
		cache.saveIfDirty();
	}

	private void scan(ClientPacketListener listener, NoNickConfig config) {
		for (PlayerInfo info : listener.getOnlinePlayers()) {
			GameProfile profile = info.getProfile();
			if (profile == null || profile.id() == null) {
				continue;
			}

			UUID id = profile.id();
			KnownPlayer known = players.computeIfAbsent(id, KnownPlayer::new);
			String display = info.getTabListDisplayName() == null ? null : info.getTabListDisplayName().getString();
			String cached = config.mojangLookup || cache.hasFreshAnswer(id, config.cacheHours)
				? cache.cachedName(id, config.cacheHours)
				: null;
			String skinName = SkinNameExtractor.profileName(profile);

			if (known.update(profile.name(), display, cached, skinName) && config.notifyOnJoin) {
				announce(known);
			}

			if (config.mojangLookup && !known.lookupDone()
				&& !cache.hasFreshAnswer(id, config.cacheHours)) {
				cache.request(id, config.cacheHours);
			}
		}

		if (config.debugLog) {
			NoNick.LOGGER.info("NoNick: {} players, {} nicked, {} cached lookups",
				players.size(), nickedPlayers().size(), cache.size());
		}
	}

	private void drainLookups(Minecraft minecraft, NoNickConfig config) {
		MojangNameCache.Result result;
		while ((result = cache.poll()) != null) {
			cache.applyResult(result);
			KnownPlayer known = players.get(result.id());
			if (known == null) {
				continue;
			}
			if (result.retry()) {
				continue;
			}

			if (result.name() != null) {
				known.markLookupDone();
				if (known.applyLookup(result.name()) && config.notifyOnJoin) {
					announce(known);
				}
			} else if (known.lookupFailed() && config.notifyOnJoin) {
				announce(known);
			}
			if (config.debugLog) {
				NoNick.LOGGER.info("NoNick lookup {} -> {}", result.id(), result.name());
			}
		}
	}

	/**
	 * Rewrites a component that contains a player's visible name. Returns the
	 * original component when the player is not nicked, so callers can keep
	 * the exact object they already had.
	 */
	public Component format(UUID id, Component original) {
		NoNickConfig config = NoNickConfigManager.get();
		if (!config.enabled || original == null) {
			return original;
		}
		KnownPlayer known = players.get(id);
		if (known == null || !known.nicked()) {
			return original;
		}

		String visible = original.getString();
		String alias = known.aliasFor(visible);
		return ComponentRewriter.reveal(
			original,
			known.realName(),
			alias,
			known.nick(),
			config.keepDecorations && alias != null,
			config.showNickname
		);
	}

	public ChatType.Bound formatPlayerChat(UUID sender, ChatType.Bound bound) {
		NoNickConfig config = NoNickConfigManager.get();
		if (!config.enabled || !config.replaceChat || sender == null || bound == null) {
			return bound;
		}

		Component name = format(sender, bound.name());
		var target = bound.targetName().map(component -> formatGeneric(component, false, config.keepDecorations));
		if (name == bound.name() && target.equals(bound.targetName())) {
			return bound;
		}
		return new ChatType.Bound(bound.chatType(), name, target);
	}

	public ChatType.Bound formatDisguisedChat(ChatType.Bound bound) {
		NoNickConfig config = NoNickConfigManager.get();
		if (!config.enabled || !config.replaceChat || bound == null) {
			return bound;
		}
		Component name = formatGeneric(bound.name(), config.showNickname, config.keepDecorations);
		var target = bound.targetName().map(component -> formatGeneric(component, false, config.keepDecorations));
		if (name == bound.name() && target.equals(bound.targetName())) {
			return bound;
		}
		return new ChatType.Bound(bound.chatType(), name, target);
	}

	public Component formatSystemMessage(Component message) {
		NoNickConfig config = NoNickConfigManager.get();
		if (!config.enabled || !config.replaceSystemMessages || message == null) {
			return message;
		}
		Component result = formatGeneric(message, false, true);
		if (config.debugLog) {
			NoNick.LOGGER.info("NoNick system message: '{}' -> '{}'", message.getString(), result.getString());
		}
		return result;
	}

	private Component formatGeneric(Component original, boolean showNickname, boolean keepDecorations) {
		NoNickConfig config = NoNickConfigManager.get();
		String visible = original.getString();
		KnownPlayer best = null;
		String bestAlias = null;

		for (KnownPlayer candidate : players.values()) {
			if (!candidate.nicked()) {
				continue;
			}
			String alias = candidate.aliasFor(visible);
			if (alias != null && (bestAlias == null || alias.length() > bestAlias.length())) {
				best = candidate;
				bestAlias = alias;
			}
		}

		if (best == null) {
			if (config.debugLog) {
				NoNick.LOGGER.info("NoNick system lookup: no alias in '{}' (players={})", visible, players.size());
			}
			return original;
		}
		return ComponentRewriter.reveal(
			original,
			best.realName(),
			bestAlias,
			best.nick(),
			keepDecorations,
			showNickname
		);
	}

	public List<KnownPlayer> nickedPlayers() {
		return players.values().stream()
			.filter(KnownPlayer::nicked)
			.sorted(Comparator.comparing(KnownPlayer::realName, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	public List<KnownPlayer> allPlayers() {
		return new ArrayList<>(players.values());
	}

	public KnownPlayer get(UUID id) {
		return players.get(id);
	}

	public KnownPlayer find(String query) {
		if (query == null || query.isBlank()) {
			return null;
		}
		String needle = query.toLowerCase(Locale.ROOT);
		for (KnownPlayer known : players.values()) {
			if (known.realName().toLowerCase(Locale.ROOT).equals(needle)
				|| known.profileName().toLowerCase(Locale.ROOT).equals(needle)
				|| known.nick() != null && known.nick().toLowerCase(Locale.ROOT).equals(needle)) {
				return known;
			}
		}
		return null;
	}

	public void forceLookup(KnownPlayer known) {
		known.resetLookup();
		cache.forceRequest(known.id());
	}

	public void clearSession() {
		players.clear();
		announced.clear();
		connection = null;
	}

	public void clearCache() {
		cache.clear();
		players.clear();
		announced.clear();
	}

	public int cachedCount() {
		return cache.size();
	}

	public void close() {
		cache.close();
	}

	private void announce(KnownPlayer known) {
		if (known == null || !known.nicked()) {
			return;
		}
		String nick = known.nick() != null && !known.nick().isBlank() ? known.nick() : known.profileName();
		if (nick == null || nick.isBlank() || nick.equalsIgnoreCase(known.realName())) {
			return;
		}
		if (known.realName().equals(announced.get(known.id()))) {
			return;
		}
		announced.put(known.id(), known.realName());

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.gui == null || minecraft.gui.hud == null) {
			return;
		}
		minecraft.gui.hud.getChat().addClientSystemMessage(
			Component.literal("[NoNick] ").withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.translatable("text.nonick.detected", nick, known.realName())
					.withStyle(ChatFormatting.YELLOW))
		);
	}
}
