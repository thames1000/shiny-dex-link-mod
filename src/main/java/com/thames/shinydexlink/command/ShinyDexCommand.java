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
import com.thames.shinydexlink.sync.HuntProgressSync;
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
    private final HuntProgressSync huntProgressSync;
    private final Logger logger;
    private final CooldownManager cooldowns = new CooldownManager();

    public ShinyDexCommand(
            ShinyDexConfig config,
            ShinyDexApiClient apiClient,
            LinkedPlayerStore linkedPlayerStore,
            EventQueue eventQueue,
            HuntManager huntManager,
            HuntProgressSync huntProgressSync,
            Logger logger
    ) {
        this.config = config;
        this.apiClient = apiClient;
        this.linkedPlayerStore = linkedPlayerStore;
        this.eventQueue = eventQueue;
        this.huntManager = huntManager;
        this.huntProgressSync = huntProgressSync;
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
                        .then(literal("status").executes(context -> huntStatus(context.getSource())))
                        .then(literal("surprise").executes(context -> huntSurprise(context.getSource())))
                        .then(literal("random").executes(context -> huntSurprise(context.getSource())))
                        .then(literal("stop")
                                .executes(context -> huntStopAll(context.getSource()))
                                .then(huntTargetArgs(this::huntStopOne)))
                        .then(literal("reset")
                                .executes(context -> huntResetAll(context.getSource()))
                                .then(huntTargetArgs(this::huntResetOne)))
                        .then(literal("eggs")
                                .executes(context -> huntToggleEggsAll(context.getSource()))
                                .then(huntTargetArgs(this::huntToggleEggsOne)))
                        .then(literal("encounters")
                                .executes(context -> huntToggleEncountersAll(context.getSource()))
                                .then(huntTargetArgs(this::huntToggleEncountersOne)))
                        .then(argument("species", StringArgumentType.word())
                                .executes(context -> huntSet(context.getSource(), StringArgumentType.getString(context, "species"), null))
                                .then(argument("form", StringArgumentType.word())
                                        .executes(context -> huntSet(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "species"),
                                                StringArgumentType.getString(context, "form")))))));
    }

    /** Shared {@code <species> [form]} argument subtree for per-hunt commands. */
    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> huntTargetArgs(
            HuntTargetAction action) {
        return argument("species", StringArgumentType.word())
                .executes(ctx -> action.run(ctx.getSource(), StringArgumentType.getString(ctx, "species"), null))
                .then(argument("form", StringArgumentType.word())
                        .executes(ctx -> action.run(ctx.getSource(),
                                StringArgumentType.getString(ctx, "species"),
                                StringArgumentType.getString(ctx, "form"))));
    }

    @FunctionalInterface
    private interface HuntTargetAction {
        int run(CommandSourceStack source, String species, String form) throws CommandSyntaxException;
    }

    private int huntSet(CommandSourceStack source, String species, String form) throws CommandSyntaxException {
        ServerPlayer player = requireHunt(source);
        if (player == null) {
            return 0;
        }
        UUID uuid = player.getUUID();
        String key = HuntState.makeKey(species, form);
        boolean exists = huntManager.find(uuid, key).isPresent();
        if (!exists && huntManager.size(uuid) >= huntManager.maxHunts()) {
            source.sendFailure(Component.literal("You already have " + huntManager.maxHunts()
                    + " active hunts (the max). Stop one with /shinydex hunt stop <species> first."));
            return 0;
        }
        HuntState state = huntManager.setTarget(
                uuid, species, SpeciesLookup.displayName(species), form,
                config.huntCountEncounters, config.huntCountEggHatches);
        if (state == null) {
            source.sendFailure(Component.literal("Usage: /shinydex hunt <species> [form]"));
            return 0;
        }
        HuntNetworking.sendUpdate(player, huntManager.getAll(uuid));
        // Resume from any progress saved on the website for this species/form.
        huntProgressSync.fetchAndSeed(player, state.species, state.form);
        String label = form == null ? state.displayName : state.displayName + " (" + form + ")";
        int active = huntManager.size(uuid);
        source.sendSuccess(() -> Component.literal("Now hunting " + label + ". Counter reset to 0. ("
                + active + "/" + huntManager.maxHunts() + " hunts active)"), false);
        return 1;
    }

    /**
     * Starts a hunt for a random Cobblemon species — a "surprise" hunt. The pick avoids species the
     * player is already hunting when possible. If the player doesn't fancy the chosen target they can
     * back out with {@code /shinydex hunt stop <species>} (or {@code stop} to clear every hunt).
     */
    private int huntSurprise(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = requireHunt(source);
        if (player == null) {
            return 0;
        }
        UUID uuid = player.getUUID();
        if (huntManager.size(uuid) >= huntManager.maxHunts()) {
            source.sendFailure(Component.literal("You already have " + huntManager.maxHunts()
                    + " active hunts (the max). Stop one with /shinydex hunt stop <species> first."));
            return 0;
        }
        String species = pickSurpriseSpecies(uuid);
        if (species == null) {
            source.sendFailure(Component.literal("No Cobblemon species are available to pick a surprise hunt from."));
            return 0;
        }
        HuntState state = huntManager.setTarget(
                uuid, species, SpeciesLookup.displayName(species), null,
                config.huntCountEncounters, config.huntCountEggHatches);
        if (state == null) {
            source.sendFailure(Component.literal("Could not start a surprise hunt right now."));
            return 0;
        }
        HuntNetworking.sendUpdate(player, huntManager.getAll(uuid));
        // Resume from any progress saved on the website for this species.
        huntProgressSync.fetchAndSeed(player, state.species, state.form);
        int active = huntManager.size(uuid);
        source.sendSuccess(() -> Component.literal("Surprise! Now hunting " + state.displayName + ". ("
                + active + "/" + huntManager.maxHunts() + " hunts active). Not the one for you? "
                + "Stop it with /shinydex hunt stop " + state.species + "."), false);
        return 1;
    }

    /**
     * Picks a random species id for a surprise hunt, preferring one the player isn't already hunting.
     * Returns null only when Cobblemon's species registry is unavailable (e.g. the mod is absent).
     */
    private String pickSurpriseSpecies(UUID uuid) {
        java.util.List<String> ids = SpeciesLookup.allIds();
        if (ids.isEmpty()) {
            return null;
        }
        Set<String> active = new java.util.HashSet<>();
        for (HuntState state : huntManager.getAll(uuid)) {
            active.add(state.species);
        }
        java.util.List<String> candidates = new ArrayList<>();
        for (String id : ids) {
            if (!active.contains(id)) {
                candidates.add(id);
            }
        }
        if (candidates.isEmpty()) {
            candidates = ids;
        }
        return candidates.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private int huntStatus(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = requireHunt(source);
        if (player == null) {
            return 0;
        }
        java.util.List<HuntState> hunts = huntManager.getAll(player.getUUID());
        if (hunts.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active hunts. Start one with /shinydex hunt <species>."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal(hunts.size() + " active hunt(s):"), false);
        for (HuntState state : hunts) {
            String label = state.form == null ? state.displayName : state.displayName + " (" + state.form + ")";
            source.sendSuccess(() -> Component.literal(
                    " - " + label + ": " + state.total() + " ("
                            + state.encounters + " enc, " + state.eggs + " eggs, " + state.manual + " manual). "
                            + "auto enc=" + state.countEncounters + ", eggs=" + state.countEggs + "."), false);
        }
        return 1;
    }

    private int huntStopAll(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = requireHunt(source);
        if (player == null) {
            return 0;
        }
        int removed = huntManager.stopAll(player.getUUID());
        HuntNetworking.sendUpdate(player, huntManager.getAll(player.getUUID()));
        if (removed == 0) {
            source.sendSuccess(() -> Component.literal("No active hunts to stop."), false);
        } else {
            source.sendSuccess(() -> Component.literal("Stopped " + removed + " hunt(s)."), false);
        }
        return 1;
    }

    private int huntStopOne(CommandSourceStack source, String species, String form) throws CommandSyntaxException {
        ServerPlayer player = requireHunt(source);
        if (player == null) {
            return 0;
        }
        HuntState target = resolveHunt(source, player.getUUID(), species, form);
        if (target == null) {
            return 0;
        }
        huntManager.stop(player.getUUID(), target.key());
        HuntNetworking.sendUpdate(player, huntManager.getAll(player.getUUID()));
        source.sendSuccess(() -> Component.literal("Stopped hunt for " + target.displayName + "."), false);
        return 1;
    }

    private int huntResetAll(CommandSourceStack source) throws CommandSyntaxException {
        return huntApplyAll(source, "reset", state -> huntManager.reset(state.uuid, state.key));
    }

    private int huntResetOne(CommandSourceStack source, String species, String form) throws CommandSyntaxException {
        return huntApplyOne(source, species, form, "reset",
                (uuid, key) -> huntManager.reset(uuid, key),
                state -> state.displayName + " counter reset to 0.");
    }

    private int huntToggleEggsAll(CommandSourceStack source) throws CommandSyntaxException {
        return huntApplyAll(source, "egg auto-count", state -> huntManager.toggleEggs(state.uuid, state.key));
    }

    private int huntToggleEggsOne(CommandSourceStack source, String species, String form) throws CommandSyntaxException {
        return huntApplyOne(source, species, form, "egg auto-count",
                (uuid, key) -> huntManager.toggleEggs(uuid, key),
                state -> state.displayName + " auto-count egg hatches: " + state.countEggs + ".");
    }

    private int huntToggleEncountersAll(CommandSourceStack source) throws CommandSyntaxException {
        return huntApplyAll(source, "encounter auto-count", state -> huntManager.toggleEncounters(state.uuid, state.key));
    }

    private int huntToggleEncountersOne(CommandSourceStack source, String species, String form) throws CommandSyntaxException {
        return huntApplyOne(source, species, form, "encounter auto-count",
                (uuid, key) -> huntManager.toggleEncounters(uuid, key),
                state -> state.displayName + " auto-count encounters: " + state.countEncounters + ".");
    }

    /** Pairs a UUID with a hunt key so an all-hunts action can name each hunt as it iterates. */
    private record HuntRef(UUID uuid, String key) {
    }

    private int huntApplyAll(CommandSourceStack source, String label,
            java.util.function.Function<HuntRef, Optional<HuntState>> action) throws CommandSyntaxException {
        ServerPlayer player = requireHunt(source);
        if (player == null) {
            return 0;
        }
        UUID uuid = player.getUUID();
        java.util.List<HuntState> hunts = huntManager.getAll(uuid);
        if (hunts.isEmpty()) {
            source.sendFailure(Component.literal("No active hunts."));
            return 0;
        }
        for (HuntState state : hunts) {
            action.apply(new HuntRef(uuid, state.key()));
        }
        HuntNetworking.sendUpdate(player, huntManager.getAll(uuid));
        source.sendSuccess(() -> Component.literal("Applied " + label + " change to all " + hunts.size() + " hunt(s)."), false);
        return 1;
    }

    private int huntApplyOne(CommandSourceStack source, String species, String form, String label,
            java.util.function.BiFunction<UUID, String, Optional<HuntState>> action,
            java.util.function.Function<HuntState, String> message) throws CommandSyntaxException {
        ServerPlayer player = requireHunt(source);
        if (player == null) {
            return 0;
        }
        UUID uuid = player.getUUID();
        HuntState target = resolveHunt(source, uuid, species, form);
        if (target == null) {
            return 0;
        }
        HuntState updated = action.apply(uuid, target.key()).orElse(null);
        if (updated == null) {
            source.sendFailure(Component.literal("Could not change " + label + " for that hunt."));
            return 0;
        }
        HuntNetworking.sendUpdate(player, huntManager.getAll(uuid));
        source.sendSuccess(() -> Component.literal(message.apply(updated)), false);
        return 1;
    }

    /**
     * Finds the hunt a per-hunt command targets. Matches the exact species/form key, or — when no
     * form was given and the species has exactly one hunt — that hunt. Reports the failure itself.
     */
    private HuntState resolveHunt(CommandSourceStack source, UUID uuid, String species, String form) {
        String key = HuntState.makeKey(species, form);
        HuntState exact = huntManager.find(uuid, key).orElse(null);
        if (exact != null) {
            return exact;
        }
        if (form == null) {
            String normalizedSpecies = species == null ? "" : species.toLowerCase(java.util.Locale.ROOT).trim();
            java.util.List<HuntState> ofSpecies = huntManager.getAll(uuid).stream()
                    .filter(state -> normalizedSpecies.equals(state.species))
                    .toList();
            if (ofSpecies.size() == 1) {
                return ofSpecies.get(0);
            }
            if (ofSpecies.size() > 1) {
                source.sendFailure(Component.literal("Several " + species
                        + " hunts are active. Add the form, e.g. /shinydex hunt stop " + species + " <form>."));
                return null;
            }
        }
        source.sendFailure(Component.literal("No active hunt for " + species
                + (form == null ? "" : " (" + form + ")") + "."));
        return null;
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
