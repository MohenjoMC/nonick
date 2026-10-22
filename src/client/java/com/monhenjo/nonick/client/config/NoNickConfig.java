package com.monhenjo.nonick.client.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

/**
 * Everything the player can change. Cloth Config reads this class directly,
 * so the field names double as translation keys under
 * {@code text.autoconfig.nonick.option.*}.
 */
@Config(name = "nonick")
@Config.Gui.Background("nonick:textures/gui/config_background.png")
public final class NoNickConfig implements ConfigData {
	@ConfigEntry.Category("general")
	@ConfigEntry.Gui.Tooltip
	public boolean enabled = true;

	@ConfigEntry.Category("display")
	@ConfigEntry.Gui.Tooltip
	public boolean replaceNametags = true;

	@ConfigEntry.Category("display")
	@ConfigEntry.Gui.Tooltip
	public boolean replaceTabList = true;

	@ConfigEntry.Category("display")
	@ConfigEntry.Gui.Tooltip
	public boolean replaceChat = true;

	@ConfigEntry.Category("display")
	@ConfigEntry.Gui.Tooltip
	public boolean replaceSystemMessages = true;

	@ConfigEntry.Category("display")
	@ConfigEntry.Gui.Tooltip
	public boolean keepDecorations = true;

	@ConfigEntry.Category("display")
	@ConfigEntry.Gui.Tooltip
	public boolean showNickname = false;

	@ConfigEntry.Category("detection")
	@ConfigEntry.Gui.Tooltip
	public boolean notifyOnJoin = true;

	@ConfigEntry.Category("detection")
	@ConfigEntry.Gui.Tooltip
	public boolean mojangLookup = true;

	@ConfigEntry.Category("detection")
	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.BoundedDiscrete(min = 1, max = 720)
	public int cacheHours = 24;

	@ConfigEntry.Category("advanced")
	@ConfigEntry.Gui.Tooltip
	public boolean debugLog = false;

	@Override
	public void validatePostLoad() {
		if (cacheHours < 1 || cacheHours > 720) {
			cacheHours = 24;
		}
	}
}
