package wc42;

import com.simplystamina.config.StaminaConfig;
import com.simplystamina.stamina.StaminaData;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

final class StaminaAccess {
	private StaminaAccess() {
	}

	static float ratio(Player player) {
		float max = StaminaConfig.maxStamina();
		if (max <= 0.0F) {
			return 0.0F;
		}
		return Mth.clamp(StaminaData.get(player).getStamina() / max, 0.0F, 1.0F);
	}
}
