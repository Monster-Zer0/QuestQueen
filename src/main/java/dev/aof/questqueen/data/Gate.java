package dev.aof.questqueen.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record Gate(GateOp op, List<GateCondition> conditions) {
    public static final Gate AND = new Gate(GateOp.AND, List.of());
    public static final Gate OR = new Gate(GateOp.OR, List.of());
    public static final Gate XOR = new Gate(GateOp.XOR, List.of());
    public static final Gate NOT = new Gate(GateOp.NOT, List.of());

    public static Gate of(GateOp op) {
        return switch (op) {
            case AND -> AND;
            case OR -> OR;
            case XOR -> XOR;
            case NOT -> NOT;
        };
    }

    public Gate withOp(GateOp next) {
        return new Gate(next, conditions);
    }

    private static final Codec<Gate> OBJECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GateOp.CODEC.optionalFieldOf("op", GateOp.AND).forGetter(Gate::op),
            GateCondition.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(Gate::conditions)
    ).apply(instance, Gate::new));

    public static final Codec<Gate> CODEC = Codec.withAlternative(
            GateOp.CODEC.xmap(op -> new Gate(op, List.of()), Gate::op),
            OBJECT_CODEC
    );

    public Gate {
        conditions = List.copyOf(conditions);
    }
}
