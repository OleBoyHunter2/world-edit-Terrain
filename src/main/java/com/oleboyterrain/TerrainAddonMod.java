package com.oleboyterrain;

import com.oleboyterrain.command.TerrainCommand;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerrainAddonMod implements ModInitializer {
	public static final String MOD_ID = "terrain-addon";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		TerrainCommand.register();
		LOGGER.info("Terrain Addon loaded!");
	}
}