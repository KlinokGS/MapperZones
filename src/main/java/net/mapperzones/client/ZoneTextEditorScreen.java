package net.mapperzones.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Single multiline editor with a command-block-like syntax preview. */
public final class ZoneTextEditorScreen extends Screen {
    private final Screen parent;
    private final String kind;
    private final Consumer<String> onSave;
    private final StringBuilder text = new StringBuilder();
    private int cursor;
    /** Selection anchor; -1 means there is no active selection. */
    private int selectionAnchor = -1;
    private boolean draggingSelection;
    private int firstVisibleLine;
    private int areaX, areaY, areaW, areaH;
    private boolean focused = true;

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
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.save"), b -> {
            onSave.accept(text.toString()); minecraft.setScreen(parent);
        }).bounds(width - margin - 142, bottom, 68, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mapperzones.back"), b -> minecraft.setScreen(parent))
                .bounds(width - margin - 68, bottom, 68, 20).build());
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
        int row = firstVisibleLine + Math.max(0, (int)(my - areaY - 6) / font.lineHeight);
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

    @Override public boolean charTyped(char ch, int modifiers) {
        if (!focused || Character.isISOControl(ch)) return super.charTyped(ch, modifiers);
        if (text.length() < 32760) insert(String.valueOf(ch));
        return true;
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { minecraft.setScreen(parent); return true; }
        boolean ctrl = controlDown(modifiers);
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
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
            String line=ls.get(row); int yy=areaY+6+(row-firstVisibleLine)*font.lineHeight;
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
        if (!kind.equals("conditions")) {
            int color = 0xFFDADADA;
            if (s.startsWith("command:") || s.startsWith("sound:") || s.startsWith("particle:") || s.startsWith("effect:") || s.startsWith("teleport:") || s.startsWith("zone:") || s.startsWith("redstone:")) color = 0xFF8BFFA0;
            g.drawString(font, Component.literal(trimToWidth(s, maxWidth)), x, y, color);
            return;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\\"(?:\\\\.|[^\\\"])*\\\"|'(?:\\\\.|[^'])*'|(?:>=|<=|==|!=|&&|\\|\\||[()!<>])|[-+]?(?:\\d+\\.?\\d*|\\.\\d+)|[A-Za-z_][A-Za-z0-9_./:-]*|\\s+|.").matcher(s);
        int drawX = x;
        int used = 0;
        while (matcher.find()) {
            String token = matcher.group();
            int color = 0xFFDADADA;
            String lower = token.toLowerCase(Locale.ROOT);
            if (token.isBlank()) color = 0xFFDADADA;
            else if (lower.matches("action\\.(enter|stay|exit):?")) color = 0xFFFFA6D7;
            else if (token.startsWith("\"") || token.startsWith("'")) color = 0xFFFFC98B;
            else if (token.matches("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)")) color = 0xFFB9A0FF;
            else if (java.util.Set.of("&&", "||", "==", "!=", ">", "<", ">=", "<=", "!", "(", ")").contains(token)) color = 0xFFFFD166;
            else if (lower.equals("exists")) color = 0xFF78E6D0;
            else if (lower.startsWith("player.")) color = 0xFF8BD5FF;
            else if (lower.startsWith("global.")) color = 0xFF8BFFA0;
            else if (java.util.Set.of("true", "false", "item", "team", "time", "weather", "dim", "var").contains(lower)) color = 0xFFFFA6D7;
            int tokenWidth = font.width(token);
            if (used + tokenWidth > maxWidth) break;
            if (!token.isBlank()) g.drawString(font, Component.literal(token), drawX, y, color, false);
            drawX += tokenWidth;
            used += tokenWidth;
        }
    }

    private String trimToWidth(String value, int maxWidth) {
        String visible = value;
        while (!visible.isEmpty() && font.width(visible) > maxWidth) visible = visible.substring(0, visible.length() - 1);
        return visible;
    }

    @Override public boolean isPauseScreen(){return false;}
}
