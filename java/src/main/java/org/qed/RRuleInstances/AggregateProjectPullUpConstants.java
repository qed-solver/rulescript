package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

public record AggregateProjectPullUpConstants() implements RRule {
    static final RelRN source = RelRN.scan("S", "S_Type");
    static final RexRN constant = RexRN.trueLiteral();
    static final RelRN input = source.project(Seq.of(constant, source.field(0)));

    static final RexRN key0 = input.field(0);
    static final RexRN key1 = input.field(1);
    static final RelRN.AggCall f = key1.aggCall("f");

    static final RelRN beforeAgg = new RelRN.Aggregate(
            input,
            Seq.of(key0, key1),
            Seq.of(f));

    static final RelRN reduced = new RelRN.Aggregate(
            input,
            Seq.of(key1),
            Seq.of(f));
    static final RelRN afterAgg = reduced.project(Seq.of(
            constant,
            reduced.field(0),
            reduced.field(1)));

    @Override
    public RelRN before() {
        return beforeAgg;
    }

    @Override
    public RelRN after() {
        return afterAgg;
    }
}