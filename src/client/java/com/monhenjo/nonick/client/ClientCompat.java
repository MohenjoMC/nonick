package com.monhenjo.nonick.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Small reflection layer for the few client classes that moved between 26.1,
 * 26.2, and 26.3. Everything else is compiled against the normal API.
 */
public final class ClientCompat {
	private static final InputConstants.Type KEYBOARD = findKeyboardType();

	private ClientCompat() {
	}

	public static InputConstants.Type keyboardType() {
		return KEYBOARD;
	}

	public static Screen currentScreen(Minecraft minecraft) {
		try {
			Field field = Minecraft.class.getDeclaredField("screen");
			field.setAccessible(true);
			return (Screen) field.get(minecraft);
		} catch (ReflectiveOperationException ignored) {
			// 26.2 and 26.3 expose the screen through the GUI object.
		}

		Object gui = minecraft.gui;
		if (gui == null) {
			return null;
		}
		try {
			Method method = gui.getClass().getMethod("screen");
			return (Screen) method.invoke(gui);
		} catch (ReflectiveOperationException ignored) {
			// 26.1 keeps the screen in a field.
		}
		try {
			Field field = gui.getClass().getDeclaredField("screen");
			field.setAccessible(true);
			return (Screen) field.get(gui);
		} catch (ReflectiveOperationException ignored) {
			return null;
		}
	}

	public static void setOverlayMessage(Minecraft minecraft, Component message) {
		Object gui = minecraft.gui;
		if (gui == null) {
			return;
		}

		try {
			Method method = gui.getClass().getMethod("setOverlayMessage", Component.class, boolean.class);
			method.invoke(gui, message, false);
			return;
		} catch (ReflectiveOperationException ignored) {
			// 26.3 moved the HUD to a field.
		}

		Object hud = hud(minecraft);
		if (hud != null) {
			try {
				Method method = hud.getClass().getMethod("setOverlayMessage", Component.class, boolean.class);
				method.invoke(hud, message, false);
			} catch (ReflectiveOperationException ignored) {
				// If the method is gone there is no safe fallback.
			}
		}
	}

	public static void addClientChatMessage(Minecraft minecraft, Component message) {
		Object chat = chat(minecraft);
		if (chat == null) {
			return;
		}

		for (String methodName : new String[]{"addClientSystemMessage", "addMessage"}) {
			try {
				Method method = chat.getClass().getMethod(methodName, Component.class);
				method.invoke(chat, message);
				return;
			} catch (ReflectiveOperationException ignored) {
				// Try the next name.
			}
		}
	}

	public static Object tabList(Minecraft minecraft) {
		Object gui = minecraft.gui;
		if (gui != null) {
			try {
				Method method = gui.getClass().getMethod("getTabList");
				return method.invoke(gui);
			} catch (ReflectiveOperationException ignored) {
				// 26.3 keeps the tab list on the HUD.
			}
		}

		Object hud = hud(minecraft);
		if (hud != null) {
			try {
				Method method = hud.getClass().getMethod("getTabList");
				return method.invoke(hud);
			} catch (ReflectiveOperationException ignored) {
				// No tab list found.
			}
		}
		return null;
	}

	public static Object hud(Minecraft minecraft) {
		Object gui = minecraft.gui;
		if (gui == null) {
			return null;
		}
		try {
			Field field = gui.getClass().getField("hud");
			return field.get(gui);
		} catch (ReflectiveOperationException ignored) {
			// 26.1 may have a getter instead.
		}
		try {
			Method method = gui.getClass().getMethod("getHud");
			return method.invoke(gui);
		} catch (ReflectiveOperationException ignored) {
			return null;
		}
	}

	public static Object chat(Minecraft minecraft) {
		Object gui = minecraft.gui;
		if (gui != null) {
			try {
				Method method = gui.getClass().getMethod("getChat");
				return method.invoke(gui);
			} catch (ReflectiveOperationException ignored) {
				// 26.3 keeps chat on the HUD.
			}
		}

		Object hud = hud(minecraft);
		if (hud != null) {
			try {
				Method method = hud.getClass().getMethod("getChat");
				return method.invoke(hud);
			} catch (ReflectiveOperationException ignored) {
				// No chat component found.
			}
		}
		return null;
	}

	private static InputConstants.Type findKeyboardType() {
		for (String name : new String[]{"KEYBOARD", "KEYSYM"}) {
			try {
				Field field = InputConstants.Type.class.getField(name);
				return (InputConstants.Type) field.get(null);
			} catch (ReflectiveOperationException ignored) {
				// Try the older name.
			}
		}
		throw new IllegalStateException("No keyboard input type found");
	}
}
