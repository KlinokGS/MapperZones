package net.mapperzones.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.mapperzones.config.MapperZonesSettings;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Single multiline editor with a command-block-like syntax preview. */
public final class ZoneTextEditorScreen extends Screen {
    private final Screen parent;
    private final String kind;
    private final Consumer<String> onSave;
    private final StringBuilder text = new StringBuilder();
    private final int maxHintsCount = 4;
    private int cursor;
    /** Selection anchor; -1 means there is no active selection. */
    private int selectionAnchor = -1;
    private boolean draggingSelection;
    private int firstVisibleLine;
    private int areaX, areaY, areaW, areaH;
    private boolean focused = true;
    private int completionIndex;
    private int completionReplaceStart;
    private String completionPrefix = "";
    private int completionScrollOffset;
    private int completionPopupX, completionPopupY, completionPopupW, completionPopupH;
    private int completionPopupVisibleCount;
    private record Completion(String label, String insertion) {}

    static final java.util.regex.Pattern syntax = java.util.regex.Pattern.compile(
            "(?i)(?<string>\\\"(?:\\\\.|[^\\\"])*\\\"|'(?:\\\\.|[^'])*')"
                    + "|(?<marker>\\b(?:command|sound|particle|effect|teleport|zone|redstone|inventory):|"
                    + "\\bblockAt(?=\\s*\\()|\\bvar\\.(?:player|global)\\.|\\bzone\\.)"
                    + "|(?<actionTag>\\baction\\.(?:enter|stay|exit):?)"
                    + "|(?<function>\\bexists(?=\\s*\\())"
                    + "|(?<playerProperty>\\bplayer\\.[A-Za-z_][A-Za-z0-9_]*\\b)"
                    + "|(?<globalProperty>\\bglobal\\.[A-Za-z_][A-Za-z0-9_]*\\b)"
                    + "|(?<operation>\\.(?:set|remove|open|direction|powered|lit|axis|extended|disable|enable|reset)\\b)"
                    + "|(?<number>(?<![\\w.])[-+]?\\d+(?:\\.\\d+)?\\b)"
                    + "|(?<logical>&&|\\|\\||==|!=|<=|>=|[<>!])"
                    + "|(?<value>\\b(?:true|false)\\b|@[pares])"
    );
    static final java.util.regex.Pattern blockAt = java.util.regex.Pattern.compile("(?i)blockAt\\s*\\(\\s*-?\\d+\\s*,\\s*-?\\d+\\s*,\\s*-?\\d+\\s*\\)\\s*$");

    public ZoneTextEditorScreen(Screen parent, String kind, String initial, Consumer<String> onSave) {
        super(Component.translatable(titleKey(kind)));
        this.parent = parent;
        this.kind = kind;
        this.onSave = onSave;
        if (initial != null) text.append(initial.replace("\r", ""));
        cursor = text.length();
    }

    private static String titleKey(String k) {
        return switch (k) { case "enter" -> "screen.mapperzones.enter_title"; case "exit" -> "screen.mapperzones.exit_title"; case "stay" -> "screen.mapperzones.stay_title"; default -> "screen.mapperzones.condition_title"; };
    }

    @Override protected void init() {
        int margin = 18;
        areaX = margin; areaY = 64; areaW = width - margin * 2; areaH = Math.max(60, height - 158);
        int bottom = height - 27;
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.syntax_help"), b -> {
            if (kind.equals("conditions")) {
                minecraft.setScreen(new ConditionHelpScreen(this));
            } else {
                minecraft.setScreen(new ActionBuilderScreen(this));
            }
        }).bounds(margin, bottom, Math.min(100, Math.max(76, areaW / 5)), 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.save"), b -> saveAndReturn())
                .bounds(width - margin - 142, bottom, 68, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.back"), b -> {
            if (minecraft != null) minecraft.setScreen(parent);
        }).bounds(width - margin - 68, bottom, 68, 20).build());
    }

    private boolean hasSelection() { return selectionAnchor >= 0 && selectionAnchor != cursor; }
    private int selectionStart() { return Math.min(selectionAnchor, cursor); }
    private int selectionEnd() { return Math.max(selectionAnchor, cursor); }
    private void clearSelection() { selectionAnchor = -1; }
    private void beginMove(boolean selecting) {
        if (selecting) {
            if (selectionAnchor < 0) selectionAnchor = cursor;
        } else clearSelection();
    }
    private void deleteSelection() {
        if (!hasSelection()) return;
        int start = selectionStart(), end = selectionEnd();
        text.delete(start, end);
        cursor = start;
        clearSelection();
    }
    private void insert(String value) {
        deleteSelection();
        text.insert(cursor, value); cursor += value.length(); ensureCursorVisible();
    }
    private boolean controlDown(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || (modifiers & GLFW.GLFW_MOD_SUPER) != 0;
    }
    private int positionAt(double mx, double my) {
        List<String> ls = lines();
        int row = firstVisibleLine + Math.max(0, (int)(my - areaY - maxHintsCount) / font.lineHeight);
        row = Math.min(row, ls.size() - 1);
        String line = ls.get(row);
        int targetX = (int) mx - (areaX + 30), col = 0;
        while (col < line.length() && font.width(line.substring(0, col + 1)) < targetX) col++;
        int pos = 0;
        for (int i = 0; i < row; i++) pos += ls.get(i).length() + 1;
        return Math.min(text.length(), pos + col);
    }

    private int lineStart(int pos) { int p = Math.min(pos, text.length()); while (p > 0 && text.charAt(p - 1) != '\n') p--; return p; }
    private int lineEnd(int pos) { int p = Math.min(pos, text.length()); while (p < text.length() && text.charAt(p) != '\n') p++; return p; }
    private int lineNumber(int pos) { int n=0; for(int i=0;i<Math.min(pos,text.length());i++) if(text.charAt(i)=='\n') n++; return n; }
    private List<String> lines() {
        List<String> out = new ArrayList<>();
        String[] split = text.toString().split("\n", -1);
        for (String s : split) out.add(s);
        return out;
    }
    private void ensureCursorVisible() {
        int visible = Math.max(1, (areaH - 12) / font.lineHeight);
        int line = lineNumber(cursor);
        if (line < firstVisibleLine) firstVisibleLine = line;
        else if (line >= firstVisibleLine + visible) firstVisibleLine = line - visible + 1;
        firstVisibleLine = Math.max(0, firstVisibleLine);
    }


    /** Context-sensitive, lightweight IDE-style completion. Does not affect syntax coloring. */
    private List<Completion> completions() {
        if (!MapperZonesSettings.codeSuggestionsEnabled() || !focused || cursor <= 0) return List.of();
        int currentLineStart = lineStart(cursor);
        String before = text.substring(currentLineStart, cursor);
        String trimmed = before.stripLeading();
        if (trimmed.isEmpty()) return List.of();

        char previous = before.charAt(before.length() - 1);
        if (Character.isWhitespace(previous) || "()=<>!&|,;".indexOf(previous) >= 0) return List.of();

        int tokenStart = cursor;
        while (tokenStart > currentLineStart) {
            char c = text.charAt(tokenStart - 1);
            if (Character.isWhitespace(c) || "=<>!&|,;(){}$\"'".indexOf(c) >= 0) break;
            tokenStart--;
        }
        completionReplaceStart = tokenStart;
        String token = text.substring(tokenStart, cursor);
        if (token.isEmpty()) return List.of();
        String lower = token.toLowerCase(Locale.ROOT);
        List<Completion> out = new ArrayList<>();
        java.util.function.Consumer<String> add = value -> out.add(new Completion(value, value));

        // Function signatures are explanatory only: insert an empty call for the user to fill in.
        if (kind.equals("conditions") && "exists".startsWith(lower) && !lower.contains(".")) {
            out.add(new Completion("exists(var)", "exists()"));
        } else if (lower.startsWith("player.")) {
            String partial = lower.substring("player.".length());
            // TODO: Match player properties with Map syntax helper list
            for (String property : List.of("health", "max_health", "absorption", "food", "saturation",
                    "level", "experience", "name", "gamemode", "uuid", "x", "y", "z", "dimension",
                    "armor", "air", "max_air", "on_ground", "sprinting", "sneaking", "alive",
                    "in_water", "underwater", "invisible", "sleeping", "team")) {
                if (property.startsWith(partial)) out.add(new Completion(property, "player." + property));
            }
        } else if ("player".startsWith(lower)) {
            // First suggest the root; only show properties after the dot is typed.
            add.accept("player.");
        } else if (lower.startsWith("blockat") && !isInsideExistsCall(tokenStart)) {
            // Explain the arguments, but insert editable coordinates. Do not offer this
            // function while the caret is filling an exists(...) argument.
            out.add(new Completion("blockAt(x,y,z)", "blockAt(0,0,0)."));
        } else if (lower.startsWith("var.player.") && lower.endsWith(".") && token.substring("var.player.".length()).contains(".")) {
            add.accept(token + "set"); add.accept(token + "remove");
        } else if (lower.startsWith("var.player.") || lower.startsWith("var.global.")) {
            boolean player = lower.startsWith("var.player.");
            String stem = player ? "var.player." : "var.global.";
            String partial = token.substring(Math.min(stem.length(), token.length()));
            if (!partial.contains(".")) {
                Set<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                if (player) ClientData.PLAYER_VARIABLES.values().forEach(map -> names.addAll(map.keySet()));
                else names.addAll(ClientData.GLOBALS.keySet());
                for (String name : names) {
                    String candidate = stem + name;
                    if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)) add.accept(candidate);
                }
            } else if (token.endsWith(".")) {
                add.accept(token + "set"); add.accept(token + "remove");
            }
        } else if (lower.startsWith(".") && isAfterNumericBlockAt(tokenStart)) {
            List<String> blockProperties = kind.equals("conditions")
                    ? List.of(".open", ".direction", ".lit", ".axis", ".extended", ".powered")
                    : List.of(".open", ".direction", ".lit", ".axis", ".extended");
            for (String prop : blockProperties) {
                if (prop.startsWith(lower)) add.accept(prop);
            }
        } else {
            // Short boolean completions are useful when entering values, but never add operators.
            if ("true".startsWith(lower) && lower.startsWith("t")) add.accept("true");
            if ("false".startsWith(lower) && lower.startsWith("f")) add.accept("false");

            if (kind.equals("conditions")) {
                for (String candidate : List.of("exists(var)", "var.player.", "var.global.", "player", "blockAt(x,y,z)",
                        "time", "weather", "dim", "team", "item", "action.enter:", "action.stay:", "action.exit:")) {
                    if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)
                            && !(candidate.equals("blockAt(x,y,z)") && isInsideExistsCall(tokenStart))) {
                        String insertion = candidate.equals("exists(var)") ? "exists()"
                                : candidate.equals("blockAt(x,y,z)") ? "blockAt(0,0,0)." : candidate;
                        out.add(new Completion(candidate, insertion));
                    }
                }
            } else {
                for (String candidate : List.of("command:", "sound:", "particle:", "effect:", "teleport:",
                        "inventory:save", "inventory:restore", "zone:enable ", "zone:disable ", "zone:reset ",
                        "redstone:", "var.player.", "var.global.", "player", "blockAt(x,y,z)", "freeze ")) {
                    if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)
                            && !(candidate.equals("blockAt(x,y,z)") && isInsideExistsCall(tokenStart))) {
                        String insertion = candidate.equals("blockAt(x,y,z)") ? "blockAt(0,0,0)" : candidate;
                        add.accept(insertion);
                    }
                }
            }
        }

        // Remove an exact self-completion, while keeping explanatory signatures when applicable.
        out.removeIf(c -> c.insertion().equals(token));
        Set<String> seen = new java.util.HashSet<>();
        out.removeIf(c -> !seen.add(c.label().toLowerCase(Locale.ROOT)));
        return out;
    }

    private boolean isInsideExistsCall(int tokenStart) {
        int start = lineStart(tokenStart);
        String before = text.substring(start, tokenStart);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)\\bexists\\s*\\([^)]*$").matcher(before);
        return m.find();
    }

    /** True only when the caret follows a syntactically numeric blockAt(x,y,z) call. */
    private boolean isAfterNumericBlockAt(int tokenStart) {
        int start = lineStart(tokenStart);
        String before = text.substring(start, tokenStart);
        return blockAt.matcher(before).find();
    }

    private boolean applyCompletion(List<Completion> options, int index) {
        if (options.isEmpty() || index < 0 || index >= options.size()) return false;
        Completion completion = options.get(index);
        int start = Math.max(lineStart(cursor), Math.min(completionReplaceStart, cursor));
        text.replace(start, cursor, completion.insertion());
        cursor = start + completion.insertion().length();
        clearSelection();
        completionScrollOffset = 0;
        completionIndex = 0;
        ensureCursorVisible();
        return true;
    }

    @Override public boolean charTyped(char ch, int modifiers) {
        if (!focused || Character.isISOControl(ch)) return super.charTyped(ch, modifiers);
        if (text.length() < 32760) insert(String.valueOf(ch));
        return true;
    }

    private void saveAndReturn() {
        onSave.accept(text.toString());
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { if (minecraft != null) minecraft.setScreen(parent); return true; }
        boolean ctrl = controlDown(modifiers);
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        List<Completion> options = completions();
        if (!options.isEmpty()) {
            if (key == GLFW.GLFW_KEY_UP) {
                completionIndex = Math.floorMod(completionIndex - 1, options.size());
                keepCompletionSelectionVisible(options.size()); return true;
            }
            if (key == GLFW.GLFW_KEY_DOWN) {
                completionIndex = (completionIndex + 1) % options.size();
                keepCompletionSelectionVisible(options.size()); return true;
            }
            if (key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (applyCompletion(options, completionIndex)) { completionIndex = 0; return true; }
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) { completionIndex = 0; return true; }
        }
        if (ctrl && key == GLFW.GLFW_KEY_A) {
            selectionAnchor = 0; cursor = text.length(); ensureCursorVisible(); return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_C) {
            if (hasSelection()) minecraft.keyboardHandler.setClipboard(text.substring(selectionStart(), selectionEnd()));
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_X) {
            if (hasSelection()) { minecraft.keyboardHandler.setClipboard(text.substring(selectionStart(), selectionEnd())); deleteSelection(); }
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            String clip = minecraft.keyboardHandler.getClipboard();
            if (clip != null && !clip.isEmpty()) insert(clip.replace("\r", "").substring(0, Math.min(clip.replace("\r", "").length(), 32760 - text.length() + (hasSelection() ? selectionEnd() - selectionStart() : 0))));
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { insert("\n"); return true; }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (hasSelection()) deleteSelection();
            else if (cursor > 0) { text.deleteCharAt(cursor - 1); cursor--; }
            ensureCursorVisible(); return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            if (hasSelection()) deleteSelection(); else if (cursor < text.length()) text.deleteCharAt(cursor);
            ensureCursorVisible(); return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            beginMove(shift);
            if (!shift && hasSelection() && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
                cursor = key == GLFW.GLFW_KEY_LEFT ? selectionStart() : selectionEnd(); clearSelection();
            } else if (key == GLFW.GLFW_KEY_LEFT) { if(cursor>0)cursor--; }
            else if (key == GLFW.GLFW_KEY_RIGHT) { if(cursor<text.length())cursor++; }
            else if (key == GLFW.GLFW_KEY_HOME) cursor=lineStart(cursor);
            else if (key == GLFW.GLFW_KEY_END) cursor=lineEnd(cursor);
            else {
                int start=lineStart(cursor), col=cursor-start;
                if(key==GLFW.GLFW_KEY_UP && start>0) { int prevEnd=start-1, prevStart=lineStart(prevEnd); cursor=Math.min(prevStart+col,prevEnd); }
                else if(key==GLFW.GLFW_KEY_DOWN) { int end=lineEnd(cursor); if(end<text.length()) cursor=Math.min(end+1+col,lineEnd(end+1)); }
            }
            if (!shift) clearSelection();
            ensureCursorVisible(); return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void keepCompletionSelectionVisible(int optionCount) {
        int visible = Math.min(maxHintsCount, optionCount);
        if (completionIndex < completionScrollOffset) completionScrollOffset = completionIndex;
        else if (completionIndex >= completionScrollOffset + visible) completionScrollOffset = completionIndex - visible + 1;
        completionScrollOffset = Math.max(0, Math.min(completionScrollOffset, Math.max(0, optionCount - visible)));
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mx >= areaX && mx <= areaX + areaW && my >= areaY && my <= areaY + areaH) {
            focused = true;
            cursor = positionAt(mx, my);
            selectionAnchor = cursor;
            draggingSelection = true;
            return true;
        }
        return super.mouseClicked(mx,my,button);
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (draggingSelection && button == 0) {
            cursor = positionAt(mx, Math.max(areaY + 1, Math.min(areaY + areaH - 1, my)));
            ensureCursorVisible();
            return true;
        }
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && draggingSelection) {
            draggingSelection = false;
            if (!hasSelection()) clearSelection();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        List<Completion> options = completions();
        if (!options.isEmpty() && options.size() > maxHintsCount
                && mx >= completionPopupX && mx <= completionPopupX + completionPopupW
                && my >= completionPopupY && my <= completionPopupY + completionPopupH) {
            completionScrollOffset = Math.max(0, Math.min(options.size() - maxHintsCount,
                    completionScrollOffset - (int)Math.signum(delta)));
            return true;
        }
        int max=Math.max(0,lines().size()-Math.max(1,(areaH-12)/font.lineHeight));
        firstVisibleLine=Math.max(0,Math.min(max,firstVisibleLine-(int)Math.signum(delta)*3)); return true;
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font,title,width/2,12,0xFFFFFF);
        Component hint = Component.translatable(kind.equals("conditions") ? "screen.mapperzones.editor_condition_hint" : "screen.mapperzones.editor_action_hint");
        g.drawCenteredString(font,hint,width/2,32,0xB7DFFF);
        g.fill(areaX-2,areaY-2,areaX+areaW+2,areaY+areaH+2,0xFF8A8A8A);
        g.fill(areaX,areaY,areaX+areaW,areaY+areaH,0xE8000000);
        List<String> ls=lines(); int visible=Math.max(1,(areaH-12)/font.lineHeight);
        for(int row=firstVisibleLine;row<Math.min(ls.size(),firstVisibleLine+visible);row++) {
            String line=ls.get(row); int yy=areaY+maxHintsCount+(row-firstVisibleLine)*font.lineHeight;
            g.drawString(font,String.format(Locale.ROOT,"%02d",row+1),areaX+5,yy,0xFF7F8C98);
            if (hasSelection()) {
                int lineStart = 0; for (int li = 0; li < row; li++) lineStart += ls.get(li).length() + 1; int start = Math.max(selectionStart(), lineStart), end = Math.min(selectionEnd(), lineStart + line.length());
                if (start < end) {
                    String before = line.substring(0, start - lineStart);
                    String selected = line.substring(start - lineStart, end - lineStart);
                    int sx = areaX + 30 + font.width(before), ex = sx + font.width(selected);
                    g.fill(sx, yy - 1, ex, yy + font.lineHeight - 1, 0xA0608CFF);
                }
            }
            drawHighlighted(g,line,areaX+30,yy,Math.max(20,areaW-38));
            if(lineNumber(cursor)==row && focused) {
                String before=line.substring(0,Math.max(0,Math.min(line.length(),cursor-lineStart(cursor))));
                int cx=areaX+30+font.width(before);
                if(cx<areaX+areaW-4) g.fill(cx,yy-1,cx+1,yy+font.lineHeight-1,0xFFFFFFFF);
            }
        }
        // Compact autocomplete popup anchored to the caret. Existing syntax highlighting is untouched.
        List<Completion> options = completions();
        if (!options.isEmpty() && focused) {
            int row = lineNumber(cursor);
            if (row >= firstVisibleLine && row < firstVisibleLine + visible) {
                String currentLine = ls.get(row);
                int lineStartPos = lineStart(cursor);
                String beforeCursor = currentLine.substring(0, Math.max(0, Math.min(currentLine.length(), cursor - lineStartPos)));
                int caretX = areaX + 30 + font.width(beforeCursor);
                int caretY = areaY + maxHintsCount + (row - firstVisibleLine + 1) * font.lineHeight;
                int popupW = 0;
                for (Completion c : options) popupW = Math.max(popupW, font.width(c.label()));
                popupW = Math.min(Math.max(120, popupW + 14), Math.max(120, width - 16));
                int visibleOptions = Math.min(maxHintsCount, options.size());
                completionScrollOffset = Math.max(0, Math.min(completionScrollOffset, options.size() - visibleOptions));
                int popupH = visibleOptions * (font.lineHeight + 2) + maxHintsCount;
                int popupX = Math.max(8, Math.min(caretX, width - popupW - 8));
                int popupY = caretY + popupH < height - 28 ? caretY : Math.max(8, caretY - popupH - font.lineHeight);
                completionPopupX = popupX; completionPopupY = popupY; completionPopupW = popupW; completionPopupH = popupH;
                completionPopupVisibleCount = visibleOptions;
                g.fill(popupX, popupY, popupX + popupW, popupY + popupH, 0xF0202530);
                g.fill(popupX, popupY, popupX + popupW, popupY + 1, 0xFF80BFFF);
                for (int rowIndex = 0; rowIndex < visibleOptions; rowIndex++) {
                    int optionIndex = completionScrollOffset + rowIndex;
                    int yy = popupY + 3 + rowIndex * (font.lineHeight + 2);
                    if (optionIndex == completionIndex) g.fill(popupX + 2, yy - 1, popupX + popupW - 2, yy + font.lineHeight, 0xFF3A5270);
                    String label = trimToWidth(options.get(optionIndex).label(), popupW - 12);
                    g.drawString(font, label, popupX + maxHintsCount, yy, optionIndex == completionIndex ? 0xFFFFFFFF : 0xFFD4DCE8, false);
                }
                if (options.size() > visibleOptions) {
                    int trackX = popupX + popupW - 4;
                    int trackTop = popupY + 3, trackBottom = popupY + popupH - 3;
                    g.fill(trackX, trackTop, trackX + 2, trackBottom, 0xFF3B4655);
                    int thumbH = Math.max(8, (trackBottom - trackTop) * visibleOptions / options.size());
                    int thumbRange = Math.max(1, trackBottom - trackTop - thumbH);
                    int thumbY = trackTop + thumbRange * completionScrollOffset / Math.max(1, options.size() - visibleOptions);
                    g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFF80BFFF);
                }
            }
        }
        Component footerHint = Component.translatable(kind.equals("conditions")
                ? "screen.mapperzones.condition_vars_hint"
                : "screen.mapperzones.action_help_hint");
        int hintY = areaY + areaH + 7;
        int hintWidth = Math.max(40, width - areaX * 2);
        // Wrap long localized hints instead of letting them run past the right edge.
        // The editor layout reserves enough space between the text area and bottom buttons.
        List<net.minecraft.util.FormattedCharSequence> hintLines = font.split(footerHint, hintWidth);
        int maxHintLines = Math.max(1, (height - 32 - hintY) / font.lineHeight);
        for (int i = 0; i < Math.min(hintLines.size(), maxHintLines); i++) {
            g.drawString(font, hintLines.get(i), areaX, hintY + i * font.lineHeight, 0xFF9BE7A8, false);
        }
        super.render(g,mx,my,pt);
    }

    private void drawHighlighted(GuiGraphics g, String s, int x, int y, int maxWidth) {
        // Shared, token-based syntax highlighting for conditions and actions.
        // Matches are found anywhere in the line, so leading spaces never affect highlighting.
        final int baseColor = 0xFFDADADA;
        final int markerColor = 0xFF928EFF;   // command tags, blockAt, var.player/global prefixes
        final int operationColor = 0xFFC57EFF; // .set, .remove, block properties and zone operations
        final int numberColor = 0xFF2CF7FF;   // command-like cyan numbers
        final int logicalColor = 0xFFFFA24A;  // boolean/comparison operators
        final int functionColor = 0xFF78E6D0; // exists(...)
        final int actionTagColor = 0xFFFFA6D7; // action.enter/stay/exit
        final int playerColor = 0xFF8BD5FF;   // player.* properties
        final int globalColor = 0xFF8BFFA0;   // legacy global.* references
        final int valueColor = 0xFFF9FF5C;    // true/false and selectors
        final int stringColor = 0xFFFFC98B;

        java.util.regex.Matcher matcher = syntax.matcher(s);
        int drawX = x;
        int pos = 0;
        int used = 0;
        while (matcher.find()) {
            if (matcher.start() > pos) {
                String plain = s.substring(pos, matcher.start());
                int w = font.width(plain);
                if (used + w > maxWidth) {
                    String clipped = trimToWidth(plain, Math.max(0, maxWidth - used));
                    if (!clipped.isEmpty()) g.drawString(font, Component.literal(clipped), drawX, y, baseColor, false);
                    return;
                }
                if (!plain.isEmpty()) g.drawString(font, Component.literal(plain), drawX, y, baseColor, false);
                drawX += w;
                used += w;
            }
            String token = matcher.group();
            int w = font.width(token);
            if (used + w > maxWidth) {
                String clipped = trimToWidth(token, Math.max(0, maxWidth - used));
                if (!clipped.isEmpty()) g.drawString(font, Component.literal(clipped), drawX, y, baseColor, false);
                return;
            }
            int color;
            if (matcher.group("string") != null) color = stringColor;
            else if (matcher.group("operation") != null) color = operationColor;
            else if (matcher.group("number") != null) color = numberColor;
            else if (matcher.group("logical") != null) color = logicalColor;
            else if (matcher.group("function") != null) color = functionColor;
            else if (matcher.group("actionTag") != null) color = actionTagColor;
            else if (matcher.group("playerProperty") != null) color = playerColor;
            else if (matcher.group("globalProperty") != null) color = globalColor;
            else if (matcher.group("value") != null) color = valueColor;
            else color = markerColor;
            g.drawString(font, Component.literal(token), drawX, y, color, false);
            drawX += w;
            used += w;
            pos = matcher.end();
        }
        if (pos < s.length() && used < maxWidth) {
            String tail = s.substring(pos);
            String clipped = trimToWidth(tail, maxWidth - used);
            if (!clipped.isEmpty()) g.drawString(font, Component.literal(clipped), drawX, y, baseColor, false);
        }
    }

    private String trimToWidth(String value, int maxWidth) {
        String visible = value;
        while (!visible.isEmpty() && font.width(visible) > maxWidth) visible = visible.substring(0, visible.length() - 1);
        return visible;
    }

    @Override public boolean isPauseScreen(){return false;}
}
