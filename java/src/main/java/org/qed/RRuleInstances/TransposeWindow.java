package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import kala.collection.Seq;

// ORIGIN: Spark
// SCOPE: PARTIAL — QED has no window operator, so each window is modeled as a group-by aggregate over the same source joined back to the source on the partition keys (partitions (a) and (a, b) with (a) a proper subset of (a, b), and the outer window's function depending only on a base column), and only case 1 of the rule (two directly stacked windows, no Project in between) is encoded.
public record TransposeWindow() implements RRule {

    static final RelType.VarType aType = new RelType.VarType("a_type", true);
    static final RelType.VarType bType = new RelType.VarType("b_type", true);
    static final RelType.VarType pType = new RelType.VarType("p_type", true);
    static final RelType.VarType inType = new RelType.VarType("in_type", true);
    static final RelType.VarType outType = new RelType.VarType("out_type", true);

    static final RelRN source = RelRN.scanMany("R", Seq.of(aType, bType, pType));

    // inner window w2: PARTITION BY (a, b), uninterpreted window function over the payload
    static final RelRN aggInner = new RelRN.Aggregate(
        source,
        Seq.of(source.field(0), source.field(1)),
        Seq.of(new RelRN.AggCall("innerW", false, inType, Seq.of(source.field(2)))));
    // aggInner output: (a, b, innerW)

    // outer window w1: PARTITION BY (a), a proper subset of (a, b); uninterpreted window function over the payload
    static final RelRN aggOuter = new RelRN.Aggregate(
        source,
        Seq.of(source.field(0)),
        Seq.of(new RelRN.AggCall("outerW", false, outType, Seq.of(source.field(2)))));
    // aggOuter output: (a, outerW)

    @Override
    public RelRN before() {
        // w1(w2(R)): join back the inner (finer partition) window first, then the outer (coarser) window
        RexRN condAB = RexRN.and(
            new RexRN.Pred(SqlStdOperatorTable.EQUALS,
                Seq.of(source.joinField(0, aggInner), source.joinField(3, aggInner))),
            new RexRN.Pred(SqlStdOperatorTable.EQUALS,
                Seq.of(source.joinField(1, aggInner), source.joinField(4, aggInner))));
        RelRN joinAB = source.join(JoinRelType.INNER, condAB, aggInner);
        // joinAB: (a, b, p, a, b, innerW)
        RelRN w2 = joinAB.project(Seq.of(joinAB.field(0), joinAB.field(1), joinAB.field(2), joinAB.field(5)));
        // w2: (a, b, p, innerW)
        RexRN condA = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(w2.joinField(0, aggOuter), w2.joinField(4, aggOuter)));
        RelRN joinA = w2.join(JoinRelType.INNER, condA, aggOuter);
        // joinA: (a, b, p, innerW, a, outerW)
        return joinA.project(Seq.of(joinA.field(0), joinA.field(1), joinA.field(2),
            joinA.field(3), joinA.field(5)));
        // (a, b, p, innerW, outerW)
    }

    @Override
    public RelRN after() {
        // w2(w1(R)): join back the outer (coarser partition) window first, then the inner (finer) window
        RexRN condA = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(source.joinField(0, aggOuter), source.joinField(3, aggOuter)));
        RelRN joinA = source.join(JoinRelType.INNER, condA, aggOuter);
        // joinA: (a, b, p, a, outerW)
        RelRN w1 = joinA.project(Seq.of(joinA.field(0), joinA.field(1), joinA.field(2), joinA.field(4)));
        // w1: (a, b, p, outerW)
        RexRN condAB = RexRN.and(
            new RexRN.Pred(SqlStdOperatorTable.EQUALS,
                Seq.of(w1.joinField(0, aggInner), w1.joinField(4, aggInner))),
            new RexRN.Pred(SqlStdOperatorTable.EQUALS,
                Seq.of(w1.joinField(1, aggInner), w1.joinField(5, aggInner))));
        RelRN joinAB = w1.join(JoinRelType.INNER, condAB, aggInner);
        // joinAB: (a, b, p, outerW, a, b, innerW)
        return joinAB.project(Seq.of(joinAB.field(0), joinAB.field(1), joinAB.field(2),
            joinAB.field(6), joinAB.field(3)));
        // (a, b, p, innerW, outerW) — attribute order restored by the rule's trailing Project
    }
}
