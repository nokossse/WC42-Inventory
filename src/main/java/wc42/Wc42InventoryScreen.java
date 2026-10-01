package wc42;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Wc42.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class Wc42InventoryScreen extends AbstractContainerScreen<InventoryMenu> {
	private enum Page {
		PLAYER,
		CURIOS,
		STATS
	}

	private static final int PANEL_WIDTH = 200;
	private static final int PANEL_HEIGHT = 280;
	private static final int PANEL_COLOR = argb(22, 22, 22, 184);
	private static final int BAND_COLOR = argb(0, 0, 0, 192);
	private static final int SLOT_COLOR = argb(22, 22, 22, 181);
	private static final int BORDER_COLOR = argb(48, 48, 48, 184);
	private static final int ARROW_COLOR = argb(22, 22, 22, 221);
	private static final int TAB_Y = 5;
	private static final int TAB_HEIGHT = 13;
	private static final int TAB_X = 6;
	private static final int TAB_GAP = 2;
	private static final int TAB_PAD = 4;
	private static final int TAB_COUNT = 3;
	private static final int STAT_X = 10;
	private static final int STAT_Y = 31;
	private static final int STAT_WIDTH = 180;
	private static final int STAT_HEIGHT = 12;
	private static final int STAT_PITCH = 16;
	private static final int STAT_FILL = argb(18, 18, 18, 192);
	private static final String[] TAB_LABELS = {"Player", "Curios", "Stats"};
	private static final int PLAYER_X = 64;
	private static final int PLAYER_Y = 27;
	private static final int PLAYER_WIDTH = 72;
	private static final int PLAYER_HEIGHT = 132;
	private static final int PLAYER_SCALE = 68;
	private static final int LABEL_X = 6;
	private static final int INVENTORY_LABEL_Y = 164;
	private static final int INVENTORY_LABEL_HEIGHT = 21;
	private static final int EMPTY_ICON_ALPHA = 220;
	private static final int PANEL_TOP = 1;
	private static final int PANEL_BOTTOM = 256;
	private static final int PANEL_LEFT = 2;
	private static final int PANEL_RIGHT = 198;
	private static final int[][] CELLS = cells();
	private static final int[][] ARROW = {
		{163, 81, 3, 5},
		{160, 86, 9, 2},
		{161, 88, 7, 1},
		{162, 89, 5, 1},
		{163, 90, 3, 1},
		{164, 91, 1, 1}
	};
	private static final int[][] CURIOS_COLUMNS = {
		{11, 30}, {11, 54}, {11, 78}, {11, 102}, {11, 126},
		{35, 30}, {35, 54}, {35, 78}, {35, 102}, {35, 126},
		{143, 30}, {143, 54}, {143, 78}, {143, 102}, {143, 126},
		{167, 30}, {167, 54}, {167, 78}, {167, 102}, {167, 126}
	};

	private static Page pendingPage = Page.PLAYER;

	private final List<int[]> curiosCells = new ArrayList<>();
	private final List<Slot> curiosShown = new ArrayList<>();
	private Page page;
	private int statsRefresh;

	public Wc42InventoryScreen(InventoryMenu menu, Inventory inventory, Page page) {
		super(menu, inventory, Component.empty());
		this.imageWidth = PANEL_WIDTH;
		this.imageHeight = PANEL_HEIGHT;
		this.page = page;
		this.slotColor = (88 << 24) | (141 << 16) | (141 << 8) | 141;
	}

	@SubscribeEvent
	public static void open(ScreenEvent.Opening event) {
		Minecraft client = Minecraft.getInstance();
		MultiPlayerGameMode gameMode = client.gameMode;
		if (client.player == null || gameMode == null) {
			return;
		}
		GameType type = gameMode.getPlayerMode();
		if (type != GameType.SURVIVAL && type != GameType.ADVENTURE) {
			return;
		}
		var next = event.getNewScreen();
		if (next instanceof Wc42InventoryScreen) {
			return;
		}
		boolean curiosScreen = ModList.get().isLoaded("curios") && CuriosAccess.isCuriosScreen(next);
		if (!(next instanceof InventoryScreen) && !curiosScreen) {
			return;
		}
		if (!(next instanceof AbstractContainerScreen<?> container) || !(container.getMenu() instanceof InventoryMenu menu)) {
			return;
		}
		InventoryMenu opened = curiosScreen ? menu : client.player.inventoryMenu;
		Page page = curiosScreen ? Page.CURIOS : pendingPage;
		pendingPage = Page.PLAYER;
		event.setNewScreen(new Wc42InventoryScreen(opened, client.player.getInventory(), page));
	}

	@Override
	protected void init() {
		super.init();
		placeSlots();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && clickTab(mouseX, mouseY)) {
			return true;
		}
		placeSlots();
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private boolean clickTab(double mouseX, double mouseY) {
		int index = tabAt(mouseX, mouseY);
		if (index < 0) {
			return false;
		}
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		Page target = pageFor(index);
		if (target == this.page) {
			return true;
		}
		boolean curiosLoaded = ModList.get().isLoaded("curios");
		boolean onCuriosMenu = curiosLoaded && CuriosAccess.isCuriosMenu(this.menu);
		if (target == Page.CURIOS && curiosLoaded) {
			ItemStack carried = this.menu.getCarried();
			this.menu.setCarried(ItemStack.EMPTY);
			CuriosAccess.openCurios(carried);
			return true;
		}
		if (onCuriosMenu && this.minecraft.player != null) {
			ItemStack carried = this.menu.getCarried();
			this.menu.setCarried(ItemStack.EMPTY);
			pendingPage = target;
			this.minecraft.player.containerMenu = this.minecraft.player.inventoryMenu;
			this.minecraft.setScreen(new InventoryScreen(this.minecraft.player));
			this.minecraft.player.containerMenu.setCarried(carried);
			CuriosAccess.openVanilla(carried);
			return true;
		}
		this.page = target;
		if (target == Page.STATS) {
			this.statsRefresh = 0;
		}
		placeSlots();
		return true;
	}

	private void placeSlots() {
		for (int[] cell : CELLS) {
			if (cell[3] >= this.menu.slots.size()) {
				continue;
			}
			Slot slot = this.menu.slots.get(cell[3]);
			boolean side = cell[3] <= 8 || cell[3] == 45;
			if ((this.page == Page.CURIOS || this.page == Page.STATS) && side) {
				slot.x = -2000;
				slot.y = -2000;
			} else {
				int pad = (cell[2] - 16) / 2;
				slot.x = cell[0] + pad;
				slot.y = cell[1] + pad;
			}
		}
		placeHotbar();
		placeCurios();
	}

	private void placeHotbar() {
		int x = StatusHud.hotbarX(this.width) - this.leftPos;
		int y = StatusHud.hotbarY(this.height) - this.topPos;
		int pitch = 21;
		for (int column = 0; column < 9; column++) {
			int slotIndex = 36 + column;
			if (slotIndex >= this.menu.slots.size()) {
				return;
			}
			Slot slot = this.menu.slots.get(slotIndex);
			slot.x = x + column * pitch + 2;
			slot.y = y + 2;
		}
	}

	private void placeCurios() {
		this.curiosCells.clear();
		this.curiosShown.clear();
		if (!ModList.get().isLoaded("curios")) {
			return;
		}
		List<Slot> equipment = CuriosAccess.equipment(this.menu);
		if (this.page == Page.CURIOS) {
			int count = Math.min(equipment.size(), CURIOS_COLUMNS.length);
			for (int i = 0; i < count; i++) {
				Slot slot = equipment.get(i);
				int[] pos = CURIOS_COLUMNS[i];
				slot.x = pos[0] + 3;
				slot.y = pos[1] + 3;
				this.curiosCells.add(new int[] {pos[0], pos[1], 22});
				this.curiosShown.add(slot);
			}
		}
		CuriosAccess.parkHidden(this.menu, this.curiosShown);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		placeSlots();
		drawDim(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);
		drawHotbarDim(graphics);
		drawEmptyIcons(graphics);
		drawEffects(graphics, mouseX, mouseY);
		this.renderTooltip(graphics, mouseX, mouseY);
	}

	private void drawEffects(GuiGraphics graphics, int mouseX, int mouseY) {
		if (this.minecraft.player == null) {
			return;
		}
		List<MobEffectInstance> effects = new ArrayList<>(this.minecraft.player.getActiveEffects());
		if (effects.isEmpty()) {
			return;
		}
		effects.sort(Comparator.naturalOrder());
		boolean wide = this.leftPos >= 124;
		int x = wide ? this.leftPos - 124 : Math.max(0, this.leftPos - 36);
		int stride = effects.size() > 5 ? 132 / (effects.size() - 1) : 33;
		int y = this.topPos;
		MobEffectInstance hovered = null;
		for (MobEffectInstance effect : effects) {
			if (wide) {
				graphics.blit(INVENTORY_LOCATION, x, y, 0, 166, 120, 32);
			} else {
				graphics.blit(INVENTORY_LOCATION, x, y, 0, 198, 32, 32);
			}
			TextureAtlasSprite sprite = this.minecraft.getMobEffectTextures().get(effect.getEffect());
			int iconX = x + (wide ? 6 : 7);
			graphics.blit(iconX, y + 7, 0, 18, 18, sprite);
			if (wide) {
				graphics.drawString(this.font, effectName(effect), x + 28, y + 6, 0xFFFFFF);
				graphics.drawString(this.font, MobEffectUtil.formatDuration(effect, 1.0F), x + 28, y + 16, 0x7F7F7F);
			} else if (mouseX >= x && mouseX < x + 32 && mouseY >= y && mouseY < y + stride) {
				hovered = effect;
			}
			y += stride;
		}
		if (hovered != null) {
			graphics.renderComponentTooltip(this.font, List.of(effectName(hovered), MobEffectUtil.formatDuration(hovered, 1.0F)), mouseX, mouseY);
		}
	}

	private static Component effectName(MobEffectInstance effect) {
		MutableComponent name = Component.translatable(effect.getEffect().getDescriptionId());
		int amplifier = effect.getAmplifier();
		if (amplifier >= 1 && amplifier <= 9) {
			name.append(" ").append(Component.translatable("potion.potency." + amplifier));
		}
		return name;
	}

	private void drawDim(GuiGraphics graphics) {
		int color = (100 << 24) | (16 << 16) | (16 << 8) | 16;
		int x = StatusHud.hotbarX(this.width);
		int y = StatusHud.hotbarY(this.height);
		int slot = 20;
		int pitch = 21;
		int rowWidth = 9 * slot + 8;
		graphics.fill(0, 0, this.width, y, color);
		graphics.fill(0, y, x, y + slot, color);
		graphics.fill(x + rowWidth, y, this.width, y + slot, color);
		graphics.fill(0, y + slot, this.width, this.height, color);
		for (int gap = 0; gap < 8; gap++) {
			int gapX = x + gap * pitch + slot;
			graphics.fill(gapX, y, gapX + 1, y + slot, color);
		}
	}

	private void drawHotbarDim(GuiGraphics graphics) {
		int color = (180 << 24) | (16 << 16) | (16 << 8) | 16;
		int x = StatusHud.hotbarX(this.width);
		int y = StatusHud.hotbarY(this.height);
		int pitch = 21;
		for (int slot = 0; slot < 9; slot++) {
			int slotX = x + slot * pitch;
			graphics.fill(slotX, y, slotX + 20, y + 20, color);
		}
	}

	private void drawEmptyIcons(GuiGraphics graphics) {
		float alpha = EMPTY_ICON_ALPHA / 255.0F;
		for (Slot slot : this.menu.slots) {
			if (slot.x < 0 || !slot.getItem().isEmpty()) {
				continue;
			}
			Pair<ResourceLocation, ResourceLocation> icon = slot.getNoItemIcon();
			if (icon == null) {
				continue;
			}
			TextureAtlasSprite sprite = this.minecraft.getTextureAtlas(icon.getFirst()).apply(icon.getSecond());
			graphics.blit(this.leftPos + slot.x, this.topPos + slot.y, 400, 16, 16, sprite, 0.0F, 0.0F, 0.0F, alpha);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (this.page != Page.STATS || this.minecraft.getConnection() == null) {
			return;
		}
		if (this.statsRefresh > 0) {
			this.statsRefresh--;
			return;
		}
		this.statsRefresh = 20;
		this.minecraft.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		fillPanel(graphics, this.leftPos, this.topPos);
		for (int index = 0; index < TAB_COUNT; index++) {
			int[] tab = tabRect(index);
			int color = pageFor(index) == this.page ? BORDER_COLOR : PANEL_COLOR;
			graphics.fill(this.leftPos + tab[0], this.topPos + tab[1], this.leftPos + tab[0] + tab[2], this.topPos + tab[1] + tab[3], color);
			drawCentered(graphics, TAB_LABELS[index], this.leftPos + tab[0], this.topPos + tab[1], tab[2], tab[3]);
		}
		drawSlotBox(graphics, 5, 189, 9, 3);
		if (this.page == Page.PLAYER) {
			drawSlotBox(graphics, 143, 35, 2, 2);
		}
		for (int[] cell : visibleCells()) {
			if (isGridCell(cell)) {
				continue;
			}
			int x = this.leftPos + cell[0];
			int y = this.topPos + cell[1];
			int size = cell[2];
			graphics.fill(x, y, x + size, y + 1, BORDER_COLOR);
			graphics.fill(x, y + size - 1, x + size, y + size, BORDER_COLOR);
			graphics.fill(x, y + 1, x + 1, y + size - 1, BORDER_COLOR);
			graphics.fill(x + size - 1, y + 1, x + size, y + size - 1, BORDER_COLOR);
			graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, SLOT_COLOR);
		}
		if (this.page == Page.PLAYER) {
			for (int[] part : ARROW) {
				graphics.fill(this.leftPos + part[0], this.topPos + part[1], this.leftPos + part[0] + part[2], this.topPos + part[1] + part[3], ARROW_COLOR);
			}
		}
		drawLabel(graphics, "Inventory", this.leftPos + LABEL_X, this.topPos + INVENTORY_LABEL_Y, INVENTORY_LABEL_HEIGHT);

		Player player = this.minecraft.player;
		if (player == null) {
			return;
		}
		if (this.page == Page.STATS) {
			drawStats(graphics, this.minecraft.player);
		} else {
			int boxX = this.leftPos + PLAYER_X;
			int boxY = this.topPos + PLAYER_Y;
			int entityX = boxX + PLAYER_WIDTH / 2;
			int entityY = boxY + PLAYER_HEIGHT;
			graphics.enableScissor(boxX, boxY, boxX + PLAYER_WIDTH, boxY + PLAYER_HEIGHT);
			InventoryScreen.renderEntityInInventoryFollowsMouse(
				graphics,
				entityX,
				entityY,
				PLAYER_SCALE,
				entityX - mouseX,
				entityY - PLAYER_HEIGHT / 2.0F - mouseY,
				player
			);
			graphics.disableScissor();
		}
		StatusHud.drawHotbarFrames(graphics, player, StatusHud.hotbarX(this.width), StatusHud.hotbarY(this.height));
	}

	private void drawSlotBox(GuiGraphics graphics, int x, int y, int columns, int rows) {
		int stride = 21;
		int width = columns * 20 + columns + 1;
		int height = rows * 20 + rows + 1;
		int x0 = this.leftPos + x;
		int y0 = this.topPos + y;
		graphics.fill(x0, y0, x0 + width, y0 + 1, BORDER_COLOR);
		graphics.fill(x0, y0 + height - 1, x0 + width, y0 + height, BORDER_COLOR);
		graphics.fill(x0, y0 + 1, x0 + 1, y0 + height - 1, BORDER_COLOR);
		graphics.fill(x0 + width - 1, y0 + 1, x0 + width, y0 + height - 1, BORDER_COLOR);
		for (int row = 1; row < rows; row++) {
			int lineY = y0 + row * stride;
			graphics.fill(x0 + 1, lineY, x0 + width - 1, lineY + 1, BORDER_COLOR);
		}
		for (int column = 1; column < columns; column++) {
			int lineX = x0 + column * stride;
			for (int row = 0; row < rows; row++) {
				int lineY = y0 + 1 + row * stride;
				graphics.fill(lineX, lineY, lineX + 1, lineY + 20, BORDER_COLOR);
			}
		}
		for (int row = 0; row < rows; row++) {
			for (int column = 0; column < columns; column++) {
				int slotX = x0 + 1 + column * stride;
				int slotY = y0 + 1 + row * stride;
				graphics.fill(slotX, slotY, slotX + 20, slotY + 20, SLOT_COLOR);
			}
		}
	}

	private static boolean isGridCell(int[] cell) {
		int slot = cell[3];
		return (slot >= 1 && slot <= 4) || (slot >= 9 && slot <= 35);
	}

	private List<int[]> visibleCells() {
		List<int[]> cells = new ArrayList<>();
		for (int[] cell : CELLS) {
			boolean side = cell[3] <= 8 || cell[3] == 45;
			if ((this.page == Page.CURIOS || this.page == Page.STATS) && side) {
				continue;
			}
			cells.add(cell);
		}
		if (this.page == Page.CURIOS) {
			for (int[] cell : this.curiosCells) {
				cells.add(new int[] {cell[0], cell[1], cell[2], -1, SLOT_COLOR});
			}
		}
		return cells;
	}

	private void fillPanel(GuiGraphics graphics, int originX, int originY) {
		for (int y = PANEL_TOP; y < PANEL_BOTTOM; y++) {
			if (y == 22 || y == 163 || y == 185) {
				continue;
			}
			int color = band(y) ? BAND_COLOR : PANEL_COLOR;
			int cursor = PANEL_LEFT;
			for (int[] span : holeSpans(y)) {
				int start = Math.max(span[0], PANEL_LEFT);
				int end = Math.min(span[1], PANEL_RIGHT);
				if (end <= cursor) {
					continue;
				}
				if (start > cursor) {
					graphics.fill(originX + cursor, originY + y, originX + start, originY + y + 1, color);
				}
				cursor = Math.max(cursor, end);
			}
			if (cursor < PANEL_RIGHT) {
				graphics.fill(originX + cursor, originY + y, originX + PANEL_RIGHT, originY + y + 1, color);
			}
		}
	}

	private int[][] holeSpans(int y) {
		int[] starts = new int[24];
		int[] ends = new int[24];
		int count = 0;
		for (int[] cell : visibleCells()) {
			if (y >= cell[1] && y < cell[1] + cell[2]) {
				starts[count] = cell[0];
				ends[count] = cell[0] + cell[2];
				count++;
			}
		}
		if (this.page == Page.PLAYER) {
			for (int[] part : ARROW) {
				if (y >= part[1] && y < part[1] + part[3]) {
					starts[count] = part[0];
					ends[count] = part[0] + part[2];
					count++;
				}
			}
		}
		if (this.page == Page.STATS) {
			for (int row = 0; row < 8; row++) {
				int top = STAT_Y + row * STAT_PITCH;
				if (y >= top && y < top + STAT_HEIGHT) {
					starts[count] = STAT_X;
					ends[count] = STAT_X + STAT_WIDTH;
					count++;
				}
			}
		}
		for (int index = 0; index < TAB_COUNT; index++) {
			int[] tab = tabRect(index);
			if (y >= tab[1] && y < tab[1] + tab[3]) {
				starts[count] = tab[0];
				ends[count] = tab[0] + tab[2];
				count++;
			}
		}
		for (int i = 1; i < count; i++) {
			int start = starts[i];
			int end = ends[i];
			int j = i;
			while (j > 0 && starts[j - 1] > start) {
				starts[j] = starts[j - 1];
				ends[j] = ends[j - 1];
				j--;
			}
			starts[j] = start;
			ends[j] = end;
		}
		int[][] spans = new int[count][2];
		int n = 0;
		for (int i = 0; i < count; i++) {
			if (n > 0 && starts[i] <= spans[n - 1][1]) {
				spans[n - 1][1] = Math.max(spans[n - 1][1], ends[i]);
			} else {
				spans[n][0] = starts[i];
				spans[n][1] = ends[i];
				n++;
			}
		}
		int[][] trimmed = new int[n][2];
		System.arraycopy(spans, 0, trimmed, 0, n);
		return trimmed;
	}

	private int[] tabRect(int index) {
		int x = TAB_X;
		int width = this.font.width(TAB_LABELS[index]) + TAB_PAD * 2;
		for (int previous = 0; previous < index; previous++) {
			x += this.font.width(TAB_LABELS[previous]) + TAB_PAD * 2 + TAB_GAP;
		}
		return new int[] {x, TAB_Y, width, TAB_HEIGHT};
	}

	private static Page pageFor(int index) {
		return switch (index) {
			case 0 -> Page.PLAYER;
			case 1 -> Page.CURIOS;
			default -> Page.STATS;
		};
	}

	private void drawStats(GuiGraphics graphics, LocalPlayer player) {
		for (int row = 0; row < 8; row++) {
			int x = this.leftPos + STAT_X;
			int y = this.topPos + STAT_Y + row * STAT_PITCH;
			graphics.fill(x, y, x + STAT_WIDTH, y + 1, BORDER_COLOR);
			graphics.fill(x, y + STAT_HEIGHT - 1, x + STAT_WIDTH, y + STAT_HEIGHT, BORDER_COLOR);
			graphics.fill(x, y + 1, x + 1, y + STAT_HEIGHT - 1, BORDER_COLOR);
			graphics.fill(x + STAT_WIDTH - 1, y + 1, x + STAT_WIDTH, y + STAT_HEIGHT - 1, BORDER_COLOR);
			graphics.fill(x + 1, y + 1, x + STAT_WIDTH - 1, y + STAT_HEIGHT - 1, STAT_FILL);
			String text = statLine(player, row);
			if (text != null) {
				graphics.drawString(this.font, text, x + 2, y + 2, 0xFFFFFFFF, false);
			}
		}
	}

	private static String statLine(LocalPlayer player, int row) {
		return switch (row) {
			case 0 -> String.format(Locale.ROOT, "XP : %d (%.1f%%)", player.experienceLevel, player.experienceProgress * 100.0F);
			case 1 -> "Armor : " + formatStat(attribute(player, Attributes.ARMOR));
			case 2 -> "Armor toughness : " + formatStat(attribute(player, Attributes.ARMOR_TOUGHNESS));
			case 3 -> "Knockback resistance : " + formatStat(knockback(player));
			case 4 -> "Player kills : " + player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS));
			case 5 -> "Deaths : " + player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
			case 6 -> playtime(player);
			default -> null;
		};
	}

	private static double knockback(Player player) {
		double amount = 0.0D;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = player.getItemBySlot(slot);
			for (AttributeModifier modifier : stack.getAttributeModifiers(slot).get(Attributes.KNOCKBACK_RESISTANCE)) {
				amount += modifier.getAmount();
			}
		}
		if (amount == 0.0D) {
			amount = attribute(player, Attributes.KNOCKBACK_RESISTANCE);
		}
		return amount * 10.0D;
	}

	private static double attribute(Player player, Attribute attribute) {
		var instance = player.getAttribute(attribute);
		return instance == null ? 0.0D : instance.getValue();
	}

	private static String formatStat(double value) {
		double rounded = Math.rint(value);
		if (Math.abs(value - rounded) < 0.05D) {
			return Integer.toString((int) rounded);
		}
		return String.format(Locale.ROOT, "%.1f", value);
	}

	private static String playtime(LocalPlayer player) {
		int ticks = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
		int minutes = ticks / (20 * 60);
		return String.format(Locale.ROOT, "Playtime : %02d:%02d", minutes / 60, minutes % 60);
	}

	private int tabAt(double mouseX, double mouseY) {
		for (int index = 0; index < TAB_COUNT; index++) {
			int[] tab = tabRect(index);
			int x = this.leftPos + tab[0];
			int y = this.topPos + tab[1];
			if (mouseX >= x && mouseX < x + tab[2] && mouseY >= y && mouseY < y + tab[3]) {
				return index;
			}
		}
		return -1;
	}

	private static boolean band(int y) {
		return (y >= 1 && y <= 21) || (y >= 164 && y <= 184);
	}

	private static int argb(int red, int green, int blue, int alpha) {
		return (alpha << 24) | (red << 16) | (green << 8) | blue;
	}

	private static int[][] cells() {
		int[] xs = {5, 26, 47, 68, 89, 110, 131, 152, 173};
		int[] rows = {189, 210, 231};
		int[][] cells = new int[37][];
		cells[0] = new int[] {153, 94, 23, 0, SLOT_COLOR};
		cells[1] = new int[] {143, 35, 22, 1, SLOT_COLOR};
		cells[2] = new int[] {164, 35, 22, 2, SLOT_COLOR};
		cells[3] = new int[] {143, 56, 22, 3, SLOT_COLOR};
		cells[4] = new int[] {164, 56, 22, 4, SLOT_COLOR};
		cells[5] = new int[] {35, 30, 22, 5, SLOT_COLOR};
		cells[6] = new int[] {35, 54, 22, 6, SLOT_COLOR};
		cells[7] = new int[] {35, 78, 22, 7, SLOT_COLOR};
		cells[8] = new int[] {35, 102, 22, 8, SLOT_COLOR};
		int index = 9;
		int slot = 9;
		for (int row = 0; row < rows.length; row++) {
			for (int x : xs) {
				cells[index] = new int[] {x, rows[row], 22, slot, SLOT_COLOR};
				index++;
				slot++;
			}
		}
		cells[index] = new int[] {35, 138, 22, 45, SLOT_COLOR};
		return cells;
	}

	private static void drawLabel(GuiGraphics graphics, String text, int x, int y, int height) {
		Font font = Minecraft.getInstance().font;
		graphics.drawString(font, text, x, y + (height - font.lineHeight) / 2, 0xFFFFFFFF, false);
	}

	private static void drawCentered(GuiGraphics graphics, String text, int x, int y, int width, int height) {
		Font font = Minecraft.getInstance().font;
		graphics.drawString(font, text, x + (width - font.width(text)) / 2, y + (height - font.lineHeight) / 2, 0xFFFFFFFF, false);
	}
}
