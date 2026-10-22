package com.monhenjo.nonick.client.name;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces a name only when it stands on its own. A nickname like {@code Nick}
 * must not rewrite the {@code Nick} inside {@code Nickname}.
 */
public final class TokenReplacer {
	private TokenReplacer() {
	}

	public static boolean containsToken(String input, String token) {
		if (input == null || token == null || token.isEmpty()) {
			return false;
		}
		if (input.equals(token)) {
			return true;
		}
		if (token.indexOf(' ') >= 0) {
			return input.contains(token);
		}
		return pattern(token).matcher(input).find();
	}

	public static String replaceToken(String input, String token, String replacement) {
		if (input == null || token == null || token.isEmpty() || token.equals(replacement)) {
			return input;
		}
		Matcher matcher = pattern(token).matcher(input);
		return matcher.replaceAll(Matcher.quoteReplacement(replacement));
	}

	private static Pattern pattern(String token) {
		if (token.indexOf(' ') >= 0) {
			return Pattern.compile(Pattern.quote(token));
		}
		return Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(token) + "(?![A-Za-z0-9_])");
	}
}
