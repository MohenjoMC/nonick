package com.monhenjo.nonick.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.AutoConfigClient;

/**
 * Loaded by Mod Menu only when Mod Menu is installed. The mod still works
 * without it: the settings key opens the same screen.
 */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> AutoConfigClient.getConfigScreen(NoNickConfig.class, parent).get();
	}
}
