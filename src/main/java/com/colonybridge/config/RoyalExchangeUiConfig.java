package com.colonybridge.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Locale;

/** Client-only visual settings. No menu, trade, or server state is read from this spec. */
public final class RoyalExchangeUiConfig {
    public static final ModConfigSpec CLIENT_SPEC;

    public enum Color {
        HEADER("frame", "headerBackground", 0xff172b27, "Dark strip behind the title and tabs."),
        PANEL("frame", "screenBackground", 0xffe9e4d7, "Main screen background."),
        PANEL_LIGHT("frame", "catalogBackground", 0xfff7f4eb, "Left catalog background and light cards."),
        PANEL_DARK("frame", "detailsBackground", 0xffede8dc, "Right details background."),
        CARD_EDGE("frame", "cardBorder", 0xffc8c6b9, "Borders around cards and input boxes."),
        GOLD("frame", "goldAccent", 0xffc79c54, "Gold dividers and card accents."),
        VALUE_BG("frame", "inputBackground", 0xfff7f4eb, "Search and quantity input background."),

        INK("text", "mainText", 0xff23352f, "Primary text on light backgrounds."),
        MUTED("text", "secondaryText", 0xff607068, "Descriptions and secondary text."),
        COPPER("text", "greenAccent", 0xff2b6855, "Green accents, quantity text, and scroll thumb."),
        BRASS("text", "sectionHeadings", 0xff8a6b36, "Selected good, quantity, and quote headings."),
        TITLE_TEXT("text", "titleText", 0xfff8efd9, "Royal Exchange title in the dark header."),
        GOOD_SELECTED_TEXT("text", "selectedGoodText", 0xff103d30, "Selected catalog row text."),
        SUCCESS_TEXT("text", "successText", 0xff1e6847, "Success and connected status text."),
        ERROR_TEXT("text", "errorText", 0xff8a2f29, "Errors and unavailable status text."),

        TAB_SELECTED_FILL("tabs", "selectedBackground", 0xff2b6855, "Selected Buy, Sell, or Contracts tab background."),
        TAB_NORMAL_FILL("tabs", "otherBackground", 0xff245446, "Other tab backgrounds."),
        TAB_HOVER_FILL("tabs", "hoverBackground", 0xff326d5a, "Other tab background while hovered."),
        TAB_SELECTED_EDGE("tabs", "selectedBorder", 0xffc79c54, "Border of the selected tab."),
        TAB_NORMAL_EDGE("tabs", "otherBorder", 0xff55766b, "Borders of the other tabs."),
        TAB_TEXT("tabs", "text", 0xfff8f5ea, "Text on all three tabs."),

        GOOD_SELECTED_BG("catalog", "selectedRowBackground", 0xffdce9df, "Selected item row background."),
        GOOD_DIVIDER("catalog", "rowDivider", 0xffe4e2d8, "Thin lines between item rows."),

        BUTTON_PRIMARY_HOVER("buttons", "primaryHoverBackground", 0xff357b62, "Primary action button while hovered."),
        BUTTON_SECONDARY_HOVER("buttons", "secondaryHoverBackground", 0xffe3eee5, "Secondary button while hovered."),
        BUTTON_DISABLED_BG("buttons", "disabledBackground", 0xffd8d9d1, "Disabled button background."),
        BUTTON_DISABLED_TEXT("buttons", "disabledText", 0xff7a837d, "Disabled button label."),
        BUTTON_LIGHT_TEXT("buttons", "lightText", 0xfff8f5ea, "Text on green action buttons.");

        private final String group;
        private final String key;
        private final int fallback;
        private final String description;
        private ModConfigSpec.ConfigValue<String> value;

        Color(String group, String key, int fallback, String description) {
            this.group = group;
            this.key = key;
            this.fallback = fallback;
            this.description = description;
        }
    }

    public enum Text {
        TITLE("header", "title", "Royal Exchange title."),
        BUY_TAB("header", "buyTabLabel", "Buy tab label."),
        SELL_TAB("header", "sellTabLabel", "Sell tab label."),
        CONTRACTS_TAB("header", "contractsTabLabel", "Contracts tab label."),
        SEARCH("catalog", "searchText", "Text typed in the search box."),
        GOOD_NAME("catalog", "itemNames", "Item names in the scrollable list."),
        SELECTED_GOOD_TITLE("trade", "selectedGoodHeading", "Selected good heading."),
        SELECTED_GOOD_BODY("trade", "selectedGoodDetails", "Selected good name."),
        QUANTITY_TITLE("trade", "quantityHeading", "Quantity heading and daily allowance."),
        QUANTITY_VALUE("trade", "quantityNumber", "Editable number in the quantity box."),
        QUANTITY_BUTTONS("trade", "quantityButtons", "Plus and minus labels beside the quantity box."),
        MARKET_TITLE("trade", "marketQuoteHeading", "Market quote heading."),
        MARKET_BODY("trade", "marketQuoteDetails", "Prices and quote messages."),
        ACTION_BUTTONS("trade", "actionButtonLabels", "Request Quote and Buy/Sell Goods labels."),
        BASKET_ROWS("basket", "basketRows", "Names and quantities in the nine-line sell basket."),
        BASKET_TITLE("basket", "basketHeading", "Sell basket heading and item count."),
        BASKET_QUOTE("basket", "basketQuote", "Basket quote heading and total payout."),
        BASKET_STATUS("basket", "basketStatus", "Basket status heading and message."),
        CONTRACT_GUIDE("contracts", "contractGuide", "Instructions on the left of Contracts."),
        CONTRACT_TITLE("contracts", "contractHeadings", "Royal Contract, Deliver, and Reward headings."),
        CONTRACT_BODY("contracts", "contractDetails", "Contract item, reward, and status text."),
        CONTRACT_BUTTONS("contracts", "contractButtonLabels", "Previous, Next, and Fulfill labels."),
        FOOTER_COUNT("footer", "goodsCount", "Goods count in the lower left."),
        FOOTER_STATUS("footer", "siteStatus", "Site connection status in the lower right.");

        private final String group;
        private final String key;
        private final String description;
        private ModConfigSpec.IntValue x;
        private ModConfigSpec.IntValue y;

        Text(String group, String key, String description) {
            this.group = group;
            this.key = key;
            this.description = description;
        }
    }

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        for (Color color : Color.values()) {
            builder.push("colors");
            builder.push(color.group);
            color.value = builder.comment(color.description, "Use a quoted #RRGGBB color, for example \"#2B6855\".")
                    .translation("config.colonybridge.royal_exchange.color." + color.key)
                    .define(color.key, String.format(Locale.ROOT, "#%06X", color.fallback & 0xffffff),
                            candidate -> candidate instanceof String text && text.matches("#[0-9a-fA-F]{6}"));
            builder.pop();
            builder.pop();
        }
        for (Text position : Text.values()) {
            builder.push("textPositions");
            builder.push(position.group);
            position.x = builder.comment(position.description + " Horizontal shift: positive moves right, negative moves left.")
                    .translation("config.colonybridge.royal_exchange.position." + position.key + "X")
                    .defineInRange(position.key + "X", 0, -12, 12);
            position.y = builder.comment(position.description + " Vertical shift: positive moves down, negative moves up.")
                    .translation("config.colonybridge.royal_exchange.position." + position.key + "Y")
                    .defineInRange(position.key + "Y", 0, -12, 12);
            builder.pop();
            builder.pop();
        }
        CLIENT_SPEC = builder.build();
    }

    private RoyalExchangeUiConfig() {
    }

    public static int color(Color color) {
        try {
            String hex = color.value.get();
            return 0xff000000 | Integer.parseInt(hex.substring(1), 16);
        } catch (RuntimeException invalid) {
            return color.fallback;
        }
    }

    public static int x(Text text) {
        return text.x.get();
    }

    public static int y(Text text) {
        return text.y.get();
    }
}
