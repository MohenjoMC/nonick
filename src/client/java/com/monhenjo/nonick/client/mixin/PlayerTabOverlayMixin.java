package com.monhenjo.nonick.client.mixin;

import com.monhenjo.nonick.client.NoNickClient;
import com.monhenjo.nonick.client.config.NoNickConfigManager;
import com.monhenjo.nonick.client.name.NameMapper;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
	private void nonick$replaceTabName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		if (!NoNickConfigManager.get().replaceTabList || info == null) {
			return;
		}

		NameMapper mapper = NoNickClient.mapper();
		Component original = cir.getReturnValue();
		if (mapper == null || original == null) {
			return;
		}

		Component replaced = mapper.format(info.getProfile().id(), original);
		if (replaced != original) {
			cir.setReturnValue(replaced);
		}
	}
}
