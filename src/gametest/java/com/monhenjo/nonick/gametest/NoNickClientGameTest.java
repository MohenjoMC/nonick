package com.monhenjo.nonick.gametest;

import com.monhenjo.nonick.client.ClientCompat;
import com.monhenjo.nonick.client.NoNickClient;
import com.monhenjo.nonick.client.config.NoNickConfigManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * A small end-to-end check. It joins a singleplayer world so the client
 * packet listener and entity renderers load, opens the settings screen, runs
 * the status command, then feeds one fake nickname through the same table the
 * player info packets use. The chat and tab-list screenshots show whether the
 * rewrite actually reached the screen.
 */
@SuppressWarnings("UnstableApiUsage")
public final class NoNickClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.runOnClient(minecraft -> NoNickConfigManager.get().debugLog = true);
			context.waitTicks(25);

			context.runOnClient(NoNickClient::openConfigScreen);
			context.waitTicks(20);

			boolean screenOpen = context.computeOnClient(minecraft -> ClientCompat.currentScreen(minecraft) != null);
			if (!screenOpen) {
				throw new AssertionError("NoNick settings screen did not open");
			}
			context.takeScreenshot("nonick-settings");
			context.runOnClient(minecraft -> minecraft.setScreenAndShow(null));

			context.runOnClient(minecraft -> minecraft.player.connection.sendCommand("nonick status"));
			context.waitTicks(10);
			context.takeScreenshot("nonick-status");

			context.runOnClient(minecraft -> {
				var info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
				if (info == null) {
					throw new AssertionError("local player info is missing");
				}
				info.setTabListDisplayName(net.minecraft.network.chat.Component.literal("xX_Nick_Xx"));
			});
			context.waitTicks(25);

			singleplayer.getServer().runCommand("say xX_Nick_Xx joined the game");
			context.waitTicks(10);
			context.takeScreenshot("nonick-chat");

			singleplayer.getServer().runCommand("scoreboard objectives add nonick_test dummy");
			singleplayer.getServer().runCommand("scoreboard objectives setdisplay list nonick_test");
			context.getInput().holdKey(options -> options.keyPlayerList);
			context.waitTicks(5);
			context.takeScreenshot("nonick-tab");
			context.getInput().releaseKey(options -> options.keyPlayerList);
		}
	}
}
