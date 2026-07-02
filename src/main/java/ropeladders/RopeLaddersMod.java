package ropeladders;

import net.fabricmc.api.ModInitializer;
import ropeladders.core.RopeLaddersConfig;

public class RopeLaddersMod implements ModInitializer {
    public static final String MOD_ID = "ropeladders";

    @Override
    public void onInitialize() {
        RopeLaddersConfig.load();
    }
}