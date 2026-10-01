package wc42;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Wc42.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class StatusHud {
	private static final ResourceLocation HEALTH = ResourceLocation.fromNamespaceAndPath(Wc42.MOD_ID, "textures/gui/health.png");
	private static final ResourceLocation FOOD = ResourceLocation.fromNamespaceAndPath(Wc42.MOD_ID, "textures/gui/food.png");
	private static final int ICON_GAP = 1;
	private static final int VEHICLE_ICON_GAP = 4;
	private static final ResourceLocation SLOTS = ResourceLocation.fromNamespaceAndPath(Wc42.MOD_ID, "textures/gui/slots.png");
	private static final ResourceLocation OXYGEN = ResourceLocation.fromNamespaceAndPath(Wc42.MOD_ID, "textures/gui/oxygen.png");
	private static final ResourceLocation LEFT_ANCHOR = ResourceLocation.fromNamespaceAndPath(Wc42.MOD_ID, "textures/gui/left_anchor.png");
	private static final int ANCHOR_WIDTH = 160;
	private static final int ANCHOR_HEIGHT = 20;
	private static final int ANCHOR_TEXT_PAD = 4;
	private static final int DURABILITY_SIZE = 3;
	private static final int DURABILITY_PAD_X = 7;
	private static final int DURABILITY_PAD_Y = 7;
	private static final int DURABILITY_TEXT_GAP = 4;
	private static final int HUD_ALPHA = 204;
	private static final int MARGIN = 8;
	private static final int SLOT_COUNT = 9;
	private static final int SLOT_SIZE = 20;
	private static final int SLOT_GAP = 1;
	private static final int SLOT_SELECTED_U = 21;
	private static final int SLOT_NORMAL_U = 0;
	private static final int SLOT_SHEET_WIDTH = 41;
	private static final int SLOT_SHEET_HEIGHT = 20;
	private static final int OFFHAND_GAP = 4;
	private static final int OXYGEN_WIDTH = 188;
	private static final int OXYGEN_HEIGHT = 2;
	private static final int OXYGEN_WHITE_V = 0;
	private static final int OXYGEN_BLACK_V = 3;
	private static final int OXYGEN_SHEET_HEIGHT = 5;
	private static final int OXYGEN_GAP = 1;
	private static final int STAMINA_PAD = 4;
	private static final long OXYGEN_FADE_MS = 500L;

	private static float oxygenAlpha = 0.0F;
	private static long oxygenLastMs = 0L;

	private StatusHud() {
	}

	@SubscribeEvent
	public static void registerOverlays(RegisterGuiOverlaysEvent event) {
		event.registerAboveAll("status_icons", StatusHud::render);
		MinecraftForge.EVENT_BUS.addListener(StatusHud::hideVanillaBars);
	}

	private static void hideVanillaBars(RenderGuiOverlayEvent.Pre event) {
		ResourceLocation id = event.getOverlay().id();
		if (id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
				|| id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
				|| id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())
				|| id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
				|| id.equals(VanillaGuiOverlay.HOTBAR.id())
				|| id.equals(VanillaGuiOverlay.AIR_LEVEL.id())
				|| id.equals(VanillaGuiOverlay.ITEM_NAME.id())
				|| id.equals(VanillaGuiOverlay.POTION_ICONS.id())) {
			event.setCanceled(true);
		}
	}

	private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
		Minecraft client = Minecraft.getInstance();
		if (client.options.hideGui) {
			return;
		}

		Player player = client.player;
		if (player == null || player.isSpectator()) {
			return;
		}

		int hotbarY = hotbarY(screenHeight);
		int rowWidth = SLOT_COUNT * SLOT_SIZE + (SLOT_COUNT - 1) * SLOT_GAP;
		int startX = hotbarX(screenWidth);
		boolean sbwVehicle = inSbwVehicle(player);
		boolean inventoryOpen = client.screen instanceof Wc42InventoryScreen;
		float hotbarFade = HudVisibility.hotbarFade(inventoryOpen);
		if (!inventoryOpen && hotbarFade > 0.001F) {
			drawHudHotbarFrames(graphics, player, startX, hotbarY, hotbarFade);
			drawHudHotbarItems(graphics, player, partialTick, startX, hotbarY, hotbarFade);
		}
		drawOxygen(graphics, player, startX, hotbarY + SLOT_SIZE + OXYGEN_GAP);
		float statusFade = HudVisibility.statusFade();
		if (statusFade <= 0.001F) {
			prepareSprite(graphics, 1.0F, 1.0F, 1.0F, 1.0F);
			return;
		}
		if (!sbwVehicle) {
			drawHeldItem(graphics, player, hotbarY, anchorWidth(player, startX), statusFade);
		}
		if (player.getAbilities().instabuild) {
			prepareSprite(graphics, 1.0F, 1.0F, 1.0F, 1.0F);
			return;
		}

		long now = System.currentTimeMillis();
		float maxHealth = player.getMaxHealth();
		float healthRatio = maxHealth <= 0f ? 0f : player.getHealth() / maxHealth;
		float foodRatio = player.getFoodData().getFoodLevel() / (float) StatusIcons.FOOD_MAX;
		int healthX = screenWidth - MARGIN - StatusIcons.HEALTH_SPRITE;
		int foodX = healthX - ICON_GAP - StatusIcons.FOOD_SPRITE_WIDTH;
		if (sbwVehicle) {
			healthX = startX - VEHICLE_ICON_GAP - StatusIcons.HEALTH_SPRITE;
			foodX = startX + rowWidth + VEHICLE_ICON_GAP;
		}
		blitIcon(graphics, FOOD, foodX, hotbarY, StatusIcons.column(foodRatio, now), StatusIcons.FOOD_SPRITE_WIDTH, StatusIcons.FOOD_SPRITE_HEIGHT, StatusIcons.FOOD_STRIDE, StatusIcons.FOOD_SHEET_WIDTH, StatusIcons.FOOD_SHEET_HEIGHT, statusFade);
		blitIcon(graphics, HEALTH, healthX, hotbarY, StatusIcons.column(healthRatio, now), StatusIcons.HEALTH_SPRITE, StatusIcons.HEALTH_SPRITE, StatusIcons.HEALTH_STRIDE, StatusIcons.HEALTH_SHEET_WIDTH, StatusIcons.HEALTH_SHEET_HEIGHT, statusFade);
		prepareSprite(graphics, 1.0F, 1.0F, 1.0F, 1.0F);
	}

	private static boolean inSbwVehicle(Player player) {
		if (!ModList.get().isLoaded("superbwarfare")) {
			return false;
		}

		Entity entity = player.getVehicle();
		while (entity != null) {
			if (entity.getClass().getName().startsWith("com.atsuishio.superbwarfare.entity.vehicle.")) {
				return true;
			}
			entity = entity.getVehicle();
		}
		return false;
	}

	private static void drawOxygen(GuiGraphics graphics, Player player, int x, int y) {
		long now = System.currentTimeMillis();
		int maxAir = player.getMaxAirSupply();
		int air = player.getAirSupply();
		boolean full = maxAir <= 0 || air >= maxAir;
		boolean show = player.isEyeInFluid(FluidTags.WATER) || !full;

		float step = oxygenLastMs == 0L ? 0.0F : Mth.clamp((now - oxygenLastMs) / (float) OXYGEN_FADE_MS, 0.0F, 1.0F);
		oxygenLastMs = now;
		float target = show ? 1.0F : 0.0F;
		if (oxygenAlpha < target) {
			oxygenAlpha = Math.min(target, oxygenAlpha + step);
		} else if (oxygenAlpha > target) {
			oxygenAlpha = Math.max(target, oxygenAlpha - step);
		}

		if (oxygenAlpha <= 0.001F) {
			oxygenAlpha = 0.0F;
			return;
		}

		float ratio = maxAir <= 0 ? 0.0F : Mth.clamp(air / (float) maxAir, 0.0F, 1.0F);
		int whiteWidth = Math.round(OXYGEN_WIDTH * ratio);

		prepareSprite(graphics, 1.0F, 1.0F, 1.0F, oxygenAlpha);
		blitOxygen(graphics, x, y, OXYGEN_WIDTH, OXYGEN_BLACK_V);
		if (whiteWidth > 0) {
			blitOxygen(graphics, x, y, whiteWidth, OXYGEN_WHITE_V);
		}
		prepareSprite(graphics, 1.0F, 1.0F, 1.0F, 1.0F);
	}

	private static void prepareSprite(GuiGraphics graphics, float red, float green, float blue, float alpha) {
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		graphics.setColor(red, green, blue, alpha);
	}

	private static void blitOxygen(GuiGraphics graphics, int x, int y, int width, int v) {
		graphics.blit(OXYGEN, x, y, width, OXYGEN_HEIGHT, 0, v, width, OXYGEN_HEIGHT, OXYGEN_WIDTH, OXYGEN_SHEET_HEIGHT);
	}

	private static int anchorWidth(Player player, int hotbarLeft) {
		int limit = hotbarLeft;
		if (!player.getOffhandItem().isEmpty() && player.getMainArm() == HumanoidArm.RIGHT) {
			limit -= SLOT_SIZE + OFFHAND_GAP;
		}
		int available = limit - MARGIN - MARGIN;
		return Mth.clamp(available, ANCHOR_HEIGHT, ANCHOR_WIDTH);
	}

	private static void drawStamina(GuiGraphics graphics, Player player, int boxY, int boxWidth, float fade) {
		if (!ModList.get().isLoaded("simplystamina")) {
			return;
		}

		int width = boxWidth - STAMINA_PAD * 2;
		if (width <= 0) {
			return;
		}
		int x = MARGIN + STAMINA_PAD;
		int y = boxY + ANCHOR_HEIGHT - STAMINA_PAD - OXYGEN_HEIGHT;
		int whiteWidth = Math.round(width * StaminaAccess.ratio(player));

		prepareSprite(graphics, 1.0F, 1.0F, 1.0F, fade);
		blitOxygen(graphics, x, y, width, OXYGEN_BLACK_V);
		if (whiteWidth > 0) {
			blitOxygen(graphics, x, y, whiteWidth, OXYGEN_WHITE_V);
		}
	}

	private static void drawHeldItem(GuiGraphics graphics, Player player, int y, int boxWidth, float fade) {
		prepareSprite(graphics, 1.0F, 1.0F, 1.0F, fade);
		blitAnchor(graphics, MARGIN, y, boxWidth);
		drawStamina(graphics, player, y, boxWidth, fade);

		ItemStack stack = player.getMainHandItem();
		boolean empty = stack.isEmpty();

		int squareX = MARGIN + DURABILITY_PAD_X;
		int squareY = y + DURABILITY_PAD_Y;
		int rgb = !empty && stack.isDamageableItem() ? stack.getBarColor() & 0xFFFFFF : 0xFFFFFF;
		if (!empty && ModList.get().isLoaded("tacz")) {
			int ammoColor = GunAmmo.color(stack);
			if (ammoColor >= 0) {
				rgb = ammoColor;
			}
		}
		int color = scaleAlpha((HUD_ALPHA << 24) | rgb, fade);
		graphics.fill(squareX, squareY, squareX + DURABILITY_SIZE, squareY + DURABILITY_SIZE, color);

		Font font = Minecraft.getInstance().font;
		int textX = squareX + DURABILITY_SIZE + DURABILITY_TEXT_GAP;
		int maxWidth = MARGIN + boxWidth - ANCHOR_TEXT_PAD - textX;
		String name = empty ? "Hands" : stack.getHoverName().getString();
		if (maxWidth <= 0) {
			return;
		}
		if (font.width(name) > maxWidth) {
			String ellipsis = "...";
			int body = maxWidth - font.width(ellipsis);
			name = body <= 0 ? font.plainSubstrByWidth(name, maxWidth) : font.plainSubstrByWidth(name, body) + ellipsis;
		}
		graphics.drawString(font, name, textX, y + ANCHOR_TEXT_PAD, scaleAlpha((HUD_ALPHA << 24) | 0xFFFFFF, fade), false);
	}

	private static void blitAnchor(GuiGraphics graphics, int x, int y, int width) {
		int border = 1;
		int middle = width - border * 2;
		int sourceMiddle = ANCHOR_WIDTH - border * 2;
		graphics.blit(LEFT_ANCHOR, x, y, border, ANCHOR_HEIGHT, 0, 0, border, ANCHOR_HEIGHT, ANCHOR_WIDTH, ANCHOR_HEIGHT);
		if (middle > 0) {
			graphics.blit(LEFT_ANCHOR, x + border, y, middle, ANCHOR_HEIGHT, border, 0, sourceMiddle, ANCHOR_HEIGHT, ANCHOR_WIDTH, ANCHOR_HEIGHT);
		}
		graphics.blit(LEFT_ANCHOR, x + width - border, y, border, ANCHOR_HEIGHT, ANCHOR_WIDTH - border, 0, border, ANCHOR_HEIGHT, ANCHOR_WIDTH, ANCHOR_HEIGHT);
	}

	static int hotbarX(int screenWidth) {
		int rowWidth = SLOT_COUNT * SLOT_SIZE + (SLOT_COUNT - 1) * SLOT_GAP;
		return (screenWidth - rowWidth) / 2;
	}

	static int hotbarY(int screenHeight) {
		return screenHeight - MARGIN - SLOT_SIZE;
	}

	static void drawHotbarFrames(GuiGraphics graphics, Player player, int startX, int y) {
		int pitch = SLOT_SIZE + SLOT_GAP;
		int selected = player.getInventory().selected;
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			blitSlot(graphics, startX + slot * pitch, y, slot == selected, 1.0F);
		}
	}

	private static void drawHudHotbarFrames(GuiGraphics graphics, Player player, int startX, int y, float fade) {
		int pitch = SLOT_SIZE + SLOT_GAP;
		int rowWidth = SLOT_COUNT * SLOT_SIZE + (SLOT_COUNT - 1) * SLOT_GAP;
		int selected = player.getInventory().selected;
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			blitSlot(graphics, startX + slot * pitch, y, slot == selected, fade);
		}
		ItemStack offhand = player.getOffhandItem();
		if (!offhand.isEmpty()) {
			int offX = player.getMainArm() == HumanoidArm.RIGHT
					? startX - SLOT_SIZE - OFFHAND_GAP
					: startX + rowWidth + OFFHAND_GAP;
			blitSlot(graphics, offX, y, false, fade);
		}
	}

	private static void drawHudHotbarItems(GuiGraphics graphics, Player player, float partialTick, int startX, int y, float fade) {
		int pitch = SLOT_SIZE + SLOT_GAP;
		int rowWidth = SLOT_COUNT * SLOT_SIZE + (SLOT_COUNT - 1) * SLOT_GAP;
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			int x = startX + slot * pitch;
			renderSlotItem(graphics, player, player.getInventory().getItem(slot), x + 2, y + 2, partialTick, slot, fade);
		}
		ItemStack offhand = player.getOffhandItem();
		if (!offhand.isEmpty()) {
			int offX = player.getMainArm() == HumanoidArm.RIGHT
					? startX - SLOT_SIZE - OFFHAND_GAP
					: startX + rowWidth + OFFHAND_GAP;
			renderSlotItem(graphics, player, offhand, offX + 2, y + 2, partialTick, SLOT_COUNT, fade);
		}
	}

	private static void blitSlot(GuiGraphics graphics, int x, int y, boolean selected, float fade) {
		prepareSprite(graphics, 1.0F, 1.0F, 1.0F, fade);
		graphics.blit(
			SLOTS,
			x,
			y,
			SLOT_SIZE,
			SLOT_SIZE,
			selected ? SLOT_SELECTED_U : SLOT_NORMAL_U,
			0,
			SLOT_SIZE,
			SLOT_SIZE,
			SLOT_SHEET_WIDTH,
			SLOT_SHEET_HEIGHT
		);
	}

	private static void renderSlotItem(GuiGraphics graphics, Player player, ItemStack stack, int x, int y, float partialTick, int seed, float fade) {
		if (stack.isEmpty() || fade <= 0.001F) {
			return;
		}

		prepareSprite(graphics, 1.0F, 1.0F, 1.0F, fade);
		float pop = stack.getPopTime() - partialTick;
		if (pop > 0.0F) {
			float scale = 1.0F + pop / 5.0F;
			PoseStack pose = graphics.pose();
			pose.pushPose();
			pose.translate(x + 8, y + 12, 0.0F);
			pose.scale(1.0F / scale, (scale + 1.0F) / 2.0F, 1.0F);
			pose.translate(-(x + 8), -(y + 12), 0.0F);
		}

		graphics.renderItem(player, stack, x, y, seed);
		if (pop > 0.0F) {
			graphics.pose().popPose();
		}
		if (fade > 0.95F) {
			graphics.renderItemDecorations(Minecraft.getInstance().font, stack, x, y);
		}
		prepareSprite(graphics, 1.0F, 1.0F, 1.0F, 1.0F);
	}

	private static int scaleAlpha(int argb, float fade) {
		int alpha = Math.round(((argb >>> 24) & 0xFF) * fade);
		return (alpha << 24) | (argb & 0xFFFFFF);
	}

	private static void blitIcon(GuiGraphics graphics, ResourceLocation texture, int x, int y, int column, int spriteWidth, int spriteHeight, int stride, int sheetWidth, int sheetHeight, float fade) {
		prepareSprite(graphics, 1.0F, 1.0F, 1.0F, fade);
		graphics.blit(texture, x, y, spriteWidth, spriteHeight, column * stride, 0, spriteWidth, spriteHeight, sheetWidth, sheetHeight);
	}
}
