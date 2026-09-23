package dev.aof.questqueen.data;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum GateOp implements StringRepresentable {
    AND("and"),
    OR("or"),
    NOT("not"),
    XOR("xor");

    public static final Codec<GateOp> CODEC = StringRepresentable.fromEnum(GateOp::values);

    private final String name;

    GateOp(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static GateOp fromName(String value) {
        for (GateOp op : values()) {
            if (op.name.equalsIgnoreCase(value)) {
                return op;
            }
        }
        return AND;
    }
}
