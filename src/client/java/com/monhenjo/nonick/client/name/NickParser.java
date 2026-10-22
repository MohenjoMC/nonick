package com.monhenjo.nonick.client.name;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out which part of a tab list display name is a nickname.
 *
 * <p>Servers decorate names in many ways: {@code [VIP] Steve}, {@code ~Steve},
 * {@code Steve [Team]}, or just {@code xX_Steve_Xx}. Braced groups are treated
 * as decorations, so the text left over is the name the server is actually
 * showing. The real account name is removed from that list before a nickname
 * is chosen.
 */
public final class NickParser {
	private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
	private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_]{3,16}");

	private static final Set<String> RANK_WORDS = Set.of(
		"ADMIN", "ADMINISTRATOR", "BUILDER", "DEFAULT", "DEV", "DEVELOPER",
		"GUEST", "HELPER", "MEDIA", "MEMBER", "MOD", "MODERATOR", "MVP",
		"OWNER", "STAFF", "SUPPORT", "VIP", "YOUTUBE", "YT"
	);

	private NickParser() {
	}

	public record Parsed(String primary, List<String> aliases) {
		public static final Parsed EMPTY = new Parsed(null, List.of());

		public boolean nicked() {
			return primary != null || !aliases.isEmpty();
		}
	}

	public static boolean looksLikeUsername(String value) {
		return value != null && USERNAME.matcher(value).matches();
	}

	/**
	 * @param profileName the name in the player info packet
	 * @param realName    the name from the UUID lookup, or {@code profileName}
	 * @param displayText the plain text of the tab list display name, or null
	 */
	public static Parsed parse(String profileName, String realName, String displayText) {
		String real = realName == null || realName.isBlank() ? profileName : realName;
		if (displayText == null) {
			return real.equals(profileName) ? Parsed.EMPTY : new Parsed(profileName, List.of(profileName));
		}

		String display = displayText.strip();
		if (display.isEmpty()) {
			return real.equals(profileName) ? Parsed.EMPTY : new Parsed(profileName, List.of(profileName));
		}

		Set<String> aliases = new LinkedHashSet<>();
		List<String> candidates = new ArrayList<>();

		for (String token : tokensOutsideBrackets(display)) {
			if (token.equalsIgnoreCase(real) || isRankWord(token) || isAllCapsDecoration(token)) {
				continue;
			}
			aliases.add(token);
			candidates.add(token);
		}

		// A nickname with a space cannot be a Java account name. Keep the whole
		// text as an alias so chat names that use it can still be rewritten.
		if (display.indexOf(' ') >= 0 && !display.equalsIgnoreCase(real)) {
			aliases.add(display);
		}

		if (!real.equals(profileName)) {
			// The server rewrote the profile name itself. That rewritten value
			// is the nickname shown above the player's head.
			aliases.add(profileName);
			String primary = candidates.size() == 1 ? candidates.getFirst() : profileName;
			return new Parsed(primary, List.copyOf(aliases));
		}

		if (candidates.isEmpty()) {
			aliases.remove(display);
			if (aliases.isEmpty() && !TokenReplacer.containsToken(display, real)) {
				// No ASCII name token but the display is not the real name.
				// Keep the whole string so a Unicode nickname can still match.
				return new Parsed(display, List.of(display));
			}
			return aliases.isEmpty() ? Parsed.EMPTY : new Parsed(null, List.copyOf(aliases));
		}

		if (candidates.size() == 1) {
			return new Parsed(candidates.getFirst(), List.copyOf(aliases));
		}

		// More than one plausible name. Do not guess which one is the nickname,
		// but keep them as aliases so a chat name component can still match.
		return new Parsed(null, List.copyOf(aliases));
	}

	/** Returns name-like tokens that are not inside [], (), {}, or <>. */
	public static List<String> tokensOutsideBrackets(String text) {
		List<String> tokens = new ArrayList<>();
		if (text == null || text.isEmpty()) {
			return tokens;
		}

		Matcher matcher = TOKEN.matcher(text);
		while (matcher.find()) {
			if (bracketDepthAt(text, matcher.start()) == 0) {
				tokens.add(matcher.group());
			}
		}
		return tokens;
	}

	private static int bracketDepthAt(String text, int index) {
		int depth = 0;
		for (int i = 0; i < index; i++) {
			switch (text.charAt(i)) {
				case '[', '(', '{', '<' -> depth++;
				case ']', ')', '}', '>' -> depth = Math.max(0, depth - 1);
				default -> {
				}
			}
		}
		return depth;
	}

	private static boolean isRankWord(String token) {
		return RANK_WORDS.contains(token.toUpperCase(Locale.ROOT));
	}

	/**
	 * Team tags are often all caps and sit outside brackets, for example
	 * {@code Steve RED}. Do not treat those as a nickname.
	 */
	private static boolean isAllCapsDecoration(String token) {
		return token.length() <= 12 && token.equals(token.toUpperCase(Locale.ROOT));
	}
}
