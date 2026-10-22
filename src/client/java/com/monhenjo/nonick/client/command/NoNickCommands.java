package com.monhenjo.nonick.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.monhenjo.nonick.client.NoNickClient;
import com.monhenjo.nonick.client.config.NoNickConfig;
import com.monhenjo.nonick.client.config.NoNickConfigManager;
import com.monhenjo.nonick.client.name.KnownPlayer;
import com.monhenjo.nonick.client.name.NameMapper;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

public final class NoNickCommands {
	private NoNickCommands() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
		dispatcher.register(ClientCommands.literal("nonick")
			.executes(ctx -> status(ctx.getSource()))
			.then(ClientCommands.literal("toggle").executes(ctx -> {
				NoNickClient.toggle(ctx.getSource().getClient());
				return 1;
			}))
			.then(ClientCommands.literal("status").executes(ctx -> status(ctx.getSource())))
			.then(ClientCommands.literal("list").executes(ctx -> list(ctx.getSource())))
			.then(ClientCommands.literal("config").executes(ctx -> {
				NoNickClient.openConfigScreen(ctx.getSource().getClient());
				return 1;
			}))
			.then(ClientCommands.literal("copy").executes(ctx -> {
				NoNickClient.copyTarget(ctx.getSource().getClient());
				return 1;
			}))
			.then(ClientCommands.literal("clear").executes(ctx -> {
				NameMapper mapper = NoNickClient.mapper();
				if (mapper != null) {
					mapper.clearCache();
				}
				ctx.getSource().sendFeedback(Component.translatable("text.nonick.clear.done"));
				return 1;
			}))
			.then(ClientCommands.literal("who")
				.then(ClientCommands.argument("player", StringArgumentType.word())
					.suggests((ctx, builder) -> {
						NameMapper mapper = NoNickClient.mapper();
						if (mapper != null) {
							for (KnownPlayer known : mapper.allPlayers()) {
								suggest(builder, known.realName());
								suggest(builder, known.profileName());
								suggest(builder, known.nick());
							}
						}
						return builder.buildFuture();
					})
					.executes(ctx -> who(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
			.then(ClientCommands.literal("lookup")
				.then(ClientCommands.argument("player", StringArgumentType.word())
					.executes(ctx -> lookup(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
		);
	}

	private static int status(FabricClientCommandSource source) {
		NoNickConfig config = NoNickConfigManager.get();
		NameMapper mapper = NoNickClient.mapper();
		int nicked = mapper == null ? 0 : mapper.nickedPlayers().size();
		int cached = mapper == null ? 0 : mapper.cachedCount();

		source.sendFeedback(Component.translatable(
			"text.nonick.status",
			Component.translatable(config.enabled ? "text.nonick.state.on" : "text.nonick.state.off")
				.withStyle(config.enabled ? ChatFormatting.GREEN : ChatFormatting.RED),
			nicked,
			cached
		));
		return 1;
	}

	private static int list(FabricClientCommandSource source) {
		NameMapper mapper = NoNickClient.mapper();
		if (mapper == null) {
			source.sendError(Component.translatable("text.nonick.not_ready"));
			return 0;
		}

		List<KnownPlayer> nicked = mapper.nickedPlayers();
		if (nicked.isEmpty()) {
			source.sendFeedback(Component.translatable("text.nonick.list.empty"));
			return 1;
		}

		source.sendFeedback(Component.translatable("text.nonick.list.header", nicked.size())
			.withStyle(ChatFormatting.GRAY));
		int shown = 0;
		for (KnownPlayer known : nicked) {
			if (shown++ >= 20) {
				source.sendFeedback(Component.translatable("text.nonick.list.more", nicked.size() - 20)
					.withStyle(ChatFormatting.GRAY));
				break;
			}
			String nick = known.nick() == null ? known.profileName() : known.nick();
			source.sendFeedback(Component.literal("  ")
				.append(Component.literal(known.realName()).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" is shown as ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(nick).withStyle(ChatFormatting.YELLOW)));
		}
		return 1;
	}

	private static int who(FabricClientCommandSource source, String query) {
		NameMapper mapper = NoNickClient.mapper();
		KnownPlayer known = mapper == null ? null : mapper.find(query);
		if (known == null) {
			source.sendError(Component.translatable("text.nonick.not_found", query));
			return 0;
		}

		String nick = known.nick() == null ? known.profileName() : known.nick();
		source.sendFeedback(Component.translatable("text.nonick.who.line1", known.realName(), nick)
			.withStyle(ChatFormatting.WHITE));
		source.sendFeedback(Component.translatable("text.nonick.who.line2", known.id().toString())
			.withStyle(ChatFormatting.DARK_GRAY));
		if (known.skinName() != null && !known.skinName().equals(known.realName())) {
			source.sendFeedback(Component.translatable("text.nonick.who.skin", known.skinName())
				.withStyle(ChatFormatting.DARK_GRAY));
		}
		return 1;
	}

	private static int lookup(FabricClientCommandSource source, String query) {
		NameMapper mapper = NoNickClient.mapper();
		KnownPlayer known = mapper == null ? null : mapper.find(query);
		if (known == null) {
			source.sendError(Component.translatable("text.nonick.not_found", query));
			return 0;
		}

		mapper.forceLookup(known);
		String visible = known.nick() == null ? known.profileName() : known.nick();
		source.sendFeedback(Component.translatable("text.nonick.lookup.pending", visible));
		return 1;
	}

	private static void suggest(com.mojang.brigadier.suggestion.SuggestionsBuilder builder, String value) {
		if (value != null && !value.isBlank() && value.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
			builder.suggest(value);
		}
	}
}
