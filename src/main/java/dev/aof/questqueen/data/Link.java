package dev.aof.questqueen.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record Link(String from, String to, Gate gate) {
    public static final Codec<Link> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("from").forGetter(Link::from),
            Codec.STRING.fieldOf("to").forGetter(Link::to),
            Gate.CODEC.optionalFieldOf("gate", Gate.AND).forGetter(Link::gate)
    ).apply(instance, Link::new));

    public Link(String from, String to) {
        this(from, to, Gate.AND);
    }

    public Link withGate(Gate gate) {
        return new Link(from, to, gate);
    }

    public Link withOp(GateOp op) {
        return withGate(gate.withOp(op));
    }
}
