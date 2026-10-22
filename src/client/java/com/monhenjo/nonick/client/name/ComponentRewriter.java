package com.monhenjo.nonick.client.name;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;

/** Rewrites a name component without throwing away the server's formatting. */
public final class ComponentRewriter {
	private ComponentRewriter() {
	}

	public static Component reveal(
		Component original,
		String realName,
		String alias,
		String nick,
		boolean keepDecorations,
		boolean showNickname
	) {
		MutableComponent result;

		if (keepDecorations && alias != null) {
			boolean present = TokenReplacer.containsToken(original.getString(), alias);
			result = rewriteTree(original, alias, realName);
			if (present && result.getString().equals(original.getString())) {
				// Rare: the name lives inside a translatable argument that the
				// tree walk cannot reach. Fall back to flat text.
				result = Component.literal(TokenReplacer.replaceToken(original.getString(), alias, realName))
					.withStyle(original.getStyle());
			}
		} else {
			result = Component.literal(realName).withStyle(original.getStyle());
		}

		if (showNickname && nick != null && !nick.isBlank() && !nick.equals(realName)) {
			result.append(Component.literal(" (" + nick + ")").withStyle(ChatFormatting.DARK_GRAY));
		}
		return result;
	}

	private static MutableComponent rewriteTree(Component source, String alias, String realName) {
		MutableComponent out;
		ComponentContents contents = source.getContents();
		if (contents instanceof PlainTextContents plain) {
			out = Component.literal(TokenReplacer.replaceToken(plain.text(), alias, realName));
		} else {
			out = MutableComponent.create(contents);
		}

		out.setStyle(fixStyle(source.getStyle(), alias, realName));
		for (Component sibling : source.getSiblings()) {
			out.append(rewriteTree(sibling, alias, realName));
		}
		return out;
	}

	private static Style fixStyle(Style style, String alias, String realName) {
		Style fixed = style;

		if (style.getClickEvent() instanceof ClickEvent.SuggestCommand suggest) {
			String command = TokenReplacer.replaceToken(suggest.command(), alias, realName);
			if (!command.equals(suggest.command())) {
				fixed = fixed.withClickEvent(new ClickEvent.SuggestCommand(command));
			}
		} else if (style.getClickEvent() instanceof ClickEvent.RunCommand run) {
			String command = TokenReplacer.replaceToken(run.command(), alias, realName);
			if (!command.equals(run.command())) {
				fixed = fixed.withClickEvent(new ClickEvent.RunCommand(command));
			}
		}

		if (alias.equals(style.getInsertion())) {
			fixed = fixed.withInsertion(realName);
		}
		return fixed;
	}
}
