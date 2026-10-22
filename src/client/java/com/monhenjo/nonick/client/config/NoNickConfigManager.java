package com.monhenjo.nonick.client.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

/**
 * Keeps the static config holder out of {@link NoNickConfig}. Cloth Config
 * walks every field on the config class, including static ones, and would
 * otherwise create an empty "default" category for the holder.
 */
public final class NoNickConfigManager {
	private static final NoNickConfig FALLBACK = new NoNickConfig();
	private static ConfigHolder<NoNickConfig> holder;

	private NoNickConfigManager() {
	}

	public static void init() {
		holder = AutoConfig.register(NoNickConfig.class, GsonConfigSerializer::new);
	}

	public static NoNickConfig get() {
		return holder == null ? FALLBACK : holder.getConfig();
	}

	public static void save() {
		if (holder != null) {
			holder.save();
		}
	}

	public static void reset() {
		if (holder != null) {
			holder.resetToDefault();
			holder.save();
		}
	}
}
