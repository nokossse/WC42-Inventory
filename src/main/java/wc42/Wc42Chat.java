package wc42;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Wc42.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class Wc42Chat {
	static final int X = 8;
	static final int Y = 8;
	static final int WIDTH = 180;
	static final int HEIGHT = 83;
	static final int INPUT_GAP = 2;
	static final int INPUT_HEIGHT = 12;
	static final int INPUT_Y = Y + HEIGHT + INPUT_GAP;
	private static final int LINE_HEIGHT = 9;
	private static final int LINE_COUNT = 9;
	private static final int TEXT_PAD = 2;
	private static final int BORDER_ALPHA = 18;
	private static final int FILL_ALPHA = 40;
	private static final int INPUT_ALPHA = 88;
	private static final int FADE_TICKS = 200;

	private Wc42Chat() {
	}

	@SubscribeEvent
	public static void replaceVanillaChat(RenderGuiOverlayEvent.Pre event) {
		if (!event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) {
			return;
		}
		event.setCanceled(true);
		renderMessages(event.getGuiGraphics());
	}

	@SubscribeEvent
	public static void replaceChatScreen(ScreenEvent.Opening event) {
		if (event.getNewScreen() instanceof ChatScreen chat && chat.getClass() == ChatScreen.class) {
			event.setNewScreen(new Wc42ChatScreen(chat.initial));
		}
	}

	static void placeInput(EditBox input) {
		input.setX(X + TEXT_PAD);
		input.setY(INPUT_Y);
		input.setWidth(WIDTH - TEXT_PAD * 2);
	}

	static void drawInput(GuiGraphics graphics) {
		graphics.fill(X, INPUT_Y, X + WIDTH, INPUT_Y + INPUT_HEIGHT, argb(0, 0, 0, INPUT_ALPHA));
	}

	static void renderCommandSuggestions(net.minecraft.client.gui.components.CommandSuggestions suggestions, GuiGraphics graphics, int mouseX, int mouseY) {
		placeSuggestions(suggestions);
		if (suggestions.renderSuggestions(graphics, mouseX, mouseY)) {
			return;
		}
		int shift = INPUT_Y + INPUT_HEIGHT - 72;
		graphics.pose().pushPose();
		graphics.pose().translate(0.0F, shift, 0.0F);
		suggestions.renderUsage(graphics);
		graphics.pose().popPose();
	}

	static void anchorSuggestionsDown(net.minecraft.client.gui.components.CommandSuggestions suggestions) {
		try {
			anchorField().setBoolean(suggestions, false);
		} catch (IllegalAccessException ignored) {
		}
	}

	static void placeSuggestions(net.minecraft.client.gui.components.CommandSuggestions suggestions) {
		try {
			Object list = suggestionList().get(suggestions);
			if (list == null) {
				return;
			}
			net.minecraft.client.renderer.Rect2i rect = (net.minecraft.client.renderer.Rect2i) suggestionRect(list).get(list);
			rect.setY(INPUT_Y + INPUT_HEIGHT);
		} catch (IllegalAccessException ignored) {
		}
	}

	private static java.lang.reflect.Field suggestionList() {
		if (SUGGESTIONS == null) {
			SUGGESTIONS = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(
				net.minecraft.client.gui.components.CommandSuggestions.class,
				"f_93866_"
			);
		}
		return SUGGESTIONS;
	}

	private static java.lang.reflect.Field anchorField() {
		if (ANCHOR == null) {
			ANCHOR = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(
				net.minecraft.client.gui.components.CommandSuggestions.class,
				"f_93859_"
			);
		}
		return ANCHOR;
	}

	private static java.lang.reflect.Field suggestionRect(Object list) {
		if (SUGGESTION_RECT == null) {
			SUGGESTION_RECT = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(list.getClass(), "f_93947_");
		}
		return SUGGESTION_RECT;
	}

	private static java.lang.reflect.Field SUGGESTIONS;
	private static java.lang.reflect.Field SUGGESTION_RECT;
	private static java.lang.reflect.Field ANCHOR;

	private static void renderMessages(GuiGraphics graphics) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gui == null) {
			return;
		}
		boolean open = client.screen instanceof ChatScreen;
		if (client.options.hideGui && !open) {
			return;
		}
		ChatComponent chat = client.gui.getChat();
		Font font = client.font;
		int width = WIDTH - 2 - TEXT_PAD * 2;
		int scroll = Math.max(0, chat.chatScrollbarPos);
		int skipped = 0;
		List<VisibleLine> visible = new ArrayList<>();
		int tick = client.gui.getGuiTicks();
		for (GuiMessage message : chat.allMessages) {
			float opacity = open ? 1.0F : opacity(tick - message.addedTime());
			if (opacity <= 0.0F) {
				continue;
			}
			for (FormattedCharSequence line : font.split(message.content(), width)) {
				if (skipped < scroll) {
					skipped++;
					continue;
				}
				visible.add(new VisibleLine(line, opacity));
				if (visible.size() == LINE_COUNT) {
					break;
				}
			}
			if (visible.size() == LINE_COUNT) {
				break;
			}
		}
		if (visible.isEmpty() && !open) {
			return;
		}
		float boxOpacity = open ? 1.0F : maxOpacity(visible);
		drawBox(graphics, boxOpacity);
		int textX = X + 1 + TEXT_PAD;
		int bottom = Y + HEIGHT - 1;
		graphics.enableScissor(X + 1, Y + 1, X + WIDTH - 1, Y + HEIGHT - 1);
		for (int index = 0; index < visible.size(); index++) {
			VisibleLine line = visible.get(index);
			int textY = bottom - (index + 1) * LINE_HEIGHT;
			int alpha = Math.round(255.0F * line.opacity);
			graphics.drawString(font, line.text, textX, textY, (alpha << 24) | 0xFFFFFF, true);
		}
		graphics.disableScissor();
	}

	private static void drawBox(GuiGraphics graphics, float opacity) {
		int border = argb(0, 0, 0, Math.round(BORDER_ALPHA * opacity));
		int fill = argb(0, 0, 0, Math.round(FILL_ALPHA * opacity));
		int x = X;
		int y = Y;
		graphics.fill(x, y, x + WIDTH, y + 1, border);
		graphics.fill(x, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, border);
		graphics.fill(x, y + 1, x + 1, y + HEIGHT - 1, border);
		graphics.fill(x + WIDTH - 1, y + 1, x + WIDTH, y + HEIGHT - 1, border);
		graphics.fill(x + 1, y + 1, x + WIDTH - 1, y + HEIGHT - 1, fill);
	}

	private static float maxOpacity(List<VisibleLine> lines) {
		float max = 0.0F;
		for (VisibleLine line : lines) {
			max = Math.max(max, line.opacity);
		}
		return max;
	}

	private static float opacity(int age) {
		if (age >= FADE_TICKS) {
			return 0.0F;
		}
		float factor = 1.0F - age / (float) FADE_TICKS;
		factor = Mth.clamp(factor * 10.0F, 0.0F, 1.0F);
		return factor * factor;
	}

	private static int argb(int red, int green, int blue, int alpha) {
		return (alpha << 24) | (red << 16) | (green << 8) | blue;
	}

	private record VisibleLine(FormattedCharSequence text, float opacity) {
	}
}
