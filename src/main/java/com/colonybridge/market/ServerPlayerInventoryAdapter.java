package com.colonybridge.market;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class ServerPlayerInventoryAdapter implements PlayerInventoryPort {
    private final ServerPlayer player;

    public ServerPlayerInventoryAdapter(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public String playerId() {
        return player.getUUID().toString();
    }

    @Override
    public boolean restrictedGameMode() {
        return player.isCreative() || player.isSpectator();
    }

    @Override
    public int countDiamonds() {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.DIAMOND)) count += stack.getCount();
        }
        return count;
    }

    @Override
    public int countPlainItems(String itemId) {
        Item item = VanillaItems.resolve(itemId);
        if (item == Items.AIR) return 0;
        ItemStack sample = new ItemStack(item);
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, sample)) count += stack.getCount();
        }
        return count;
    }

    @Override
    public PendingInventoryMutation prepare(InventoryMutation mutation) {
        Item removed = VanillaItems.resolve(mutation.removeItemId());
        Item granted = VanillaItems.resolve(mutation.grantItemId());
        if (removed == Items.AIR || granted == Items.AIR) {
            throw new IllegalArgumentException("Inventory mutation contains an unavailable item.");
        }
        return new Pending(List.of(new Removal(removed, mutation.removeCount())), granted, mutation.grantCount());
    }

    @Override
    public PendingInventoryMutation prepareBasket(BasketInventoryMutation mutation) {
        List<Removal> removals = new ArrayList<>();
        for (BasketLine line : mutation.lines()) {
            Item item = VanillaItems.resolve(line.itemId());
            if (item == Items.AIR) throw new IllegalArgumentException("Basket contains an unavailable item.");
            removals.add(new Removal(item, line.quantity()));
        }
        return new Pending(List.copyOf(removals), Items.DIAMOND, mutation.diamonds());
    }

    private record Removal(Item item, int count) { }

    private final class Pending implements PendingInventoryMutation {
        private final List<Removal> removals;
        private final Item granted;
        private final int grantCount;
        private final List<ItemStack> before = new ArrayList<>();
        private boolean applied;

        private Pending(List<Removal> removals, Item granted, int grantCount) {
            this.removals = removals;
            this.granted = granted;
            this.grantCount = grantCount;
        }

        @Override
        public void apply() {
            if (applied) throw new IllegalStateException("Inventory mutation was already applied.");
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                before.add(player.getInventory().getItem(slot).copy());
            }
            applied = true;
            for (Removal removal : removals) removePlain(removal.item(), removal.count());
            int remaining = grantCount;
            while (remaining > 0) {
                ItemStack stack = new ItemStack(granted, Math.min(remaining, granted.getDefaultMaxStackSize()));
                remaining -= stack.getCount();
                if (!player.getInventory().add(stack) || !stack.isEmpty()) throw new InventoryCapacityException();
            }
            player.getInventory().setChanged();
        }

        @Override
        public void commit() {
            if (!applied) throw new IllegalStateException("Inventory mutation was not applied.");
            before.clear();
        }

        @Override
        public void rollback() {
            if (!applied) return;
            for (int slot = 0; slot < before.size(); slot++) {
                player.getInventory().setItem(slot, before.get(slot));
            }
            player.getInventory().setChanged();
            before.clear();
            applied = false;
        }

        private void removePlain(Item item, int amount) {
            int remaining = amount;
            ItemStack sample = new ItemStack(item);
            for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                boolean matches = item == Items.DIAMOND ? stack.is(Items.DIAMOND)
                        : ItemStack.isSameItemSameComponents(stack, sample);
                if (!matches) continue;
                int removedCount = Math.min(remaining, stack.getCount());
                stack.shrink(removedCount);
                remaining -= removedCount;
            }
            if (remaining != 0) throw new IllegalStateException("Validated inventory changed before mutation.");
        }
    }

}
