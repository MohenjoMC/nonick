package com.monhenjo.nonick.client.mixin;

import com.monhenjo.nonick.client.NoNickClient;
import com.monhenjo.nonick.client.name.NameMapper;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Chat text is built on the server, so the nickname arrives inside the chat
 * type's name component. Rewriting it before the chat listener decorates the
 * message keeps the message body untouched.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@ModifyVariable(method = "handlePlayerChat", at = @At("HEAD"), argsOnly = true, index = 1)
	private ClientboundPlayerChatPacket nonick$playerChat(ClientboundPlayerChatPacket packet) {
		NameMapper mapper = NoNickClient.mapper();
		if (mapper == null || packet == null) {
			return packet;
		}

		ChatType.Bound bound = mapper.formatPlayerChat(packet.sender(), packet.chatType());
		if (bound == packet.chatType()) {
			return packet;
		}
		return new ClientboundPlayerChatPacket(
			packet.globalIndex(),
			packet.sender(),
			packet.index(),
			packet.signature(),
			packet.body(),
			packet.unsignedContent(),
			packet.filterMask(),
			bound
		);
	}

	@ModifyVariable(method = "handleDisguisedChat", at = @At("HEAD"), argsOnly = true, index = 1)
	private ClientboundDisguisedChatPacket nonick$disguisedChat(ClientboundDisguisedChatPacket packet) {
		NameMapper mapper = NoNickClient.mapper();
		if (mapper == null || packet == null) {
			return packet;
		}

		ChatType.Bound bound = mapper.formatDisguisedChat(packet.chatType());
		Component message = mapper.formatSystemMessage(packet.message());
		if (bound == packet.chatType() && message == packet.message()) {
			return packet;
		}
		return new ClientboundDisguisedChatPacket(message, bound);
	}

	@ModifyVariable(method = "handleSystemChat", at = @At("HEAD"), argsOnly = true, index = 1)
	private ClientboundSystemChatPacket nonick$systemChat(ClientboundSystemChatPacket packet) {
		NameMapper mapper = NoNickClient.mapper();
		if (mapper == null || packet == null) {
			return packet;
		}

		Component content = mapper.formatSystemMessage(packet.content());
		if (content == packet.content()) {
			return packet;
		}
		return new ClientboundSystemChatPacket(content, packet.overlay());
	}
}
