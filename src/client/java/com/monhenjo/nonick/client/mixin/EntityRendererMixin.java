package com.monhenjo.nonick.client.mixin;

import com.monhenjo.nonick.client.NoNickClient;
import com.monhenjo.nonick.client.config.NoNickConfigManager;
import com.monhenjo.nonick.client.name.NameMapper;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
	private void nonick$replaceNametag(Entity entity, CallbackInfoReturnable<Component> cir) {
		if (!NoNickConfigManager.get().replaceNametags || !(entity instanceof Player player)) {
			return;
		}

		NameMapper mapper = NoNickClient.mapper();
		Component original = cir.getReturnValue();
		if (mapper == null || original == null) {
			return;
		}

		Component replaced = mapper.format(player.getUUID(), original);
		if (replaced != original) {
			cir.setReturnValue(replaced);
		}
	}
}
