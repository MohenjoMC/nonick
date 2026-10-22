package com.monhenjo.nonick.client.name;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Reads the account name that Mojang embeds in a signed skin texture. The
 * texture blob is part of the GameProfile the server sends, so this costs no
 * request. It is only used when the profile name itself is not a valid
 * username, because the embedded name can be the name the skin was uploaded
 * under rather than the player's current name.
 */
public final class SkinNameExtractor {
	private SkinNameExtractor() {
	}

	public static String profileName(GameProfile profile) {
		if (profile == null || profile.id() == null || profile.properties() == null) {
			return null;
		}

		String accountId = profile.id().toString().replace("-", "");
		for (Property property : profile.properties().get("textures")) {
			String name = readName(property.value(), accountId);
			if (name != null) {
				return name;
			}
		}
		return null;
	}

	private static String readName(String value, String expectedId) {
		try {
			String json = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
			JsonObject object = JsonParser.parseString(json).getAsJsonObject();

			String profileId = object.has("profileId") ? object.get("profileId").getAsString() : null;
			String profileName = object.has("profileName") ? object.get("profileName").getAsString() : null;
			if (profileId == null || profileName == null || profileName.isBlank()) {
				return null;
			}
			if (!profileId.equalsIgnoreCase(expectedId) || !NickParser.looksLikeUsername(profileName)) {
				return null;
			}
			return profileName;
		} catch (Exception ignored) {
			return null;
		}
	}
}
