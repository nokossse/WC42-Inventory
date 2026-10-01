package wc42;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Wc42.MOD_ID)
public class Wc42 {
	public static final String MOD_ID = "wc42";

	public Wc42(FMLJavaModLoadingContext context) {
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(HudVisibility::clientTick);
	}
}
