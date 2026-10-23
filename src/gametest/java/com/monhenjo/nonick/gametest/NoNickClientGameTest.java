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
			context.waitTicks(20);

			// A singleplayer world with one listed player hides the tab list
			// even while Tab is held unless a list objective exists. Check the
			// objective before trusting the screenshot.
			boolean hasListObjective = context.computeOnClient(minecraft ->
				minecraft.level.getScoreboard().getDisplayObjective(net.minecraft.world.scores.DisplaySlot.LIST) != null);
			if (!hasListObjective) {
				throw new AssertionError("test objective did not reach the client scoreboard");
			}

			// Set the key state directly instead of sending a keyboard event.
			// An SSH session on a headless-ish host can leave the window
			// unfocused, and a dropped key event makes this screenshot show an
			// empty world with no tab list, which proves nothing.
			context.runOnClient(minecraft -> minecraft.options.keyPlayerList.setDown(true));
			context.waitTicks(5);

			boolean keyDown = context.computeOnClient(minecraft -> minecraft.options.keyPlayerList.isDown());
			if (!keyDown) {
				throw new AssertionError("tab list key did not stay down");
			}

			String realName = context.computeOnClient(minecraft -> minecraft.player.getGameProfile().name());
			String tabName = context.computeOnClient(minecraft -> {
				var info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
				if (info == null) {
					throw new AssertionError("local player info is missing");
				}
				return minecraft.gui.hud.getTabList().getNameForDisplay(info).getString();
			});
			if (!realName.equals(tabName)) {
				throw new AssertionError("tab list still shows the nickname: " + tabName);
			}
			context.takeScreenshot("nonick-tab");
			context.runOnClient(minecraft -> minecraft.options.keyPlayerList.setDown(false));
		}
	}
}
