package com.colonybridge.market.trader;

import com.colonybridge.config.RoyalExchangeUiConfig;
import com.colonybridge.config.RoyalExchangeUiConfig.Text;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.colonybridge.config.RoyalExchangeUiConfig.Color.*;
import static com.colonybridge.config.RoyalExchangeUiConfig.color;

public final class RoyalExchangeScreen extends AbstractContainerScreen<RoyalExchangeMenu> {
    public static final int SCREEN_WIDTH = RoyalExchangeLayout.WIDTH;
    public static final int SCREEN_HEIGHT = RoyalExchangeLayout.HEIGHT;
    private static final int RIGHT_TEXT_WIDTH = RoyalExchangeLayout.RIGHT_TEXT_WIDTH;
    private static final RoyalExchangeDraftCache<ClientKey, ScreenDraft> SCREEN_DRAFTS =
            new RoyalExchangeDraftCache<>(() -> System.nanoTime() / 1_000_000, 32);

    private final List<Item> filtered = new ArrayList<>();
    private final Map<Item, Integer> ownedCounts = new HashMap<>();
    private final Inventory playerInventory;
    private EditBox search;
    private EditBox quantityInput;
    private Button decrease;
    private Button increase;
    private Button quote;
    private Button buy;
    private Button buyMode;
    private Button sellMode;
    private Button contractsMode;
    private final List<Button> contractRows = new ArrayList<>();
    private final List<Button> catalogCells = new ArrayList<>();
    private final List<Button> basketCells = new ArrayList<>();
    private final List<Button> basketRemove = new ArrayList<>();
    private static final int NATIVE_PANEL = 0xffc6c6c6;
    private static final int NATIVE_INK = 0xff404040;
    private static final int NATIVE_MUTED = 0xff555555;
    private static final ResourceLocation TRADE_ARROW = ResourceLocation.withDefaultNamespace("container/villager/trade_arrow");
    private Button contractPrev;
    private Button contractNext;
    private Button completeContract;
    private Button basketBack;
    private int scroll;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;
    private ClientKey draftKey;
    private boolean draftRestored;
    private int lastQuantity;
    private boolean syncingQuantityInput;
    private boolean basketView;
    private boolean lastListSellMode;

    public RoyalExchangeScreen(RoyalExchangeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        playerInventory = inventory;
        imageWidth = SCREEN_WIDTH;
        imageHeight = SCREEN_HEIGHT;
    }

    @Override
    protected void init() {
        String previousSearch = search == null ? null : search.getValue();
        String previousQuantity = quantityInput == null ? null : quantityInput.getValue();
        boolean quantityWasFocused = quantityInput != null && quantityInput.isFocused();
        super.init();
        draggingScrollbar = false;
        search = addRenderableWidget(new EditBox(font, px(Text.SEARCH, leftPos + 14), py(Text.SEARCH, topPos + 40), 132, 20,
                Component.translatable("screen.colonybridge.royal_exchange.search")));
        search.setHint(Component.translatable(menu.sellMode()
                ? "screen.colonybridge.royal_exchange.basket.search_inventory"
                : "screen.colonybridge.royal_exchange.search"));
        search.setMaxLength(50);
        search.setBordered(true);
        search.setTextColor(0xffffffff);
        search.setTextColorUneditable(0xffa0a0a0);
        search.setTextShadow(false);
        search.setResponder(ignored -> rebuildItems());
        if (previousSearch != null) search.setValue(previousSearch);

        quantityInput = addRenderableWidget(new EditBox(font, px(Text.QUANTITY_VALUE, leftPos + 226),
                py(Text.QUANTITY_VALUE, topPos + 133), 30, 12,
                Component.translatable("screen.colonybridge.royal_exchange.text.quantity")));
        quantityInput.setBordered(false);
        quantityInput.setTextColor(NATIVE_INK);
        quantityInput.setTextShadow(false);
        quantityInput.setMaxLength(4);
        quantityInput.setFilter(value -> value.matches("[0-9]{0,4}"));
        quantityInput.setResponder(value -> {
            centerQuantityInput();
            if (syncingQuantityInput || value.isEmpty()) return;
            try {
                int quantity = Integer.parseInt(value);
                if (quantity >= 1 && quantity <= 1024 && quantity != menu.quantity()) {
                    send(RoyalExchangeMenu.SET_QUANTITY_BASE + quantity);
                }
            } catch (NumberFormatException ignored) {
                // An incomplete edit stays local until it becomes a valid quantity.
            }
        });
        setQuantityInputValue(menu.quantity());
        if (quantityWasFocused && previousQuantity != null) {
            syncingQuantityInput = true;
            quantityInput.setValue(previousQuantity);
            syncingQuantityInput = false;
        }
        quantityInput.setFocused(quantityWasFocused);
        lastQuantity = menu.quantity();

        decrease = addRenderableWidget(Button.builder(Component.literal("-"), ignored -> send(RoyalExchangeMenu.DECREASE_QUANTITY))
                .bounds(leftPos + 164, topPos + 128, 24, 20).build(ExchangeButton::new));
        increase = addRenderableWidget(Button.builder(Component.literal("+"), ignored -> send(RoyalExchangeMenu.INCREASE_QUANTITY))
                .bounds(leftPos + 270, topPos + 128, 24, 20).build(ExchangeButton::new));
        quote = addRenderableWidget(Button.builder(Component.literal(text("screen.colonybridge.royal_exchange.text.request_buy_quote")),
                ignored -> send(menu.sellMode() ? basketView ? RoyalExchangeMenu.REQUEST_BASKET_QUOTE
                        : RoyalExchangeMenu.ADD_TO_BASKET : RoyalExchangeMenu.REQUEST_QUOTE))
                .bounds(leftPos + 164, topPos + 152, 130, 20).build(ExchangeButton::new));
        buy = addRenderableWidget(Button.builder(Component.literal(text("screen.colonybridge.royal_exchange.text.buy_goods")),
                ignored -> {
                    if (menu.sellMode() && !basketView) {
                        search.setFocused(false);
                        basketView = true;
                    }
                    else send(menu.sellMode() ? RoyalExchangeMenu.COMPLETE_BASKET_TRADE : RoyalExchangeMenu.COMPLETE_TRADE);
                }).bounds(leftPos + 164, topPos + 196, 130, 20).build(ExchangeButton::new));

        buyMode = addRenderableWidget(Button.builder(Component.literal(text("screen.colonybridge.royal_exchange.text.buy")), ignored -> send(RoyalExchangeMenu.BUY_MODE))
                .bounds(leftPos + 10, topPos, 54, 24).build(ExchangeButton::new));
        sellMode = addRenderableWidget(Button.builder(Component.literal(text("screen.colonybridge.royal_exchange.text.sell")), ignored -> send(RoyalExchangeMenu.SELL_MODE))
                .bounds(leftPos + 66, topPos, 54, 24).build(ExchangeButton::new));
        contractsMode = addRenderableWidget(Button.builder(Component.literal(text("screen.colonybridge.royal_exchange.text.contracts")), ignored -> send(RoyalExchangeMenu.CONTRACTS_VIEW))
                .bounds(leftPos + 122, topPos, 90, 24).build(ExchangeButton::new));

        catalogCells.clear();
        for (int cell = 0; cell < RoyalExchangeLayout.VISIBLE_ITEMS; cell++) {
            final int selectedCell = cell;
            int x = RoyalExchangeLayout.ITEM_LIST_LEFT + cell % RoyalExchangeLayout.CATALOG_COLUMNS * 18;
            int y = RoyalExchangeLayout.ITEM_LIST_TOP + cell / RoyalExchangeLayout.CATALOG_COLUMNS * 18;
            catalogCells.add(addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                int index = RoyalExchangePresentationModel.catalogIndex(scroll, selectedCell);
                if (index < filtered.size()) send(RoyalExchangeMenu.SELECT_ITEM_BASE
                        + BuiltInRegistries.ITEM.getId(filtered.get(index)));
            }).bounds(leftPos + x, topPos + y, 18, 18).build(ExchangeButton::new)));
        }
        basketCells.clear();
        basketRemove.clear();
        for (int cell = 0; cell < RoyalExchangeMenu.BASKET_LIMIT; cell++) {
            final int selectedCell = cell;
            int x = 14 + cell % 3 * 42;
            int y = 65 + cell / 3 * 44;
            // Register removal before selection so its overlapping hitbox wins mouse focus.
            Button remove = Button.builder(Component.literal("×"),
                    ignored -> send(RoyalExchangeMenu.REMOVE_BASKET_BASE + selectedCell))
                    .bounds(leftPos + x + 24, topPos + y + 3, 11, 12).build(ExchangeButton::new);
            basketRemove.add(remove);
            addRenderableWidget(remove);
            basketCells.add(addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                send(RoyalExchangeMenu.SELECT_BASKET_BASE + selectedCell);
                basketView = false;
            }).bounds(leftPos + x, topPos + y, 38, 42).build(ExchangeButton::new)));
        }
        contractRows.clear();
        for (int row = 0; row < RoyalExchangeMenu.CONTRACT_PAGE_SIZE; row++) {
            final int selectedRow = row;
            contractRows.add(addRenderableWidget(Button.builder(Component.empty(),
                    ignored -> send(RoyalExchangeMenu.SELECT_CONTRACT_ROW_BASE + selectedRow))
                    .bounds(leftPos + 14, topPos + 56 + row * 27, 132, 25).build(ExchangeButton::new)));
        }
        contractPrev = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> send(RoyalExchangeMenu.PREV_CONTRACT))
                .bounds(leftPos + 14, topPos + 196, 20, 20).build(ExchangeButton::new));
        contractNext = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> send(RoyalExchangeMenu.NEXT_CONTRACT))
                .bounds(leftPos + 126, topPos + 196, 20, 20).build(ExchangeButton::new));
        completeContract = addRenderableWidget(Button.builder(Component.literal(text("screen.colonybridge.royal_exchange.text.fulfill_contract")), ignored -> send(RoyalExchangeMenu.COMPLETE_CONTRACT))
                .bounds(leftPos + 164, topPos + 196, 130, 20).build(ExchangeButton::new));
        completeContract.setTooltip(Tooltip.create(Component.translatable("screen.colonybridge.royal_exchange.native.validation")));
        basketBack = addRenderableWidget(Button.builder(Component.literal("← " + text("screen.colonybridge.royal_exchange.basket.inventory")),
                ignored -> basketView = false).bounds(leftPos + 16, topPos + 41, 128, 19).build(ExchangeButton::new));
        rebuildItems();
        lastListSellMode = menu.sellMode();
        updateControls();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (!draftRestored && minecraft != null && minecraft.level != null && minecraft.player != null) {
            BlockPos pos = menu.blockPosClient();
            if (pos != null) {
                draftKey = new ClientKey(minecraft.level, minecraft.player.getUUID(), pos);
                SCREEN_DRAFTS.get(draftKey).ifPresent(draft -> {
                    if (search.getValue().isEmpty()) search.setValue(draft.search());
                    scroll = RoyalExchangePresentationModel.clampScroll(draft.scroll(), filtered.size());
                });
                draftRestored = true;
            }
        }
        if (quantityInput != null && !quantityInput.isFocused() && menu.quantity() != lastQuantity) {
            setQuantityInputValue(menu.quantity());
        }
        lastQuantity = menu.quantity();
        centerQuantityInput();
        if (menu.sellMode() != lastListSellMode) {
            basketView = false;
            search.setHint(Component.translatable(menu.sellMode()
                    ? "screen.colonybridge.royal_exchange.basket.search_inventory"
                    : "screen.colonybridge.royal_exchange.search"));
            lastListSellMode = menu.sellMode();
            rebuildItems();
        } else if (menu.sellMode()) {
            rebuildItems();
        }
    }

    @Override
    public void removed() {
        commitQuantity();
        if (draftKey != null && search != null) {
            SCREEN_DRAFTS.put(draftKey, new ScreenDraft(search.getValue(), scroll));
        }
        super.removed();
    }

    private void centerQuantityInput() {
        if (quantityInput == null) return;
        int textWidth = Math.max(6, font.width(quantityInput.getValue()));
        int inputWidth = Math.max(12, textWidth + 4);
        int cursor = quantityInput.getCursorPosition();
        quantityInput.setX(px(Text.QUANTITY_VALUE, leftPos + 229 - inputWidth / 2));
        quantityInput.setWidth(inputWidth);
        // EditBox scrolls before its responder runs, using the previous width.
        // Recalculate its visible start after resizing so leading digits stay visible.
        quantityInput.setCursorPosition(0);
        quantityInput.setCursorPosition(cursor);
    }

    private void setQuantityInputValue(int quantity) {
        syncingQuantityInput = true;
        quantityInput.setValue(Integer.toString(quantity));
        syncingQuantityInput = false;
        centerQuantityInput();
    }

    private void commitQuantity() {
        if (quantityInput == null || !quantityInput.isFocused()) return;
        String value = quantityInput.getValue();
        int quantity;
        try { quantity = Integer.parseInt(value); }
        catch (NumberFormatException invalid) { quantity = menu.quantity(); }
        quantity = Math.max(1, Math.min(1024, quantity));
        setQuantityInputValue(quantity);
        quantityInput.setFocused(false);
        if (quantity != menu.quantity()) send(RoyalExchangeMenu.SET_QUANTITY_BASE + quantity);
        centerQuantityInput();
    }

    private void rebuildItems() {
        String needle = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        ownedCounts.clear();
        if (menu.sellMode()) {
            for (int slot = 0; slot < playerInventory.getContainerSize(); slot++) {
                ItemStack stack = playerInventory.getItem(slot);
                if (stack.isEmpty()) continue;
                Item item = stack.getItem();
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                if (key != null && com.colonybridge.market.MarketItemIds.isTradable(key.toString())
                        && ItemStack.isSameItemSameComponents(stack, new ItemStack(item))) {
                    ownedCounts.merge(item, stack.getCount(), Integer::sum);
                }
            }
            ownedCounts.keySet().stream().filter(item -> matchesSearch(item, needle))
                    .sorted(Comparator.comparing(item -> item.getDefaultInstance().getHoverName().getString()))
                    .forEach(filtered::add);
        } else {
            BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR && matchesSearch(item, needle))
                    .sorted(Comparator.comparing(item -> item.getDefaultInstance().getHoverName().getString()))
                    .forEach(filtered::add);
        }
        scroll = RoyalExchangePresentationModel.clampScroll(scroll, filtered.size());
        if (filtered.size() <= RoyalExchangeLayout.VISIBLE_ITEMS) draggingScrollbar = false;
    }

    private boolean matchesSearch(Item item, String needle) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        return key != null && com.colonybridge.market.MarketItemIds.isTradable(key.toString())
                && (needle.isEmpty() || key.getPath().contains(needle)
                || item.getDefaultInstance().getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle));
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (quantityInput != null && quantityInput.visible && quantityInput.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                setQuantityInputValue(menu.quantity());
                quantityInput.setFocused(false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitQuantity();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                commitQuantity();
                quantityInput.setFocused(true);
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            return quantityInput.keyPressed(keyCode, scanCode, modifiers);
        }
        if (search != null && search.visible && search.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                search.setFocused(false);
                return true;
            }
            search.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (menu.sellMode() && basketView && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            basketView = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (quantityInput != null && quantityInput.visible && quantityInput.isFocused()) return quantityInput.charTyped(codePoint, modifiers);
        if (search != null && search.visible && search.isFocused()) return search.charTyped(codePoint, modifiers);
        return super.charTyped(codePoint, modifiers);
    }

    public int screenWidthPixels() { return width; }
    public int screenHeightPixels() { return height; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!menu.contractView() && !basketView && button == 0
                && mouseX >= leftPos + 194 && mouseX < leftPos + 264
                && mouseY >= topPos + 128 && mouseY < topPos + 148) {
            search.setFocused(false);
            quantityInput.setFocused(true);
            setFocused(quantityInput);
            quantityInput.setValue("");
            return true;
        }
        commitQuantity();
        if (button == 0 && canScrollItems()
                && mouseX >= leftPos + RoyalExchangeLayout.SCROLLBAR_LEFT - 1
                && mouseX < leftPos + RoyalExchangeLayout.SCROLLBAR_RIGHT + 1
                && mouseY >= topPos + RoyalExchangeLayout.SCROLLBAR_TOP
                && mouseY < topPos + RoyalExchangeLayout.SCROLLBAR_BOTTOM) {
            search.setFocused(false);
            RoyalExchangePresentationModel.Scrollbar bar = RoyalExchangePresentationModel.scrollbar(filtered.size(), scroll);
            double pointerY = mouseY - topPos;
            if (pointerY >= bar.thumbTop() && pointerY < bar.thumbTop() + bar.thumbHeight()) {
                scrollbarGrabOffset = bar.grabOffsetAt(pointerY);
            } else {
                scrollbarGrabOffset = bar.thumbHeight() / 2.0;
                scroll = bar.scrollAt(pointerY, scrollbarGrabOffset);
            }
            draggingScrollbar = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean canScrollItems() {
        return !menu.contractView() && !basketView && filtered.size() > RoyalExchangeLayout.VISIBLE_ITEMS;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar && button == 0) {
            if (canScrollItems()) {
                scroll = RoyalExchangePresentationModel.scrollbar(filtered.size(), scroll)
                        .scrollAt(mouseY - topPos, scrollbarGrabOffset);
                return true;
            }
            draggingScrollbar = false;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (draggingScrollbar) return true;
        if (menu.contractView() && menu.contractCount() > 1 && scrollY != 0
                && mouseX >= leftPos + 14 && mouseX < leftPos + 146
                && mouseY >= topPos + 56 && mouseY < topPos + 191) {
            send(scrollY > 0 ? RoyalExchangeMenu.PREV_CONTRACT : RoyalExchangeMenu.NEXT_CONTRACT);
            return true;
        }
        if (!menu.contractView() && !basketView && mouseX >= leftPos + 10 && mouseX < leftPos + 151
                && mouseY >= topPos + 63 && mouseY < topPos + 207) {
            scroll = RoyalExchangePresentationModel.clampScroll(scroll - (int) Math.signum(scrollY), filtered.size());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderButtons(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
        Item hovered = hoveredItem(mouseX, mouseY);
        if (hovered != Items.AIR) {
            List<Component> lines = new ArrayList<>(net.minecraft.client.gui.screens.Screen.getTooltipFromItem(minecraft, new ItemStack(hovered)));
            if (menu.sellMode()) lines.add(Component.translatable("screen.colonybridge.royal_exchange.native.owned",
                    ownedCounts.getOrDefault(hovered, 0)));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
        if (menu.contractView()) {
            for (int row = 0; row < contractRows.size(); row++) {
                Button button = contractRows.get(row);
                if (button.visible && button.isMouseOver(mouseX, mouseY)) {
                    graphics.renderTooltip(font, button.getMessage(), mouseX, mouseY);
                }
            }
            if (mouseX >= leftPos + 164 && mouseX < leftPos + 300 && mouseY >= topPos + 56 && mouseY < topPos + 79
                    && menu.contractItem() != Items.AIR) {
                graphics.renderTooltip(font, new ItemStack(menu.contractItem()), mouseX, mouseY);
            } else if (mouseX >= leftPos + 172 && mouseX < leftPos + 190 && mouseY >= topPos + 96 && mouseY < topPos + 114) {
                graphics.renderTooltip(font, new ItemStack(menu.contractItem()), mouseX, mouseY);
            } else if (mouseX >= leftPos + 264 && mouseX < leftPos + 282 && mouseY >= topPos + 96 && mouseY < topPos + 114) {
                graphics.renderTooltip(font, new ItemStack(Items.DIAMOND), mouseX, mouseY);
            } else if (menu.contractState() == 2 && mouseX >= leftPos + 164 && mouseX < leftPos + 300
                    && mouseY >= topPos + 170 && mouseY < topPos + 194) {
                graphics.renderTooltip(font, menu.resultMessage(), mouseX, mouseY);
            }
        }
        if (menu.sellMode() && basketView && !menu.contractView()) {
            for (int cell = 0; cell < menu.basketCount(); cell++) {
                if (basketRemove.get(cell).isMouseOver(mouseX, mouseY)) {
                    graphics.renderTooltip(font, Component.translatable("screen.colonybridge.royal_exchange.native.remove",
                            menu.basketItem(cell).getDefaultInstance().getHoverName()), mouseX, mouseY);
                } else if (basketCells.get(cell).isMouseOver(mouseX, mouseY)) {
                    graphics.renderComponentTooltip(font, basketTooltip(cell), mouseX, mouseY);
                }
            }
            if (mouseX >= leftPos + 164 && mouseX < leftPos + 300 && mouseY >= topPos + 175 && mouseY < topPos + 195)
                graphics.renderTooltip(font, Component.literal(basketStatus()), mouseX, mouseY);
        }
        if (!menu.contractView() && !basketView) {
            if (menu.selectedItemClient() != Items.AIR && mouseX >= leftPos + 164 && mouseX < leftPos + 300
                    && mouseY >= topPos + 44 && mouseY < topPos + 66)
                graphics.renderTooltip(font, new ItemStack(menu.selectedItemClient()), mouseX, mouseY);
        }
        if (menu.sellMode() && !menu.contractView() && mouseX >= leftPos + 14 && mouseX < leftPos + 146
                && mouseY >= topPos + 199 && mouseY < topPos + 218)
            graphics.renderTooltip(font, Component.translatable("screen.colonybridge.royal_exchange.native.daily_help",
                    menu.sellAllowance()), mouseX, mouseY);
        if (!menu.contractView() && !menu.sellMode() && menu.quoteState() == 4
                && mouseX >= leftPos + 158 && mouseX < leftPos + 300
                && mouseY >= topPos + 175 && mouseY < topPos + 195) {
            graphics.renderTooltip(font, menu.resultMessage(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        updateControls();
        drawNativePanel(graphics, 0, 22, imageWidth, imageHeight - 22);
        if (menu.contractView()) {
            renderContractGuide(graphics, mouseX, mouseY);
            renderContract(graphics);
        } else if (menu.sellMode() && basketView) {
            renderBasketView(graphics, mouseX, mouseY);
        } else {
            renderItemList(graphics, mouseX, mouseY);
            renderSelectedItem(graphics);
            if (menu.sellMode()) renderSellBrowseSummary(graphics);
            else renderQuote(graphics);
        }
    }

    // Fixed logical pixels, with the same highlight direction as vanilla containers.
    private void drawNativePanel(GuiGraphics graphics, int x, int y, int width, int height) {
        int left = leftPos + x;
        int top = topPos + y;
        graphics.fill(left + 2, top, left + width - 2, top + height, 0xff000000);
        graphics.fill(left, top + 2, left + width, top + height - 2, 0xff000000);
        graphics.fill(left + 2, top + 1, left + width - 2, top + height - 1, 0xff555555);
        graphics.fill(left + 1, top + 2, left + width - 1, top + height - 2, 0xff555555);
        graphics.fill(left + 2, top + 2, left + width - 2, top + height - 2, NATIVE_PANEL);
        graphics.fill(left + 3, top + 2, left + width - 3, top + 3, 0xffffffff);
        graphics.fill(left + 2, top + 3, left + 3, top + height - 3, 0xffffffff);
        graphics.fill(left + 3, top + height - 3, left + width - 3, top + height - 2, 0xff777777);
        graphics.fill(left + width - 3, top + 3, left + width - 2, top + height - 3, 0xff777777);
    }

    private void drawItemWell(GuiGraphics graphics, int x, int y, Item item) {
        drawPreviewWell(graphics, x, y, 18, 18);
        graphics.renderItem(new ItemStack(item), leftPos + x + 1, topPos + y + 1);
    }

    private void drawPreviewWell(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(leftPos + x, topPos + y, leftPos + x + width, topPos + y + height, 0xff373737);
        graphics.fill(leftPos + x + 1, topPos + y + 1, leftPos + x + width, topPos + y + height, 0xffffffff);
        graphics.fill(leftPos + x + 1, topPos + y + 1, leftPos + x + width - 1, topPos + y + height - 1, 0xff8b8b8b);
    }

    private void drawSelectionMark(GuiGraphics graphics, Text position, int y, String label) {
        int height = Math.max(8, Math.min(2, font.split(Component.literal(label), RIGHT_TEXT_WIDTH).size()) * 10 - 2);
        int x = px(position, leftPos + 158);
        int top = py(position, topPos + y);
        graphics.fill(x, top, x + 2, top + height, color(COPPER));
    }

    private void renderDailyAllowance(GuiGraphics graphics) {
        graphics.renderItem(new ItemStack(Items.DIAMOND), leftPos + 14, topPos + 200);
        String remaining = Component.translatable("screen.colonybridge.royal_exchange.native.daily_left",
                String.format(Locale.ROOT, "%,d", menu.sellAllowance())).getString();
        graphics.drawString(font, fit(remaining, 110), leftPos + 36, topPos + 204, NATIVE_MUTED, false);
    }

    private void drawTradeArrow(GuiGraphics graphics, int x, int y) {
        graphics.setColor(0.45f, 0.45f, 0.45f, 1.0f);
        graphics.blitSprite(TRADE_ARROW, leftPos + x, topPos + y, 10, 9);
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        drawButton(graphics, buyMode, mouseX, mouseY, !menu.contractView() && !menu.sellMode(), false);
        drawButton(graphics, sellMode, mouseX, mouseY, !menu.contractView() && menu.sellMode(), false);
        drawButton(graphics, contractsMode, mouseX, mouseY, menu.contractView(), false);
        drawButton(graphics, decrease, mouseX, mouseY, false, false);
        drawButton(graphics, increase, mouseX, mouseY, false, false);
        drawButton(graphics, quote, mouseX, mouseY, false, true);
        drawButton(graphics, buy, mouseX, mouseY, false, true);
        drawButton(graphics, contractPrev, mouseX, mouseY, false, false);
        drawButton(graphics, contractNext, mouseX, mouseY, false, false);
        drawButton(graphics, completeContract, mouseX, mouseY, false, true);
        drawButton(graphics, basketBack, mouseX, mouseY, false, false);
    }

    private void drawButton(GuiGraphics graphics, Button button, int mouseX, int mouseY,
                            boolean selected, boolean primary) {
        if (button == null || !button.visible) return;
        boolean hovered = mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
        boolean tab = button == buyMode || button == sellMode || button == contractsMode;
        if (tab) {
            drawNativePanel(graphics, button.getX() - leftPos, button.getY() - topPos,
                    button.getWidth(), button.getHeight() + (selected ? 2 : 0));
            if (selected) {
                graphics.fill(button.getX() + 3, topPos + 22,
                        button.getX() + button.getWidth() - 3, topPos + 26, NATIVE_PANEL);
                graphics.fill(button.getX() + 5, button.getY() + 3,
                        button.getX() + button.getWidth() - 5, button.getY() + 5, color(COPPER));
            }
            if (button.isFocused() || hovered && button.active) {
                graphics.renderOutline(button.getX() + 1, button.getY() + 1,
                        button.getWidth() - 2, button.getHeight() - 2, 0xffffffff);
            }
            drawCenteredText(graphics, fit(button.getMessage().getString(), button.getWidth() - 8),
                    px(button == buyMode ? Text.BUY_TAB : button == sellMode ? Text.SELL_TAB : Text.CONTRACTS_TAB,
                            button.getX() + button.getWidth() / 2),
                    py(button == buyMode ? Text.BUY_TAB : button == sellMode ? Text.SELL_TAB : Text.CONTRACTS_TAB,
                            button.getY() + 9), NATIVE_INK, false);
            return;
        }
        drawNativeButton(graphics, button, hovered);
        boolean readyAction = button == buy && (menu.sellMode() ? basketView && menu.basketState() == 2 : menu.quoteState() == 2)
                || button == completeContract && menu.contractState() == 0
                && contractOwnedCount() >= menu.contractQuantity();
        if (primary && button.active && readyAction) graphics.fill(button.getX() + 2, button.getY() + 3,
                button.getX() + 4, button.getY() + button.getHeight() - 3, color(COPPER));
        Text position = button == contractPrev || button == contractNext || button == completeContract
                ? Text.CONTRACT_BUTTONS : button == decrease || button == increase ? Text.QUANTITY_BUTTONS : Text.ACTION_BUTTONS;
        drawCenteredText(graphics, fit(button.getMessage().getString(), button.getWidth() - 6),
                px(position, button.getX() + button.getWidth() / 2),
                py(position, button.getY() + (button.getHeight() - 8) / 2),
                button.active ? 0xffffffff : 0xffa0a0a0, button.active);
    }

    private void drawNativeButton(GuiGraphics graphics, Button button, boolean hovered) {
        ResourceLocation sprite = ResourceLocation.withDefaultNamespace(!button.active ? "widget/button_disabled"
                : hovered || button.isFocused() ? "widget/button_highlighted" : "widget/button");
        graphics.blitSprite(sprite, button.getX(), button.getY(), button.getWidth(), button.getHeight());
    }

    private void renderItemList(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int cell = 0; cell < catalogCells.size(); cell++) {
            Button button = catalogCells.get(cell);
            int index = RoyalExchangePresentationModel.catalogIndex(scroll, cell);
            Item item = index < filtered.size() ? filtered.get(index) : Items.AIR;
            drawItemWell(graphics, button.getX() - leftPos, button.getY() - topPos, item);
            if (button.active && (button.isMouseOver(mouseX, mouseY) || button.isFocused()))
                graphics.renderOutline(button.getX(), button.getY(), 18, 18, 0xffffffff);
            if (item == menu.selectedItemClient() && item != Items.AIR)
                graphics.renderOutline(button.getX(), button.getY(), 18, 18, color(COPPER));
            if (menu.sellMode() && item != Items.AIR) {
                int owned = ownedCounts.getOrDefault(item, 0);
                String amount = owned > 99 ? "99+" : Integer.toString(owned);
                // Use vanilla's decoration depth so item models cannot cover their counts.
                graphics.renderItemDecorations(font, new ItemStack(item), button.getX() + 1, button.getY() + 1, amount);
            }
        }
        RoyalExchangePresentationModel.Scrollbar bar = RoyalExchangePresentationModel.scrollbar(filtered.size(), scroll);
        graphics.fill(leftPos + RoyalExchangeLayout.SCROLLBAR_LEFT, topPos + bar.trackTop(),
                leftPos + RoyalExchangeLayout.SCROLLBAR_RIGHT, topPos + bar.trackBottom(), 0xff373737);
        graphics.blitSprite(ResourceLocation.withDefaultNamespace(bar.scrollable()
                        ? "container/villager/scroller" : "container/villager/scroller_disabled"),
                leftPos + RoyalExchangeLayout.SCROLLBAR_LEFT, topPos + bar.thumbTop(), 6, 27);
        if (filtered.isEmpty()) {
            String reason = !search.getValue().isBlank() ? "no_matches" : menu.sellMode() ? "no_eligible" : "no_catalog";
            drawWrapped(graphics, text("screen.colonybridge.royal_exchange.native." + reason),
                    leftPos + 14, topPos + 182, 126, NATIVE_MUTED, 3);
        } else {
            boolean selected = menu.selectedItemClient() != Items.AIR;
            String detail = menu.sellMode() && selected
                    ? Component.translatable("screen.colonybridge.royal_exchange.native.owned",
                            ownedCounts.getOrDefault(menu.selectedItemClient(), 0)).getString()
                    : selected ? Component.translatable("screen.colonybridge.royal_exchange.native.selected",
                            menu.selectedItemClient().getDefaultInstance().getHoverName()).getString()
                    : text("screen.colonybridge.royal_exchange.native.click_select");
            graphics.drawString(font, fit(detail, 126), leftPos + 14, topPos + 182, NATIVE_MUTED, false);
        }
        if (menu.sellMode()) renderDailyAllowance(graphics);
    }

    private void renderSelectedItem(GuiGraphics graphics) {
        Item selected = menu.selectedItemClient();
        String name = selected == Items.AIR ? text("screen.colonybridge.royal_exchange.native.nothing_selected")
                : selected.getDefaultInstance().getHoverName().getString();
        if (selected != Items.AIR) drawSelectionMark(graphics, Text.SELECTED_GOOD_BODY, 46, name);
        drawWrapped(graphics, name, px(Text.SELECTED_GOOD_BODY, leftPos + 164), py(Text.SELECTED_GOOD_BODY, topPos + 46),
                RIGHT_TEXT_WIDTH, NATIVE_INK, 2);
        if (selected == Items.AIR) graphics.drawString(font, text("screen.colonybridge.royal_exchange.native.choose_grid"),
                leftPos + 164, topPos + 59, NATIVE_MUTED, false);
        graphics.drawString(font, text("screen.colonybridge.royal_exchange.native." + (menu.sellMode() ? "deliver" : "pay")),
                leftPos + 164, topPos + 70, NATIVE_MUTED, false);
        graphics.drawString(font, text("screen.colonybridge.royal_exchange.native.receive"), leftPos + 258, topPos + 70, NATIVE_MUTED, false);
        drawItemWell(graphics, 172, 83, menu.sellMode() ? selected : Items.DIAMOND);
        drawItemWell(graphics, 264, 83, menu.sellMode() ? Items.DIAMOND : selected);
        drawTradeArrow(graphics, 224, 87);
        String price = !menu.sellMode() && menu.quoteState() == 2 ? "×" + menu.diamondCost() : "—";
        String quantity = selected == Items.AIR ? "—" : "×" + menu.quantity();
        drawCenteredText(graphics, menu.sellMode() ? quantity : price, leftPos + 181, topPos + 103, NATIVE_INK, false);
        drawCenteredText(graphics, menu.sellMode() ? price : quantity, leftPos + 273, topPos + 103, NATIVE_INK, false);
        graphics.drawString(font, text("screen.colonybridge.royal_exchange.text.quantity"),
                px(Text.QUANTITY_TITLE, leftPos + 164), py(Text.QUANTITY_TITLE, topPos + 116), NATIVE_MUTED, false);
        graphics.fill(leftPos + 194, topPos + 128, leftPos + 264, topPos + 148, 0xff373737);
        graphics.fill(leftPos + 195, topPos + 129, leftPos + 264, topPos + 148, 0xffffffff);
        graphics.fill(leftPos + 195, topPos + 129, leftPos + 263, topPos + 147, 0xffb0b0b0);
        if (quantityInput.isFocused()) graphics.renderOutline(leftPos + 194, topPos + 128, 70, 20, 0xffffffff);
    }

    private void renderSellBrowseSummary(GuiGraphics graphics) {
        int owned = ownedCounts.getOrDefault(menu.selectedItemClient(), 0);
        String summary = Component.translatable("screen.colonybridge.royal_exchange.native.basket_count",
                menu.basketCount(), RoyalExchangeMenu.BASKET_LIMIT).getString();
        boolean warning = false;
        if (!quote.active && menu.selectedItemClient() != Items.AIR && owned == 0) {
            summary = text("screen.colonybridge.royal_exchange.native.no_owned");
            warning = true;
        } else if (!quote.active && menu.basketCount() == RoyalExchangeMenu.BASKET_LIMIT) {
            summary = text("screen.colonybridge.royal_exchange.native.basket_full");
            warning = true;
        } else if (menu.selectedItemClient() != Items.AIR && menu.quantity() > owned) {
            summary = Component.translatable("screen.colonybridge.royal_exchange.native.insufficient_owned",
                    owned, menu.quantity()).getString();
            warning = true;
        } else if (menu.selectedItemClient() == Items.AIR && menu.basketCount() == 0) {
            summary = text("screen.colonybridge.royal_exchange.native.basket_empty");
        }
        drawWrapped(graphics, summary, px(Text.BASKET_STATUS, leftPos + 164), py(Text.BASKET_STATUS, topPos + 176),
                RIGHT_TEXT_WIDTH, warning ? color(ERROR_TEXT) : NATIVE_MUTED, 2);
    }

    private List<Component> basketTooltip(int cell) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(menu.basketQuantity(cell) + " ").append(menu.basketItem(cell).getDefaultInstance().getHoverName()));
        if (menu.basketNeedsLargerQuantity(cell)) lines.add(menu.basketQuantityWarning(cell));
        else if (menu.basketPayout(cell) > 0) lines.add(Component.literal(text("screen.colonybridge.royal_exchange.basket.line_quote")
                + " " + menu.basketPayout(cell)));
        lines.add(Component.translatable("screen.colonybridge.royal_exchange.native.order_help"));
        return lines;
    }

    private String basketStatus() {
        return switch (menu.basketState()) {
            case 1 -> text("screen.colonybridge.royal_exchange.basket.weighing");
            case 2 -> text("screen.colonybridge.royal_exchange.basket.ready") + " · " + menu.basketSeconds() + "s";
            case 3 -> text("screen.colonybridge.royal_exchange.basket.sold");
            case 4 -> menu.basketError() == 14 && menu.basketQuantityWarningCount() > 1
                    ? text("screen.colonybridge.royal_exchange.basket.quantities_required")
                    : menu.basketError() == 14 && menu.basketQuantityWarningCount() == 1
                    ? menu.basketQuantityWarning().getString()
                    : text("screen.colonybridge.royal_exchange.result." + menu.basketError());
            case 5 -> text("screen.colonybridge.royal_exchange.basket.expired");
            default -> text(menu.basketCount() == 0 ? "screen.colonybridge.royal_exchange.native.basket_empty"
                    : "screen.colonybridge.royal_exchange.basket.review");
        };
    }

    private void renderBasketView(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int cell = 0; cell < basketCells.size(); cell++) {
            Button button = basketCells.get(cell);
            boolean present = cell < menu.basketCount();
            int x = button.getX() - leftPos;
            int y = button.getY() - topPos;
            if (!present) {
                drawPreviewWell(graphics, x, y, 38, 42);
                continue;
            }
            drawNativeButton(graphics, button, button.isMouseOver(mouseX, mouseY));
            drawItemWell(graphics, x + 3, y + 3, menu.basketItem(cell));
            boolean needsQuantity = menu.basketNeedsLargerQuantity(cell);
            String quantity = "×" + menu.basketQuantity(cell);
            if (needsQuantity) graphics.renderOutline(button.getX(), button.getY(), 38, 42, color(ERROR_TEXT));
            graphics.drawString(font, quantity, button.getX() + 2, button.getY() + 24,
                    needsQuantity ? 0xffffaaaa : 0xffffffff, true);
            if (needsQuantity) graphics.drawString(font, "≥" + menu.basketRequiredQuantity(cell),
                    button.getX() + 2, button.getY() + 33, 0xffffaaaa, true);
            Button remove = basketRemove.get(cell);
            drawNativeButton(graphics, remove, remove.isMouseOver(mouseX, mouseY));
            drawCenteredText(graphics, "×", remove.getX() + 5, remove.getY() + 2, 0xffffaaaa, true);
        }
        renderDailyAllowance(graphics);
        graphics.drawString(font, text("screen.colonybridge.royal_exchange.basket.title"),
                px(Text.BASKET_TITLE, leftPos + 164), py(Text.BASKET_TITLE, topPos + 46), NATIVE_INK, false);
        graphics.drawString(font, menu.basketCount() + " / " + RoyalExchangeMenu.BASKET_LIMIT + " "
                + text("screen.colonybridge.royal_exchange.basket.goods"), leftPos + 164, topPos + 62, NATIVE_MUTED, false);
        graphics.drawString(font, text("screen.colonybridge.royal_exchange.native.receive"), leftPos + 164, topPos + 82, NATIVE_MUTED, false);
        drawItemWell(graphics, 164, 96, Items.DIAMOND);
        String total = menu.basketState() == 2 && menu.basketTotal() > 0
                ? menu.basketTotal() + diamondSuffix(menu.basketTotal()) : "—";
        graphics.drawString(font, fit(total, 104), leftPos + 188, topPos + 101, NATIVE_INK, false);
        drawWrapped(graphics, text("screen.colonybridge.royal_exchange.basket.items_stay"),
                leftPos + 164, topPos + 124, RIGHT_TEXT_WIDTH, NATIVE_MUTED, 2);
        drawWrapped(graphics, basketStatus(), px(Text.BASKET_STATUS, leftPos + 164), py(Text.BASKET_STATUS, topPos + 176),
                RIGHT_TEXT_WIDTH, menu.basketState() == 4 || menu.basketState() == 5 ? color(ERROR_TEXT)
                        : menu.basketState() == 2 || menu.basketState() == 3 ? color(COPPER) : NATIVE_MUTED, 2);
    }

    private void renderQuote(GuiGraphics graphics) {
        int state = menu.quoteState();
        if (state == 2) {
            graphics.drawString(font, fit(exchangeRateText(), RIGHT_TEXT_WIDTH), leftPos + 164, topPos + 175, NATIVE_INK, false);
            String condition = text("screen.colonybridge.royal_exchange.text." + (menu.condition() > 0
                    ? "demand_elevated" : menu.condition() < 0 ? "favorable_supply" : "steady_market"));
            graphics.drawString(font, fit(condition + " · " + menu.expiresInSeconds() + "s", RIGHT_TEXT_WIDTH),
                    leftPos + 164, topPos + 185, NATIVE_MUTED, false);
            return;
        }
        String status = switch (state) {
            case 1 -> text("screen.colonybridge.royal_exchange.text.valuation_in_progress");
            case 3 -> text("screen.colonybridge.royal_exchange.text.trade_completed");
            case 4 -> menu.resultMessage().getString();
            case 5 -> text("screen.colonybridge.royal_exchange.text.quote_expired");
            default -> text("screen.colonybridge.royal_exchange.native." + (menu.selectedItemClient() == Items.AIR ? "select_quote" : "request_quote"));
        };
        drawWrapped(graphics, status, px(Text.MARKET_BODY, leftPos + 164), py(Text.MARKET_BODY, topPos + 176),
                RIGHT_TEXT_WIDTH, state == 4 || state == 5 ? color(ERROR_TEXT) : state == 3 ? color(COPPER) : NATIVE_MUTED, 2);
    }

    private String exchangeRateText() {
        int quantity = menu.quantity();
        int diamonds = Math.max(1, menu.diamondCost());
        double itemsPerDiamond = (double) quantity / diamonds;
        if (itemsPerDiamond >= 1) {
            String amount = Math.abs(itemsPerDiamond - Math.rint(itemsPerDiamond)) < 0.001
                    ? Integer.toString((int) Math.rint(itemsPerDiamond))
                    : String.format(Locale.ROOT, "%.1f", itemsPerDiamond);
            return menu.sellMode() ? "1 diamond per " + amount : "1 diamond buys " + amount;
        }
        double diamondsPerItem = (double) diamonds / Math.max(1, quantity);
        String amount = Math.abs(diamondsPerItem - Math.rint(diamondsPerItem)) < 0.001
                ? Integer.toString((int) Math.rint(diamondsPerItem))
                : String.format(Locale.ROOT, "%.1f", diamondsPerItem);
        return menu.sellMode() ? amount + " per item" : amount + " each";
    }

    private static String diamondSuffix(int amount) { return amount == 1 ? " diamond" : " diamonds"; }

    private void renderContract(GuiGraphics graphics) {
        if (menu.contractCount() == 0 || menu.contractItem() == Items.AIR) {
            drawWrapped(graphics, text("screen.colonybridge.royal_exchange.text.no_commissions_posted"),
                    leftPos + 164, topPos + 60, RIGHT_TEXT_WIDTH, NATIVE_MUTED, 3);
            if (menu.contractState() == 1) drawWrapped(graphics,
                    text("screen.colonybridge.royal_exchange.text.fulfilled"), leftPos + 164, topPos + 170,
                    RIGHT_TEXT_WIDTH, color(COPPER), 2);
            return;
        }
        Item item = menu.contractItem();
        String name = item.getDefaultInstance().getHoverName().getString();
        drawSelectionMark(graphics, Text.CONTRACT_BODY, 58, name);
        drawWrapped(graphics, name,
                px(Text.CONTRACT_BODY, leftPos + 164), py(Text.CONTRACT_BODY, topPos + 58),
                RIGHT_TEXT_WIDTH, NATIVE_INK, 2);
        graphics.drawString(font, text("screen.colonybridge.royal_exchange.native.deliver"),
                leftPos + 164, topPos + 82, NATIVE_MUTED, false);
        graphics.drawString(font, text("screen.colonybridge.royal_exchange.native.reward"),
                leftPos + 258, topPos + 82, NATIVE_MUTED, false);
        drawItemWell(graphics, 172, 96, item);
        drawItemWell(graphics, 264, 96, Items.DIAMOND);
        drawTradeArrow(graphics, 224, 100);
        drawCenteredText(graphics, "×" + menu.contractQuantity(), leftPos + 181, topPos + 117, NATIVE_INK, false);
        drawCenteredText(graphics, "×" + menu.contractReward(), leftPos + 273, topPos + 117, NATIVE_INK, false);
        drawWrapped(graphics, text("screen.colonybridge.royal_exchange.native.carry"),
                leftPos + 164, topPos + 134, RIGHT_TEXT_WIDTH, NATIVE_MUTED, 2);
        graphics.drawString(font, fit(compactTime(menu.contractExpiresInSeconds()), RIGHT_TEXT_WIDTH),
                leftPos + 164, topPos + 157, menu.contractExpiresInSeconds() > 0 ? NATIVE_MUTED : color(ERROR_TEXT), false);
        int owned = contractOwnedCount();
        String readiness = owned < 0 ? text("screen.colonybridge.royal_exchange.native.inventory_unknown")
                : owned < menu.contractQuantity()
                ? Component.translatable("screen.colonybridge.royal_exchange.native.missing",
                        menu.contractQuantity() - owned, owned, menu.contractQuantity()).getString()
                : text("screen.colonybridge.royal_exchange.native.ready");
        String status = menu.contractState() == 1 ? text("screen.colonybridge.royal_exchange.text.fulfilled")
                : menu.contractState() == 2 ? menu.resultMessage().getString()
                : menu.contractExpiresInSeconds() <= 0 ? text("screen.colonybridge.royal_exchange.native.expired")
                : readiness;
        drawWrapped(graphics, status, px(Text.CONTRACT_BODY, leftPos + 164), py(Text.CONTRACT_BODY, topPos + 172),
                RIGHT_TEXT_WIDTH, menu.contractState() == 2 || menu.contractExpiresInSeconds() <= 0
                        || menu.contractState() != 1 && owned >= 0 && owned < menu.contractQuantity()
                        ? color(ERROR_TEXT) : menu.contractState() != 1 && owned < 0
                        ? NATIVE_MUTED : color(COPPER), 2);
    }

    private int contractOwnedCount() {
        if (minecraft == null || minecraft.player == null) return -1;
        int count = 0;
        ItemStack sample = new ItemStack(menu.contractItem());
        for (int slot = 0; slot < playerInventory.getContainerSize(); slot++) {
            ItemStack stack = playerInventory.getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, sample)) count += stack.getCount();
        }
        return count;
    }

    private void renderContractGuide(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, text("screen.colonybridge.royal_exchange.text.contracts") + " (" + menu.contractCount() + ")",
                leftPos + 14, topPos + 45, NATIVE_INK, false);
        for (int row = 0; row < contractRows.size(); row++) {
            Button button = contractRows.get(row);
            Item item = menu.contractRowItem(row);
            if (menu.contractPageStart() + row >= menu.contractCount() || item == Items.AIR) continue;
            boolean selected = menu.contractPageStart() + row == menu.contractIndex();
            drawNativeButton(graphics, button, button.isMouseOver(mouseX, mouseY));
            if (selected) {
                graphics.renderOutline(button.getX() + 1, button.getY() + 1,
                        button.getWidth() - 2, button.getHeight() - 2, 0xffffffff);
                graphics.fill(button.getX() + 1, button.getY() + 1,
                        button.getX() + 3, button.getY() + button.getHeight() - 1, color(COPPER));
            }
            drawItemWell(graphics, 19, button.getY() - topPos + 3, item);
            graphics.drawString(font, fit(item.getDefaultInstance().getHoverName().getString(), 102),
                    leftPos + 41, button.getY() + 4, 0xffffffff, true);
            String exchange = menu.contractRowQuantity(row) + " → " + menu.contractRowReward(row)
                    + diamondSuffix(menu.contractRowReward(row));
            graphics.drawString(font, fit(exchange, 102), leftPos + 41, button.getY() + 14, 0xffdddddd, true);
        }
        if (menu.contractCount() > 1) drawCenteredText(graphics,
                (menu.contractIndex() + 1) + " / " + menu.contractCount(), leftPos + 80, topPos + 202, NATIVE_INK, false);
    }

    private void updateControls() {
        RoyalExchangePresentationModel.Controls controls = RoyalExchangePresentationModel.controls(
                menu.contractView(), menu.sellMode(), menu.quoteState(), menu.contractCount());
        boolean browse = controls.tradeControlsVisible() && (!menu.sellMode() || !basketView);
        search.visible = browse;
        quantityInput.visible = browse;
        for (int cell = 0; cell < catalogCells.size(); cell++) {
            Button button = catalogCells.get(cell);
            int index = RoyalExchangePresentationModel.catalogIndex(scroll, cell);
            button.visible = browse && index < filtered.size();
            button.active = button.visible;
            if (button.visible) button.setMessage(filtered.get(index).getDefaultInstance().getHoverName());
        }
        boolean order = menu.sellMode() && basketView && !menu.contractView();
        for (int cell = 0; cell < basketCells.size(); cell++) {
            Button button = basketCells.get(cell);
            button.visible = order;
            button.active = order && cell < menu.basketCount();
            basketRemove.get(cell).visible = button.active;
            basketRemove.get(cell).active = button.active;
            if (button.active) {
                button.setMessage(Component.literal(menu.basketQuantity(cell) + " ").append(menu.basketItem(cell).getDefaultInstance().getHoverName()));
                basketRemove.get(cell).setMessage(Component.translatable("screen.colonybridge.royal_exchange.native.remove",
                        menu.basketItem(cell).getDefaultInstance().getHoverName()));
            }
        }
        decrease.visible = browse;
        increase.visible = browse;
        quote.visible = controls.tradeControlsVisible();
        if (menu.sellMode()) {
            quote.setMessage(Component.translatable(basketView
                    ? "screen.colonybridge.royal_exchange.basket.request_quote"
                    : "screen.colonybridge.royal_exchange.basket.add"));
            quote.active = basketView ? menu.basketCount() > 0 && menu.basketState() != 1
                    : ownedCounts.getOrDefault(menu.selectedItemClient(), 0) > 0
                    && (menu.basketCount() < RoyalExchangeMenu.BASKET_LIMIT
                    || menu.basketContains(menu.selectedItemClient()));
            buy.visible = controls.tradeControlsVisible();
            buy.active = basketView ? menu.basketState() == 2 : menu.basketCount() > 0;
            buy.setMessage(Component.translatable(basketView
                    ? "screen.colonybridge.royal_exchange.basket.sell"
                    : "screen.colonybridge.royal_exchange.basket.review_button"));
        } else {
            quote.setMessage(Component.literal(controls.quoteLabel()));
            quote.active = menu.selectedItemClient() != Items.AIR && menu.quoteState() != 1;
            buy.visible = controls.tradeControlsVisible();
            buy.active = controls.completeTradeVisible();
            buy.setMessage(Component.literal(controls.tradeLabel()));
        }
        basketBack.visible = menu.sellMode() && basketView && !menu.contractView();
        buyMode.active = controls.buyModeActive();
        sellMode.active = controls.sellModeActive();
        contractsMode.active = controls.contractsModeActive();
        contractPrev.visible = controls.contractNavigationVisible();
        contractNext.visible = controls.contractNavigationVisible();
        completeContract.visible = menu.contractView();
        completeContract.active = controls.completeContractVisible() && menu.contractItem() != Items.AIR
                && menu.contractExpiresInSeconds() > 0;
        for (int row = 0; row < contractRows.size(); row++) {
            Button button = contractRows.get(row);
            Item item = menu.contractRowItem(row);
            button.visible = menu.contractView() && menu.contractPageStart() + row < menu.contractCount()
                    && item != Items.AIR;
            button.active = button.visible;
            button.setMessage(Component.translatable("screen.colonybridge.royal_exchange.native.row",
                    menu.contractRowQuantity(row), item.getDefaultInstance().getHoverName(), menu.contractRowReward(row)));
        }
        if (menu.contractView()) {
            search.setFocused(false);
            quantityInput.setFocused(false);
        }
    }

    private static String compactTime(int seconds) {
        if (seconds >= 3600) return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m left";
        if (seconds > 0 && seconds < 60) return "<1m left";
        return Math.max(0, seconds / 60) + "m left";
    }

    private static String text(String key) { return Component.translatable(key).getString(); }

    private static int px(Text position, int x) { return x + RoyalExchangeUiConfig.x(position); }

    private static int py(Text position, int y) { return y + RoyalExchangeUiConfig.y(position); }

    private String fit(String text, int maxWidth) {
        return RoyalExchangePresentationModel.fit(text, maxWidth, font::width,
                width -> font.plainSubstrByWidth(text, width));
    }

    private void drawCenteredText(GuiGraphics graphics, String text, int centerX, int y, int color, boolean shadow) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, shadow);
    }

    private void drawWrapped(GuiGraphics graphics, String text, int x, int y, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), width);
        int count = Math.min(maxLines, lines.size());
        for (int line = 0; line < count; line++) {
            graphics.drawString(font, lines.get(line), x, y + line * 10, color, false);
        }
    }

    private Item hoveredItem(int mouseX, int mouseY) {
        if (menu.contractView() || menu.sellMode() && basketView) return Items.AIR;
        int index = RoyalExchangePresentationModel.catalogIndexAt(scroll, mouseX - leftPos, mouseY - topPos);
        return index >= 0 && index < filtered.size() ? filtered.get(index) : Items.AIR;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int titleX = px(Text.TITLE, 10);
        int titleY = py(Text.TITLE, 29);
        drawCrown(graphics, titleX, titleY + 1);
        graphics.drawString(font, text("screen.colonybridge.royal_exchange.text.royal_exchange"),
                titleX + 13, titleY, NATIVE_INK, false);
        if (!menu.contractView()) graphics.drawString(font, (menu.sellMode() && basketView ? menu.basketCount() : filtered.size())
                        + (menu.sellMode() && basketView ? " " + text("screen.colonybridge.royal_exchange.basket.goods")
                        : filtered.size() == 1 ? text("screen.colonybridge.royal_exchange.text.good") : text("screen.colonybridge.royal_exchange.text.goods")),
                px(Text.FOOTER_COUNT, 14), py(Text.FOOTER_COUNT, 222), color(MUTED), false);
        String status = switch (menu.onlineMarketState()) {
            case 2 -> text("screen.colonybridge.royal_exchange.native.connected");
            case 3 -> text("screen.colonybridge.royal_exchange.native.cached");
            case 1 -> text("screen.colonybridge.royal_exchange.native.connecting");
            case -1 -> text("screen.colonybridge.royal_exchange.native.offline");
            default -> text("screen.colonybridge.royal_exchange.native.local_prices");
        };
        int statusColor = menu.onlineMarketState() == 2 ? color(COPPER)
                : menu.onlineMarketState() == 1 || menu.onlineMarketState() == 3 ? color(BRASS)
                : menu.onlineMarketState() == -1 ? NATIVE_MUTED : color(ERROR_TEXT);
        graphics.drawString(font, status, px(Text.FOOTER_STATUS, 300 - font.width(status)),
                py(Text.FOOTER_STATUS, 222), statusColor, false);
    }

    // A nine-pixel crown uses the same flat logical pixels as the container bevel.
    private void drawCrown(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 1, y + 3, NATIVE_INK);
        graphics.fill(x + 4, y, x + 5, y + 3, NATIVE_INK);
        graphics.fill(x + 8, y, x + 9, y + 3, NATIVE_INK);
        graphics.fill(x + 1, y + 2, x + 8, y + 3, NATIVE_INK);
        graphics.fill(x + 1, y + 3, x + 2, y + 5, NATIVE_INK);
        graphics.fill(x + 7, y + 3, x + 8, y + 5, NATIVE_INK);
        graphics.fill(x + 1, y + 5, x + 8, y + 6, NATIVE_INK);
        graphics.fill(x + 2, y + 4, x + 7, y + 5, color(COPPER));
    }

    private record ClientKey(Object level, UUID player, BlockPos pos) {
    }

    private record ScreenDraft(String search, int scroll) {
    }

    private static final class ExchangeButton extends Button {
        private ExchangeButton(Button.Builder builder) {
            super(builder);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // RoyalExchangeScreen draws the button once with its own palette.
        }
    }
}
