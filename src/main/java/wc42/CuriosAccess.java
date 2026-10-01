package wc42;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import top.theillusivec4.curios.client.gui.CuriosScreen;
import top.theillusivec4.curios.client.gui.CuriosScreenV2;
import top.theillusivec4.curios.common.inventory.CurioSlot;
import top.theillusivec4.curios.common.inventory.container.CuriosContainer;
import top.theillusivec4.curios.common.network.NetworkHandler;
import top.theillusivec4.curios.common.network.client.CPacketOpenCurios;
import top.theillusivec4.curios.common.network.client.CPacketOpenVanilla;

final class CuriosAccess {
	private CuriosAccess() {
	}

	static boolean isCuriosScreen(Screen screen) {
		return screen instanceof CuriosScreen || screen instanceof CuriosScreenV2;
	}

	static boolean isCuriosMenu(AbstractContainerMenu menu) {
		return menu instanceof CuriosContainer;
	}

	static List<Slot> equipment(AbstractContainerMenu menu) {
		List<Slot> slots = new ArrayList<>();
		for (Slot slot : menu.slots) {
			if (slot instanceof CurioSlot curio && !curio.isCosmetic()) {
				slots.add(slot);
			}
		}
		return slots;
	}

	static void parkHidden(AbstractContainerMenu menu, Collection<Slot> shown) {
		for (Slot slot : menu.slots) {
			if (slot instanceof CurioSlot && !shown.contains(slot)) {
				slot.x = -2000;
				slot.y = -2000;
			}
		}
	}

	static void openCurios(ItemStack carried) {
		NetworkHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(), new CPacketOpenCurios(carried));
	}

	static void openVanilla(ItemStack carried) {
		NetworkHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(), new CPacketOpenVanilla(carried));
	}
}
