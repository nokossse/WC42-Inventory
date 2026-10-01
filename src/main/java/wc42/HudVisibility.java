package wc42;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Wc42.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class HudVisibility {
	private static final long LONG_PRESS_MS = 500L;
	private static final long HOTBAR_IDLE_MS = 2000L;
	private static final long FADE_MS = 500L;

	private static final KeyMapping HUD_KEY = new KeyMapping(
		"key.wc42.hud",
		com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
		GLFW.GLFW_KEY_GRAVE_ACCENT,
		"key.categories.misc"
	);

	private static boolean hotbarEnabled = true;
	private static boolean statusEnabled = true;
	private static boolean wasDown;
	private static boolean longFired;
	private static long pressedAt;
	private static long lastInteraction = System.currentTimeMillis();
	private static float hotbarAlpha = 1.0F;
	private static long hotbarStamp;
	private static float statusAlpha = 1.0F;
	private static long statusStamp;
	private static int lastSelected = -1;
	private static boolean lastUsing;
	private static boolean lastSwinging;
	private static final ItemStack[] seen = new ItemStack[10];

	private HudVisibility() {
	}

	@SubscribeEvent
	public static void registerKeys(RegisterKeyMappingsEvent event) {
		event.register(HUD_KEY);
	}

	public static void clientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		if (client.screen != null) {
			wasDown = false;
			longFired = true;
		} else {
			boolean down = HUD_KEY.isDown();
			if (down && !wasDown) {
				pressedAt = now;
				longFired = false;
			}
			if (down && !longFired && now - pressedAt >= LONG_PRESS_MS) {
				longFired = true;
				statusEnabled = !statusEnabled;
			}
			if (!down && wasDown && !longFired) {
				hotbarEnabled = !hotbarEnabled;
				lastInteraction = now;
			}
			wasDown = down;
		}

		Player player = client.player;
		if (player == null) {
			return;
		}
		if (client.screen instanceof Wc42InventoryScreen) {
			lastInteraction = now;
		} else if (hotbarTouched(player)) {
			lastInteraction = now;
		}
	}

	public static float hotbarFade(boolean inventoryOpen) {
		long now = System.currentTimeMillis();
		if (inventoryOpen) {
			hotbarAlpha = 1.0F;
			hotbarStamp = now;
			return 1.0F;
		}

		float target = 1.0F;
		if (!hotbarEnabled && now - lastInteraction >= HOTBAR_IDLE_MS) {
			target = 0.0F;
		}
		hotbarAlpha = approach(hotbarAlpha, target, now, hotbarStamp);
		hotbarStamp = now;
		return hotbarAlpha;
	}

	public static float statusFade() {
		long now = System.currentTimeMillis();
		float target = statusEnabled ? 1.0F : 0.0F;
		statusAlpha = approach(statusAlpha, target, now, statusStamp);
		statusStamp = now;
		return statusAlpha;
	}

	private static float approach(float alpha, float target, long now, long stamp) {
		long elapsed = stamp == 0L ? 0L : now - stamp;
		float step = elapsed / (float) FADE_MS;
		if (alpha < target) {
			alpha = Math.min(target, alpha + step);
		} else if (alpha > target) {
			alpha = Math.max(target, alpha - step);
		}
		if (alpha < 0.001F) {
			return 0.0F;
		}
		if (alpha > 0.999F) {
			return 1.0F;
		}
		return alpha;
	}

	private static boolean hotbarTouched(Player player) {
		boolean touched = false;
		int selected = player.getInventory().selected;
		if (selected != lastSelected) {
			touched = lastSelected >= 0;
			lastSelected = selected;
		}
		boolean using = player.isUsingItem();
		if (using != lastUsing) {
			touched = true;
			lastUsing = using;
		}
		if (player.swinging && !lastSwinging) {
			touched = true;
		}
		lastSwinging = player.swinging;

		for (int slot = 0; slot < 9; slot++) {
			touched |= watch(slot, player.getInventory().getItem(slot));
		}
		touched |= watch(9, player.getOffhandItem());
		return touched;
	}

	private static boolean watch(int index, ItemStack stack) {
		ItemStack previous = seen[index];
		if (previous != null && ItemStack.matches(previous, stack)) {
			return false;
		}
		seen[index] = stack.copy();
		return previous != null;
	}
}
