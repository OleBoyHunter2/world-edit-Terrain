package com.oleboyterrain.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.oleboyterrain.TerrainAddonMod;
import com.oleboyterrain.noise.NoiseType;
import com.oleboyterrain.noise.TerrainGenerator;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class TerrainCommand {

    private static final SuggestionProvider<ServerCommandSource> NOISE_SUGGESTIONS = (context, builder) -> {
        for (NoiseType noiseType : NoiseType.values()) {
            builder.suggest(noiseType.name().toLowerCase());
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> BLOCK_SUGGESTIONS = (context, builder) -> {
        for (var block : Registries.BLOCK) {
            String path = Registries.BLOCK.getId(block).getPath();
            String fullId = Registries.BLOCK.getId(block).toString();
            builder.suggest(path);
            builder.suggest(fullId);
        }
        return builder.buildFuture();
    };

    private static LiteralArgumentBuilder<ServerCommandSource> buildTerrainCommand() {
        return literal("terrain")
                .requires(src -> src.getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS)))
                .then(argument("noiseType", StringArgumentType.word())
                        .suggests(NOISE_SUGGESTIONS)
                        .then(argument("height", IntegerArgumentType.integer(1, 256))
                                .then(argument("scale", DoubleArgumentType.doubleArg(0.1, 500.0))
                                        .then(argument("octaves", IntegerArgumentType.integer(1, 8))
                                                .then(argument("roughness", DoubleArgumentType.doubleArg(0.1, 1.0))
                                                        .then(argument("block", StringArgumentType.greedyString())
                                                                .suggests(BLOCK_SUGGESTIONS)
                                                                .executes(ctx -> {
                                                                    String noiseArg = StringArgumentType.getString(ctx, "noiseType");
                                                                    int height = IntegerArgumentType.getInteger(ctx, "height");
                                                                    double scale = DoubleArgumentType.getDouble(ctx, "scale");
                                                                    int octaves = IntegerArgumentType.getInteger(ctx, "octaves");
                                                                    double roughness = DoubleArgumentType.getDouble(ctx, "roughness");
                                                                    String blockArg = StringArgumentType.getString(ctx, "block");

                                                                    ServerCommandSource source = ctx.getSource();
                                                                    ServerPlayerEntity player;
                                                                    try {
                                                                        player = source.getPlayerOrThrow();
                                                                    } catch (Exception e) {
                                                                        source.sendError(Text.literal("Must be run by a player!"));
                                                                        return 0;
                                                                    }

                                                                    NoiseType noiseType;
                                                                    try {
                                                                        noiseType = NoiseType.fromString(noiseArg);
                                                                    } catch (IllegalArgumentException e) {
                                                                        source.sendError(Text.literal(e.getMessage()));
                                                                        return 0;
                                                                    }

                                                                    com.sk89q.worldedit.entity.Player wePlayer = FabricAdapter.adaptPlayer(player);
                                                                    LocalSession session = WorldEdit.getInstance().getSessionManager().get(wePlayer);
                                                                    World weWorld = FabricAdapter.adapt(source.getWorld());

                                                                    BaseBlock surfaceBlock;
                                                                    try {
                                                                        ParserContext parserContext = new ParserContext();
                                                                        parserContext.setActor(wePlayer);
                                                                        parserContext.setSession(session);
                                                                        parserContext.setWorld(weWorld);
                                                                        parserContext.setTryLegacy(false);
                                                                        surfaceBlock = WorldEdit.getInstance()
                                                                                .getBlockFactory()
                                                                                .parseFromInput(blockArg, parserContext);
                                                                    } catch (InputParseException e) {
                                                                        source.sendError(Text.literal("Invalid block: " + blockArg));
                                                                        return 0;
                                                                    }

                                                                    Region region;
                                                                    try {
                                                                        region = session.getSelection(weWorld);
                                                                    } catch (Exception e) {
                                                                        source.sendError(Text.literal("No WorldEdit selection! Use //pos1 and //pos2 first."));
                                                                        return 0;
                                                                    }

                                                                    if (!(region instanceof CuboidRegion cuboid)) {
                                                                        source.sendError(Text.literal("Selection must be a cuboid."));
                                                                        return 0;
                                                                    }

                                                                    EditSession editSession = session.createEditSession(wePlayer);
                                                                    try {
                                                                        TerrainGenerator generator = new TerrainGenerator();
                                                                        generator.generate(editSession, cuboid, noiseType, height, scale, octaves, roughness, surfaceBlock);

                                                                        int changed = editSession.getBlockChangeCount();
                                                                        session.remember(editSession);
                                                                        source.sendFeedback(() -> Text.literal(
                                                                                "\u00A7aTerrain generated! \u00A77(" + changed + " blocks changed) " +
                                                                                        "\u00A78[" + noiseArg + " h:" + height + " s:" + scale +
                                                                                        " o:" + octaves + " r:" + roughness + " block:" + blockArg + "]"
                                                                        ), false);

                                                                    } catch (WorldEditException e) {
                                                                        source.sendError(Text.literal("WorldEdit error: " + e.getMessage()));
                                                                        TerrainAddonMod.LOGGER.error("Terrain gen failed", e);
                                                                        return 0;
                                                                    } finally {
                                                                        editSession.close();
                                                                    }

                                                                    return 1;
                                                                })
                                                        )
                                                )
                                        )
                                )
                        )
                );
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(buildTerrainCommand());
            dispatcher.register(literal("/terrain").redirect(dispatcher.getRoot().getChild("terrain")));
        });
        TerrainAddonMod.LOGGER.info("Registered /terrain and //terrain commands");
    }
}