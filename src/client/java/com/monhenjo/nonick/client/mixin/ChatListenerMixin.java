package com.monhenjo.nonick.client.mixin;

import com.monhenjo.nonick.client.NoNickClient;
import com.monhenjo.nonick.client.name.NameMapper;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * System chat also reaches the client through the chat listener. Rewriting
 * here catches join, leave, and death messages even when another mod builds
 * the packet before it reaches the packet listener.
 */
@Mixin(ChatListener.class)
public abstract class ChatListenerMixin {
	@ModifyVariable(method = "handleSystemMessage", at = @At("HEAD"), argsOnly = true, index = 1)
	private Component nonick$systemMessage(Component message) {
		NameMapper mapper = NoNickClient.mapper();
		return mapper == null ? message : mapper.formatSystemMessage(message);
	}

	@ModifyVariable(method = "handleDisguisedChatMessage", at = @At("HEAD"), argsOnly = true, index = 1)
	private Component nonick$disguisedMessage(Component message) {
		NameMapper mapper = NoNickClient.mapper();
		return mapper == null ? message : mapper.formatSystemMessage(message);
	}
}
