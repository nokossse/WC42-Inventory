package wc42;

import java.util.Optional;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;

import net.minecraft.world.item.ItemStack;

final class GunAmmo {
	private static final int WHITE = 0xFFFFFF;
	private static final int YELLOW = 0xFBD636;
	private static final int RED = 0xAC3232;
	private static final int DARK_RED = 0x8D2E2E;

	private GunAmmo() {
	}

	static int color(ItemStack stack) {
		IGun gun = IGun.getIGunOrNull(stack);
		if (gun == null) {
			return -1;
		}
		Optional<CommonGunIndex> index = TimelessAPI.getCommonGunIndex(gun.getGunId(stack));
		if (index.isEmpty()) {
			return -1;
		}
		GunData data = index.get().getGunData();
		int max = AttachmentDataUtils.getAmmoCountWithAttachment(stack, data);
		if (max <= 0) {
			return -1;
		}
		int current = Math.max(0, gun.getCurrentAmmoCount(stack));
		if (gun.hasBulletInBarrel(stack) && data.getBolt() != Bolt.OPEN_BOLT) {
			current++;
		}
		if (current == 0) {
			return DARK_RED;
		}
		float ratio = current / (float) max;
		if (ratio >= 0.60F) {
			return WHITE;
		}
		if (ratio >= 0.30F) {
			return YELLOW;
		}
		return RED;
	}
}
