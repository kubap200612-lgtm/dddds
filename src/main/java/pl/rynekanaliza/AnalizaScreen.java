package pl.rynekanaliza;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Panel Rynek Analiza: zakładka "Przedmioty" (lista z BK-Rynek, marża do wpisania, sugestia z analizy,
 * start/stop) i zakładka "Ustawienia" (klawisze, domyślna marża, podatek itd.).
 *
 * Lista jest stronicowana (bez przewijania), a wszystkie widżety budujemy od nowa w init().
 */
public class AnalizaScreen extends Screen {
    private enum Tab { ITEMS, SETTINGS }

    private static final int ROW_H = 22;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int GREEN = 0xFF55FF55;
    private static final int YELLOW = 0xFFFFFF55;

    private final AnalizaConfig cfg;
    private final List<BkBridge.BkTarget> all = new ArrayList<>();
    private List<BkBridge.BkTarget> filtered = new ArrayList<>();
    private String loadError = "";

    private Tab tab = Tab.ITEMS;
    private int page = 0;
    private String filter = "";
    private KeyBinding listening;
    private boolean bgDone;

    // układ (liczony w init)
    private int panelX, panelW, rowsPerPage, tableTop;
    private int colName, colNameW, colPrice, colMargin, colMarginW, colSug, colUse, colUseW;
    private final List<BkBridge.BkTarget> pageRows = new ArrayList<>();

    public AnalizaScreen(AnalizaConfig cfg) {
        super(Text.literal("Rynek Analiza"));
        this.cfg = cfg;
        try {
            all.addAll(BkBridge.targets("", cfg));
        } catch (Throwable t) {
            loadError = t.toString();
        }
        applyFilter();
    }

    private void applyFilter() {
        String f = filter.trim().toLowerCase(Locale.ROOT);
        filtered = new ArrayList<>();
        for (BkBridge.BkTarget t : all) {
            if (f.isEmpty() || t.displayName().toLowerCase(Locale.ROOT).contains(f)) filtered.add(t);
        }
    }

    private void rebuild() {
        clearChildren();
        init();
    }

    // ---------- budowanie widżetów ----------
    @Override
    protected void init() {
        panelW = Math.min(540, this.width - 8);
        panelX = (this.width - panelW) / 2;

        ButtonWidget tabItems = ButtonWidget.builder(Text.literal("Przedmioty"), b -> { tab = Tab.ITEMS; listening = null; rebuild(); })
                .dimensions(panelX, 22, 100, 20).build();
        tabItems.active = tab != Tab.ITEMS;
        addDrawableChild(tabItems);
        ButtonWidget tabSettings = ButtonWidget.builder(Text.literal("Ustawienia"), b -> { tab = Tab.SETTINGS; listening = null; rebuild(); })
                .dimensions(panelX + 104, 22, 100, 20).build();
        tabSettings.active = tab != Tab.SETTINGS;
        addDrawableChild(tabSettings);
        addDrawableChild(ButtonWidget.builder(Text.literal("Zamknij"), b -> close())
                .dimensions(panelX + panelW - 70, 22, 70, 20).build());

        if (tab == Tab.ITEMS) initItems(); else initSettings();
    }

    private void initItems() {
        // wyszukiwarka + start/stop
        TextFieldWidget filterField = new TextFieldWidget(this.textRenderer, panelX + 52, 48, 150, 16, Text.empty());
        filterField.setMaxLength(40);
        filterField.setText(filter);
        filterField.setChangedListener(s -> {
            filter = s;
            applyFilter();
            page = 0;
            rebuild();
            // po przebudowie widżetów przywracamy fokus na polu wyszukiwania
            for (var child : this.children()) {
                if (child instanceof TextFieldWidget tf && tf.getY() == 48) { setFocused(tf); tf.setFocused(true); }
            }
        });
        addDrawableChild(filterField);

        String startLabel = filter.isBlank() ? "Start analizy" : "Start (filtr)";
        addDrawableChild(ButtonWidget.builder(Text.literal(startLabel), b -> {
            String f = filter.trim();
            close();
            RynekAnalizaMod.startAnalysis(f);
        }).dimensions(panelX + 208, 46, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop"), b -> MarketAnalyzer.stop())
                .dimensions(panelX + 312, 46, 50, 20).build());

        // tabela
        tableTop = 90;
        rowsPerPage = Math.max(1, (this.height - tableTop - 52) / ROW_H);
        int pages = Math.max(1, (filtered.size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.min(Math.max(0, page), pages - 1);

        colName = panelX + 6;
        colNameW = (int) (panelW * 0.33);
        colPrice = panelX + (int) (panelW * 0.37);
        colMargin = panelX + (int) (panelW * 0.52);
        colMarginW = Math.max(52, (int) (panelW * 0.17));
        colSug = panelX + (int) (panelW * 0.72);
        colUse = panelX + (int) (panelW * 0.86);
        colUseW = panelX + panelW - colUse - 2;

        pageRows.clear();
        int from = page * rowsPerPage;
        int to = Math.min(filtered.size(), from + rowsPerPage);
        for (int i = from; i < to; i++) {
            BkBridge.BkTarget t = filtered.get(i);
            pageRows.add(t);
            int y = tableTop + (i - from) * ROW_H;
            addMarginRow(t, y);
        }

        // przełączanie stron
        int py = this.height - 40;
        ButtonWidget prev = ButtonWidget.builder(Text.literal("<"), b -> { page--; rebuild(); })
                .dimensions(panelX + panelW / 2 - 70, py, 24, 20).build();
        prev.active = page > 0;
        addDrawableChild(prev);
        ButtonWidget next = ButtonWidget.builder(Text.literal(">"), b -> { page++; rebuild(); })
                .dimensions(panelX + panelW / 2 + 46, py, 24, 20).build();
        next.active = page < pages - 1;
        addDrawableChild(next);
    }

    private void addMarginRow(BkBridge.BkTarget t, int y) {
        TextFieldWidget f = new TextFieldWidget(this.textRenderer, colMargin, y, colMarginW, 16, Text.empty());
        f.setMaxLength(12);
        f.setTextPredicate(s -> s.matches("\\d*[.,]?\\d*"));
        Double own = cfg.margins.get(t.key());
        f.setText(own == null ? "" : fmt(own));
        f.setSuggestion(own == null ? fmt(cfg.defaultMargin) : null);
        f.setChangedListener(s -> {
            Double v = parse(s);
            if (s.isEmpty()) {
                cfg.margins.remove(t.key());
                f.setSuggestion(fmt(cfg.defaultMargin));
            } else if (v != null) {
                cfg.margins.put(t.key(), v);
                f.setSuggestion(null);
            }
        });
        addDrawableChild(f);

        Double sug = cfg.suggested.get(t.key());
        ButtonWidget use = ButtonWidget.builder(Text.literal("Użyj"), b -> {
            Double v = cfg.suggested.get(t.key());
            if (v != null) f.setText(fmt(v));
        }).dimensions(colUse, y - 2, colUseW, 20).build();
        use.active = sug != null;
        addDrawableChild(use);
    }

    private void initSettings() {
        int vx = panelX + (int) (panelW * 0.56);
        int vw = (int) (panelW * 0.40);
        int y = 56;
        int step = 26;

        addKeyButton(vx, y, vw, RynekAnalizaMod.openKey);
        y += step;
        addKeyButton(vx, y, vw, RynekAnalizaMod.toggleKey);
        y += step;
        addNumberField(vx, y, vw, cfg.defaultMargin, false, v -> cfg.defaultMargin = v);
        y += step;
        addNumberField(vx, y, vw, cfg.taxFactor, false, v -> cfg.taxFactor = v);
        y += step;
        addNumberField(vx, y, vw, cfg.suggestFactor, false, v -> cfg.suggestFactor = v);
        y += step;
        addNumberField(vx, y, vw, cfg.highlightPauseTicks, true, v -> cfg.highlightPauseTicks = (int) Math.round(v));
        y += step;
        addToggle(vx, y, vw, cfg.showOverlay, v -> cfg.showOverlay = v);
        y += step;
        addToggle(vx, y, vw, cfg.resumeBk, v -> cfg.resumeBk = v);
    }

    private void addKeyButton(int x, int y, int w, KeyBinding kb) {
        String label = listening == kb ? "> naciśnij klawisz <" : kb.getBoundKeyLocalizedText().getString();
        addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> { listening = kb; rebuild(); })
                .dimensions(x, y - 2, w, 20).build());
    }

    private void addNumberField(int x, int y, int w, double value, boolean integer, Consumer<Double> setter) {
        TextFieldWidget f = new TextFieldWidget(this.textRenderer, x, y, w, 16, Text.empty());
        f.setMaxLength(10);
        f.setTextPredicate(s -> s.matches(integer ? "\\d*" : "\\d*[.,]?\\d*"));
        f.setText(fmt(value));
        f.setChangedListener(s -> {
            Double v = parse(s);
            if (v != null) {
                setter.accept(v);
                cfg.applyToAnalyzer();
            }
        });
        addDrawableChild(f);
    }

    private void addToggle(int x, int y, int w, boolean value, Consumer<Boolean> setter) {
        boolean[] state = {value};
        addDrawableChild(ButtonWidget.builder(Text.literal(value ? "TAK" : "NIE"), b -> {
            state[0] = !state[0];
            setter.accept(state[0]);
            cfg.applyToAnalyzer();
            b.setMessage(Text.literal(state[0] ? "TAK" : "NIE"));
        }).dimensions(x, y - 2, w, 20).build());
    }

    // ---------- rysowanie ----------
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (bgDone) return; // własne tło, rysowane raz na klatkę (bez rozmycia)
        bgDone = true;
        ctx.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        bgDone = false;
        renderBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(panelX - 4, 18, panelX + panelW + 4, this.height - 6, 0xE0202020);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Rynek Analiza"), this.width / 2, 8, WHITE);

        if (tab == Tab.ITEMS) drawItemsLabels(ctx); else drawSettingsLabels(ctx);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawItemsLabels(DrawContext ctx) {
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Szukaj:"), panelX + 6, 52, GRAY);

        int hy = tableTop - 14;
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Przedmiot"), colName, hy, YELLOW);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Skup ($)"), colPrice, hy, YELLOW);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Marża ($)"), colMargin, hy, YELLOW);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Sugestia"), colSug, hy, YELLOW);

        if (!loadError.isEmpty()) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("Nie mogę wczytać listy z BK-Rynek: " + loadError), panelX + 6, tableTop, 0xFFFF5555);
            return;
        }
        if (filtered.isEmpty()) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(all.isEmpty() ? "Lista przedmiotów w BK-Rynek jest pusta." : "Brak przedmiotów pasujących do wyszukiwania."), panelX + 6, tableTop, GRAY);
        }
        for (int i = 0; i < pageRows.size(); i++) {
            BkBridge.BkTarget t = pageRows.get(i);
            int y = tableTop + i * ROW_H;
            ctx.fill(panelX, y - 3, panelX + panelW, y + 18, i % 2 == 0 ? 0x30FFFFFF : 0x18FFFFFF);
            ctx.drawTextWithShadow(this.textRenderer, this.textRenderer.trimToWidth(t.displayName(), colNameW), colName, y + 4, WHITE);
            double price = t.currentBuyPrice();
            ctx.drawTextWithShadow(this.textRenderer, price > 0 ? fmt(price) + "$" : "-", colPrice, y + 4, GRAY);
            Double sug = cfg.suggested.get(t.key());
            ctx.drawTextWithShadow(this.textRenderer, sug != null ? fmt(sug) + "$" : "-", colSug, y + 4, sug != null ? GREEN : GRAY);
        }

        int pages = Math.max(1, (filtered.size() + rowsPerPage - 1) / rowsPerPage);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Strona " + (page + 1) + "/" + pages + "  (" + filtered.size() + " przedm.)"),
                panelX + panelW / 2, this.height - 35, WHITE);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Sugestia = krok cenowy 5 najtańszych ofert (rozstęp / 4). Skup = średnia 3 najtańszych x podatek - marża."),
                panelX + 6, this.height - 16, GRAY);
    }

    private void drawSettingsLabels(DrawContext ctx) {
        String[] labels = {
                "Klawisz: otwórz panel",
                "Klawisz: start/stop analizy",
                "Domyślna marża ($)",
                "Podatek (mnożnik, np. 0.97)",
                "Mnożnik sugerowanej marży",
                "Pauza podświetlenia (ticki, 20 = 1 s)",
                "Podgląd na rynku (żółte oferty)",
                "Wznów BK po analizie"
        };
        int y = 56;
        for (String l : labels) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(l), panelX + 8, y + 3, WHITE);
            y += 26;
        }
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Przy ustawianiu klawisza: Delete/Backspace = brak klawisza, Esc = anuluj."),
                panelX + 8, y + 6, GRAY);
    }

    // ---------- zdarzenia ----------
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listening != null) {
            KeyBinding kb = listening;
            listening = null;
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) kb.setBoundKey(InputUtil.UNKNOWN_KEY);
                else kb.setBoundKey(InputUtil.fromKeyCode(keyCode, scanCode));
                KeyBinding.updateKeysByCode();
                MinecraftClient.getInstance().options.write();
            }
            rebuild();
            return true;
        }
        boolean typing = getFocused() instanceof TextFieldWidget tf && tf.isFocused();
        if (!typing && RynekAnalizaMod.openKey != null && RynekAnalizaMod.openKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (listening != null) { // klik anuluje ustawianie klawisza
            listening = null;
            rebuild();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void removed() {
        cfg.save();
        cfg.applyToAnalyzer();
    }

    // ---------- liczby ----------
    private static Double parse(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Double.parseDouble(s.replace(',', '.')); } catch (NumberFormatException e) { return null; }
    }

    static String fmt(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        String s = String.format(Locale.ROOT, "%.2f", v);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
