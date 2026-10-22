package com.monhenjo.nonick.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.monhenjo.nonick.NoNick;
import com.monhenjo.nonick.client.command.NoNickCommands;
import com.monhenjo.nonick.client.config.NoNickConfig;
import com.monhenjo.nonick.client.config.NoNickConfigManager;
import com.monhenjo.nonick.client.name.KnownPlayer;
import com.monhenjo.nonick.client.name.NameMapper;
import me.shedaniel.autoconfig.AutoConfigClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class NoNickClient implements ClientModInitializer {

	public static final KeyMapping.Category CATEGORY =
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath(NoNick.MOD_ID, "main"));

	public static final KeyMapping TOGGLE_KEY = new KeyMapping(
		"key.nonick.toggle",
		InputConstants.Type.KEYBOARD,
		InputConstants.KEY_F6,
		CATEGORY
	);

	public static final KeyMapping CONFIG_KEY = new KeyMapping(
		"key.nonick.config",
		InputConstants.Type.KEYBOARD,
		InputConstants.KEY_F7,
		CATEGORY
	);

	public static final KeyMapping COPY_KEY = new KeyMapping(
		"key.nonick.copy",
		InputConstants.Type.KEYBOARD,
		InputConstants.UNKNOWN.getValue(),
		CATEGORY
	);

	private static NameMapper mapper;

	@Override
	public void onInitializeClient() {
		NoNickConfigManager.init();
		mapper = new NameMapper(FabricLoader.getInstance().getConfigDir());

		KeyMappingHelper.registerKeyMapping(TOGGLE_KEY);
		KeyMappingHelper.registerKeyMapping(CONFIG_KEY);
		KeyMappingHelper.registerKeyMapping(COPY_KEY);

		ClientTickEvents.END_CLIENT_TICK.register(NoNickClient::onEndTick);
		ClientCommandRegistrationCallback.EVENT.register(NoNickCommands::register);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			if (mapper != null) {
				mapper.close();
			}
		});

		NoNick.LOGGER.info("NoNick loaded. Toggle key: F6, settings key: F7.");
	}

	public static NameMapper mapper() {
		return mapper;
	}

	private static void onEndTick(Minecraft minecraft) {
		while (TOGGLE_KEY.consumeClick()) {
			toggle(minecraft);
		}
		while (CONFIG_KEY.consumeClick()) {
			openConfigScreen(minecraft);
		}
		while (COPY_KEY.consumeClick()) {
			copyTarget(minecraft);
		}
		if (mapper != null) {
			mapper.tick(minecraft);
		}
	}

	public static void toggle(Minecraft minecraft) {
		NoNickConfig config = NoNickConfigManager.get();
		config.enabled = !config.enabled;
		NoNickConfigManager.save();
		overlay(minecraft, Component.translatable(config.enabled ? "text.nonick.toggle.on" : "text.nonick.toggle.off"));
	}

	public static void openConfigScreen(Minecraft minecraft) {
		try {
			Screen parent = minecraft.gui.screen();
			Screen screen = AutoConfigClient.getConfigScreen(NoNickConfig.class, parent).get();
			minecraft.setScreenAndShow(screen);
		} catch (Exception error) {
			NoNick.LOGGER.error("Could not open the NoNick settings screen", error);
			overlay(minecraft, Component.translatable("text.nonick.config.failed"));
		}
	}

	public static void copyTarget(Minecraft minecraft) {
		Entity target = minecraft.crosshairPickEntity;
		if (!(target instanceof Player player)) {
			overlay(minecraft, Component.translatable("text.nonick.copy.none"));
			return;
		}

		KnownPlayer known = mapper == null ? null : mapper.get(player.getUUID());
		String name = known != null && known.nicked() ? known.realName() : player.getGameProfile().name();
		minecraft.keyboardHandler.setClipboard(name);
		overlay(minecraft, Component.translatable("text.nonick.copy.success", name));
	}

	public static void overlay(Minecraft minecraft, Component message) {
		minecraft.gui.hud.setOverlayMessage(message, false);
	}
}
