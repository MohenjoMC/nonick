package com.monhenjo.nonick.client.name;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** One player and everything the client has learned about their visible name. */
public final class KnownPlayer {
	private final UUID id;
	private String profileName = "";
	private String displayText;
	private String skinName;
	private String realName = "";
	private String nick;
	private List<String> aliases = List.of();
	private boolean nicked;
	private boolean lookupDone;

	public KnownPlayer(UUID id) {
		this.id = id;
	}

	public UUID id() {
		return id;
	}

	public String profileName() {
		return profileName;
	}

	public String displayText() {
		return displayText;
	}

	public String realName() {
		return realName;
	}

	public String skinName() {
		return skinName;
	}

	public String nick() {
		return nick;
	}

	public boolean nicked() {
		return nicked;
	}

	public boolean lookupDone() {
		return lookupDone;
	}

	public void markLookupDone() {
		lookupDone = true;
	}

	public void resetLookup() {
		lookupDone = false;
	}

	/**
	 * @return true when this update just revealed a name the player had not seen
	 */
	public boolean update(String newProfileName, String newDisplayText, String cachedRealName, String newSkinName) {
		String oldReal = realName;
		boolean wasNicked = nicked;

		profileName = newProfileName == null ? "" : newProfileName;
		displayText = newDisplayText;
		skinName = newSkinName;
		if (cachedRealName != null && !cachedRealName.isBlank()) {
			realName = cachedRealName;
		} else if (skinName != null && !NickParser.looksLikeUsername(profileName)) {
			realName = skinName;
		} else {
			realName = profileName;
		}
		recompute();

		if (!nicked) {
			return false;
		}
		if (!wasNicked) {
			return true;
		}
		return !realName.equals(oldReal) && !realName.equals(profileName);
	}

	/**
	 * Called when Mojang has no answer. If the profile name was not a valid
	 * username and the skin blob carried a matching account name, use it.
	 *
	 * @return true when this changed the name that will be drawn
	 */
	public boolean lookupFailed() {
		String oldReal = realName;
		if (skinName != null && !skinName.isBlank() && !NickParser.looksLikeUsername(profileName)) {
			realName = skinName;
			recompute();
		}
		lookupDone = true;
		return nicked && !realName.equals(oldReal);
	}

	/** @return true when the lookup changed what will be drawn */
	public boolean applyLookup(String name) {
		String oldReal = realName;
		if (name != null && !name.isBlank()) {
			realName = name;
		}
		recompute();
		return nicked && !realName.equals(oldReal);
	}

	private void recompute() {
		NickParser.Parsed parsed = NickParser.parse(profileName, realName, displayText);
		nick = parsed.primary();
		aliases = parsed.aliases();
		nicked = !realName.equals(profileName) || parsed.nicked();
	}

	/**
	 * Finds the alias that appears in {@code visible}. The longest match wins,
	 * so a nickname with a space is preferred over one of its words.
	 */
	public String aliasFor(String visible) {
		if (!nicked || visible == null || visible.isBlank() || visible.equals(realName)) {
			return null;
		}

		Set<String> candidates = new LinkedHashSet<>(aliases);
		if (nick != null) {
			candidates.add(nick);
		}
		if (!profileName.equals(realName)) {
			candidates.add(profileName);
		}
		candidates.remove(realName);

		List<String> ordered = new ArrayList<>(candidates);
		ordered.sort(Comparator.comparingInt(String::length).reversed());
		for (String candidate : ordered) {
			if (TokenReplacer.containsToken(visible, candidate)) {
				return candidate;
			}
		}
		return null;
	}
}
