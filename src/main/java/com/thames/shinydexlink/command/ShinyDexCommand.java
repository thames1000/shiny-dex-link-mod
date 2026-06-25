package com.thames.shinydexlink.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.thames.shinydexlink.api.ShinyDexApiClient;
import com.thames.shinydexlink.api.dto.BerryReportRequest;
import com.thames.shinydexlink.api.dto.LinkRequest;
import com.thames.shinydexlink.api.dto.UnlinkRequest;
import com.thames.shinydexlink.cobblemon.SpeciesLookup;
import com.thames.shinydexlink.config.ShinyDexConfig;
import com.thames.shinydexlink.data.EventQueue;
import com.thames.shinydexlink.data.LinkedPlayer;
import com.thames.shinydexlink.data.LinkedPlayerStore;
import com.thames.shinydexlink.hunt.HuntManager;
import com.thames.shinydexlink.hunt.HuntState;
import com.thames.shinydexlink.net.HuntNetworking;
import com.thames.shinydexlink.sync.BerryScanner;
import com.thames.shinydexlink.sync.CatchEventFactory;
import com.thames.shinydexlink.util.CooldownManager;
import com.thames.shinydexlink.util.TimeUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public final class ShinyDexCommand {
    private final ShinyDexConfig config;
    private final ShinyDexApiClient apiClient;
    private final LinkedPlayerStore linkedPlayerStore;
    private final EventQueue eventQueue;
    private final HuntManager huntManager;
    private final Logger logger;
    private final CooldownManager cooldowns = new CooldownManager();

    public ShinyDexCommand(
            ShinyDexConfig config,
            ShinyDexApiClient apiClient,
            LinkedPlayerStore linkedPlayerStore,
            EventQueue eventQueue,
            HuntManager huntManager,
            Logger logger
    ) {
        this.config = config;
        this.apiClient = apiClient;
        this.linkedPlayerStore = linkedPlayerStore;
        this.eventQueue = eventQueue;
        this.huntManager = huntManager;
        this.logger = logger;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("shinydex")
                .then(literal("link")
                        .then(argument("code", StringArgumentType.word())
                                .executes(context -> link(context.getSource(), StringArgumentType.getString(context, "code")))))
                .then(literal("unlink").executes(context -> unlink(context.getSource())))
                .then(literal("status").executes(context -> status(context.getSource())))
                .then(literal("sync").executes(context -> sync(context.getSource())))
                .then(literal("berries").executes(context -> berries(context.getSource())))
                .then(literal("test").executes(context -> test(context.getSource())))
                .then(literal("hunt")
                        .executes(context -> huntStatus(context.getSource()))
                        .then(literal("stop").executes(context -> huntStop(context.getSource())))
                        .then(literal("status").executes(context -> huntStatus(context.getSource())))
                        .then(literal("reset").executes(context -> huntReset(context.getSource())))
                        .then(literal("eggs").executes(context -> huntToggleEggs(context.getSource())))
                        .then(literal("encounters").executes(context -> huntToggleEncounters(context.getSource())))
                        .then(argument("species", StringArgumentType.word())
                                .executes(context -> huntSet(context.getSource(), StringArgumentType.getString(context, "species"), null))
                                .then(argument("form", StringArgumentType.word())
                                        .executes(context -> huntSet(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "species"),
                                                StringArgumentType.getString(context, "form")))))));
    }

    private int huntSet(CommandSourceStack source, String species, String form) throws CommandSyntaxException {
        ServerPlayer player = requireHunt(source);
        if (player == null) {
            return 0;
        }
        String displayName = SpeciesLookup.displayName(species);
        HuntState state = huntManager.setTarget(
                player.getUUID(), species, displayName, form,
                config.huntCountEncounters, config.huntCountEggHatches);
        if (state == null) {
            source.sendFailure(Component.literal("Usage: /shinydex hunt <species> [form]"));
            return 0;
        }
        HuntNetworking.sendUpdate(player, state);
        String label = form == null ? state.displayName : state.displayName + " (" + form + ")";
        source.sendSuccess(() -> Component.literal("Now hunting " + label + ". Counter reset to 0."), false);
        return 1;
    }

    private int huntStatus(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = requireHunt(source);
        if (player == null) {
            return 0;
        }
        HuntState state = huntManager.get(player.getUUID()).orElse(null);
        if (state == null) {
            source.sendSuccess(() -> Component.literal("No active hunt. Start one with /shinydex hunt <species>."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal(
                "Hunting " + state.displayName + ": " + state.total() + " ("
                        + state.encounters + " encounters, " + state.eggs + " eggs, " + state.manual + " manual). "
                        + "Auto-count encounters=" + state.countEncounters + ", eggs=" + state.countEggs + "."), false);
        return 1;
    }

    private int huntStop(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = requireHunt(source);
        if (player == null) {
            return 0;
        }
        huntManager.stop(player.getUUID());
        HuntNetworking.sendUpdate(player, null);
        source.sendSuccess(() -> Component.literal("Hunt stopped."), false);
        return 1;
    }

    private int huntReset(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = requireHunt(source);
        if (player == null) {
            return 0;
        }
        HuntState state = huntManager.reset(player.getUUID()).orElse(null);
        if (state == null) {
            source.sendFailure(Component.literal("No active hunt to reset."));
            return 0;
        }
        HuntNetworking.sendUpdate(player, state);
        source.sendSuccess(() -> Component.literal("Hunt counter reset to 0."), false);
        return 1;
    }

    private int huntToggleEggs(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = requireHunt(source);
        if (player == null) {
            return 0;
        }
        HuntState state = huntManager.toggleEggs(player.getUUID()).orElse(null);
        if (state == null) {
            source.sendFailure(Component.literal("No active hunt. Start one with /shinydex hunt <species>."));
            return 0;
        }
        HuntNetworking.sendUpdate(player, state);
        source.sendSuccess(() -> Component.literal("Auto-count egg hatches: " + state.countEggs + "."), false);
        return 1;
    }

    private int huntToggleEncounters(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = requireHunt(source);
        if (player == null) {
            return 0;
        }
        HuntState state = huntManager.toggleEncounters(player.getUUID()).orElse(null);
        if (state == null) {
            source.sendFailure(Component.literal("No active hunt. Start one with /shinydex hunt <species>."));
            return 0;
        }
        HuntNetworking.sendUpdate(player, state);
        source.sendSuccess(() -> Component.literal("Auto-count encounters: " + state.countEncounters + "."), false);
        return 1;
    }

    private ServerPlayer requireHunt(CommandSourceStack source) throws CommandSyntaxException {
        if (!config.enableHuntCounter) {
            source.sendFailure(Component.literal("The ShinyDex hunt counter is disabled on this server."));
            return null;
        }
        return source.getPlayerOrException();
    }

    private int link(CommandSourceStack source, String code) throws CommandSyntaxException {
        if (!config.enabled) {
            source.sendFailure(Component.literal("ShinyDex Link is disabled on this server."));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        UUID uuid = player.getUUID();
        OptionalLong remaining = cooldowns.remainingSeconds(uuid, "link", config.linkCooldownSeconds);
        if (remaining.isPresent()) {
            source.sendFailure(Component.literal("Please wait " + remaining.getAsLong() + "s before trying another ShinyDex link code."));
            return 0;
        }

        String trimmedCode = code == null ? "" : code.trim();
        if (trimmedCode.isBlank()) {
            source.sendFailure(Component.literal("Usage: /shinydex link <code>"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Checking ShinyDex link code..."), false);
        LinkRequest request = new LinkRequest(config.serverId, trimmedCode, uuid.toString(), player.getGameProfile().getName());
        apiClient.verifyLink(request).whenComplete((response, throwable) -> runForPlayer(player, () -> {
            if (throwable != null) {
                logger.warn("ShinyDex link request failed for {}", uuid, throwable);
                player.sendSystemMessage(Component.literal("ShinyDex is temporarily unavailable. Try again later."));
                return;
            }
            if (response == null || !response.success) {
                String message = response == null || response.message == null || response.message.isBlank()
                        ? "that code is invalid or expired."
                        : response.message;
                player.sendSystemMessage(Component.literal("ShinyDex link failed: " + message));
                return;
            }
            try {
                linkedPlayerStore.link(uuid, player.getGameProfile().getName(), response.linkedAccountId);
                player.sendSystemMessage(Component.literal("ShinyDex linked. Future catches will sync automatically."));
            } catch (IOException exception) {
                logger.error("Failed to save ShinyDex link for {}", uuid, exception);
                player.sendSystemMessage(Component.literal("ShinyDex linked, but the server could not save it locally."));
            }
        }));
        return 1;
    }

    private int unlink(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        UUID uuid = player.getUUID();
        boolean wasLinked = linkedPlayerStore.isLinked(uuid);
        try {
            linkedPlayerStore.unlink(uuid);
        } catch (IOException exception) {
            logger.error("Failed to unlink ShinyDex player {}", uuid, exception);
            source.sendFailure(Component.literal("ShinyDex unlink failed: local storage could not be updated."));
            return 0;
        }

        if (!wasLinked) {
            source.sendSuccess(() -> Component.literal("You are not linked to ShinyDex."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("ShinyDex disconnected from your Minecraft UUID."), false);
        apiClient.unlink(new UnlinkRequest(config.serverId, uuid.toString())).whenComplete((response, throwable) -> {
            if (throwable != null) {
                logger.warn("ShinyDex backend unlink failed for {}", uuid, throwable);
            }
        });
        return 1;
    }

    private int status(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        UUID uuid = player.getUUID();
        Optional<LinkedPlayer> linkedPlayer = linkedPlayerStore.get(uuid).filter(value -> value.linked);
        int queued = eventQueue.countFor(uuid);
        if (linkedPlayer.isEmpty()) {
            source.sendSuccess(() -> Component.literal("ShinyDex status: not linked. Queued events: " + queued + "."), false);
            return 1;
        }

        String lastSync = linkedPlayer.get().lastSyncAt == null ? "never" : TimeUtil.format(linkedPlayer.get().lastSyncAt);
        source.sendSuccess(() -> Component.literal("ShinyDex status: linked. Last sync: " + lastSync + ". Queued events: " + queued + "."), false);
        return 1;
    }

    private int sync(CommandSourceStack source) throws CommandSyntaxException {
        source.getPlayerOrException();
        source.sendSuccess(() -> Component.literal("Full ShinyDex Pokédex sync is planned. Catch sync is available after linking."), false);
        return 1;
    }

    private int berries(CommandSourceStack source) throws CommandSyntaxException {
        if (!config.enabled) {
            source.sendFailure(Component.literal("ShinyDex Link is disabled on this server."));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        UUID uuid = player.getUUID();
        if (!linkedPlayerStore.isLinked(uuid) && !config.syncUnlinkedPlayers) {
            source.sendFailure(Component.literal("You are not linked yet. Use /shinydex link <code> from the Shiny Dex website."));
            return 0;
        }

        OptionalLong remaining = cooldowns.remainingSeconds(uuid, "berries", config.testCooldownSeconds);
        if (remaining.isPresent()) {
            source.sendFailure(Component.literal("Please wait " + remaining.getAsLong() + "s before scanning your berries again."));
            return 0;
        }

        Set<String> berries = BerryScanner.scan(player, logger);
        if (berries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No Cobblemon berries found in your inventory, ender chest, or containers."), false);
            return 1;
        }

        int count = berries.size();
        source.sendSuccess(() -> Component.literal("Scanning " + count + " berry type(s) to ShinyDex..."), false);
        BerryReportRequest request = new BerryReportRequest(
                config.serverId, uuid.toString(), player.getGameProfile().getName(), new ArrayList<>(berries));
        apiClient.sendBerries(request).whenComplete((response, throwable) -> runForPlayer(player, () -> {
            if (throwable != null || response == null || !response.success) {
                String error = throwable != null
                        ? throwable.getMessage()
                        : response == null ? "Empty ShinyDex response" : response.message;
                player.sendSystemMessage(Component.literal("ShinyDex berry sync failed: " + compact(error)));
                return;
            }
            player.sendSystemMessage(Component.literal(
                    "ShinyDex berries synced: " + response.added + " new, " + response.total + " total."));
        }));
        return 1;
    }

    private int test(CommandSourceStack source) throws CommandSyntaxException {
        if (!config.enabled) {
            source.sendFailure(Component.literal("ShinyDex Link is disabled on this server."));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        UUID uuid = player.getUUID();
        if (!linkedPlayerStore.isLinked(uuid) && !config.syncUnlinkedPlayers) {
            source.sendFailure(Component.literal("You are not linked yet. Use /shinydex link <code> from the Shiny Dex website."));
            return 0;
        }

        OptionalLong remaining = cooldowns.remainingSeconds(uuid, "test", config.testCooldownSeconds);
        if (remaining.isPresent()) {
            source.sendFailure(Component.literal("Please wait " + remaining.getAsLong() + "s before sending another ShinyDex test event."));
            return 0;
        }

        var event = CatchEventFactory.testEvent(config.serverId, uuid, player.getGameProfile().getName());
        source.sendSuccess(() -> Component.literal("Sending ShinyDex test event..."), false);
        apiClient.sendTestEvent(event).whenComplete((response, throwable) -> runForPlayer(player, () -> {
            if (throwable != null || response == null || !response.accepted()) {
                String error = throwable != null
                        ? throwable.getMessage()
                        : response == null ? "Empty ShinyDex response" : response.message;
                player.sendSystemMessage(Component.literal("ShinyDex test failed: " + compact(error)));
                return;
            }
            player.sendSystemMessage(Component.literal("ShinyDex test event sent."));
        }));
        return 1;
    }

    private void runForPlayer(ServerPlayer player, Runnable runnable) {
        if (player.getServer() == null) {
            return;
        }
        player.getServer().execute(runnable);
    }

    private String compact(String message) {
        if (message == null || message.isBlank()) {
            return "unknown error";
        }
        return message.length() > 140 ? message.substring(0, 140) + "..." : message;
    }
}
