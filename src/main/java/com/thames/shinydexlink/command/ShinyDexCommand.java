package com.thames.shinydexlink.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.thames.shinydexlink.api.ShinyDexApiClient;
import com.thames.shinydexlink.api.dto.LinkRequest;
import com.thames.shinydexlink.api.dto.UnlinkRequest;
import com.thames.shinydexlink.config.ShinyDexConfig;
import com.thames.shinydexlink.data.EventQueue;
import com.thames.shinydexlink.data.LinkedPlayer;
import com.thames.shinydexlink.data.LinkedPlayerStore;
import com.thames.shinydexlink.sync.CatchEventFactory;
import com.thames.shinydexlink.util.CooldownManager;
import com.thames.shinydexlink.util.TimeUtil;
import java.io.IOException;
import java.util.Optional;
import java.util.OptionalLong;
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
    private final Logger logger;
    private final CooldownManager cooldowns = new CooldownManager();

    public ShinyDexCommand(
            ShinyDexConfig config,
            ShinyDexApiClient apiClient,
            LinkedPlayerStore linkedPlayerStore,
            EventQueue eventQueue,
            Logger logger
    ) {
        this.config = config;
        this.apiClient = apiClient;
        this.linkedPlayerStore = linkedPlayerStore;
        this.eventQueue = eventQueue;
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
                .then(literal("test").executes(context -> test(context.getSource()))));
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
