package dev.aof.questqueen.client;

/**
 * Canonical midnight defaults plus a bindable active palette for the open chapter theme.
 * Call {@link #apply(BookPalette)} at the start of each book frame; widgets keep reading these fields.
 */
public final class QuestColors {
    public static int VOID = 0xFF24142C;
    public static int CELL = 0xFF18101F;
    public static int CELL_LINE = 0xFF4A3C62;
    public static int CELL_SHADOW = 0xFF0A0610;
    public static int SIDEBAR = 0xFF1C1526;
    public static int SIDEBAR_EDGE = 0xFF3A3048;
    public static int SIDEBAR_ACTIVE = 0xFF2A2238;
    public static int SIDEBAR_TEXT = 0xFFB8A8C8;
    public static int SIDEBAR_HEADER = 0xFF8A7A9A;
    public static int LOCKED = 0xFF2B203A;
    public static int LOCKED_TEXT = 0xFF7A6A90;
    public static int CURRENT = 0xFF3DFF6E;
    public static int NEW = 0xFF488BD4;
    public static int EDIT = 0xFFFFB84A;
    public static int COMPLETED = 0xFFFFF540;
    public static int FAILED = 0xFFE01018;
    public static int CLOSED = 0xFF6A6080;
    public static int CARD = 0xFF16121C;
    public static int TEXT = 0xFFF5F0E0;
    public static int MUTED = 0xFFA898B8;
    public static int PORT_RED = 0xFF8C1A24;
    public static int PORT_DIM = 0xFF6A6080;
    public static int REWARD = 0xFFFFF540;
    public static int PATH = 0x66FFB84A;
    public static int ADD = 0x668A82A0;
    public static int MODAL_PINK = 0xFFE88AB0;
    public static int LOCKED_EDGE = 0xFF8C1A24;
    public static int GATE_AND = 0xFFC8C0D8;
    public static int GATE_OR = 0xFF4AD4FF;
    public static int GATE_XOR = 0xFFFFF540;
    public static int XOR_EDGE = NEW;
    public static int GATE_NOT = 0xFFE88AB0;

    private QuestColors() {
    }

    public static void apply(BookPalette palette) {
        VOID = palette.voidColor();
        CELL = palette.cell();
        CELL_LINE = palette.cellLine();
        CELL_SHADOW = palette.cellShadow();
        SIDEBAR = palette.sidebar();
        SIDEBAR_EDGE = palette.sidebarEdge();
        SIDEBAR_ACTIVE = palette.sidebarActive();
        SIDEBAR_TEXT = palette.sidebarText();
        SIDEBAR_HEADER = palette.sidebarHeader();
        LOCKED = palette.locked();
        LOCKED_TEXT = palette.lockedText();
        CURRENT = palette.current();
        NEW = palette.neu();
        EDIT = palette.edit();
        COMPLETED = palette.completed();
        FAILED = palette.failed();
        CLOSED = palette.closed();
        CARD = palette.card();
        TEXT = palette.text();
        MUTED = palette.muted();
        PORT_RED = palette.portRed();
        PORT_DIM = palette.portDim();
        REWARD = palette.reward();
        PATH = palette.path();
        ADD = palette.add();
        MODAL_PINK = palette.modalPink();
        LOCKED_EDGE = palette.lockedEdge();
        GATE_AND = palette.gateAnd();
        GATE_OR = palette.gateOr();
        GATE_XOR = palette.gateXor();
        XOR_EDGE = palette.xorEdge();
        GATE_NOT = palette.gateNot();
    }

    public static void reset() {
        apply(BookTheme.MIDNIGHT_ROYALTY.palette());
    }
}
