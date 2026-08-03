package com.colonybridge.notification;

import com.colonybridge.export.ExportStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ExportNotificationService {
    private final List<ExportNotification> notifications = new ArrayList<>();

    public void tick(MinecraftServer server) {
        Iterator<ExportNotification> iterator = notifications.iterator();
        while (iterator.hasNext()) {
            ExportNotification display = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(display.playerId);
            if (player == null) {
                iterator.remove();
                continue;
            }
            if (display.export.isCompletedExceptionally()) {
                player.displayClientMessage(Component.literal("Colony Bridge export failed - check the log")
                        .withStyle(ChatFormatting.RED), false);
                iterator.remove();
                continue;
            }
            if (display.export.isDone()) {
                ExportStatus status = display.export.getNow(null);
                int colonies = status == null ? 0 : status.coloniesDetected();
                String colonyLabel = colonies == 1 ? "colony" : "colonies";
                player.displayClientMessage(Component.literal("Colony Bridge snapshot saved (" + colonies + " "
                        + colonyLabel + ")").withStyle(ChatFormatting.GREEN), false);
                player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.65F, 1.2F);
                iterator.remove();
            }
        }
    }

    public void queueAll(MinecraftServer server, CompletableFuture<ExportStatus> export) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) queue(player, export);
    }

    public void queue(ServerPlayer player, CompletableFuture<ExportStatus> export) {
        notifications.removeIf(display -> display.playerId.equals(player.getUUID()));
        notifications.add(new ExportNotification(player.getUUID(), export));
    }

    public void clear() {
        notifications.clear();
    }

    private record ExportNotification(UUID playerId, CompletableFuture<ExportStatus> export) {
    }
}
