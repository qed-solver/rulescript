package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — one constant group key, one column group key, two arbitrary non-distinct aggregates carried through unchanged.
public record RemoveLiteralFromGroupExpressions() implements RRule {
    static final RelRN base = RelRN.scanMany("S", Seq.of(
            RexRN.varType("E", true),
            RexRN.varType("A", true)));
    static final RexRN c = new RexRN.Proj(
            RuleBuilder.create().genericProjectionOp("C", RexRN.varType("C", true)),
            Seq.empty());
    static final RelRN.AggCall f = new RelRN.AggCall("f", false, RexRN.varType("F", true), Seq.of(base.field(1)));
    static final RelRN.AggCall g = new RelRN.AggCall("g", false, RexRN.varType("G", true), Seq.of(base.field(1)));

    @Override
    public RelRN before() {
        return new RelRN.Aggregate(base, Seq.of(base.field(0), c), Seq.of(f, g));
    }

    @Override
    public RelRN after() {
        RelRN s = new RelRN.Aggregate(base, Seq.of(base.field(0)), Seq.of(f, g));
        return new RelRN.ProjectMany(Seq.of(s.field(0), c, s.field(1), s.field(2)), s);
    }
}
