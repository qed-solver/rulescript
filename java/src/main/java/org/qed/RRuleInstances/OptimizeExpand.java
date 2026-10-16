package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;

// ORIGIN: Spark
// SCOPE: PARTIAL — Expand projections pass Key and Value through, vary only the two cat columns, and the inner distinct agg is a count over Value
public record OptimizeExpand() implements RRule {
    static final RelType.VarType KEY_T   = RexRN.varType("Key", true);
    static final RelType.VarType CAT1_T  = RexRN.varType("Cat1", true);
    static final RelType.VarType CAT2_T  = RexRN.varType("Cat2", true);
    static final RelType.VarType VALUE_T = RexRN.varType("Value", true);
    static final RelType.VarType CNT_T   = RexRN.varType("Cnt", true);
    static final RelType.VarType AGG2_T  = RexRN.varType("Agg2", true);

    static final RelRN child =
        RelRN.scanMany("child", Seq.of(KEY_T, CAT1_T, CAT2_T, VALUE_T));

    // one Expand variant: Key and Value pass through, the two cat columns
    // become uninterpreted projections of the input row (same names on both
    // sides of the rule = same uninterpreted projection symbol)
    static RelRN branch(RelRN s, String p1, String p2) {
        return s.project(Seq.of(
            s.field(0),
            s.proj(p1, "Cat1"),
            s.proj(p2, "Cat2"),
            s.field(3)));
    }

    static RelRN expand(RelRN s) {
        return branch(s, "c1a", "c2a")
            .union(true,
                branch(s, "c1b", "c2b"),
                branch(s, "c1c", "c2c"));
    }

    // the rule's aggregation stack above the Expand. The inner count
    // distinct over Value per (Key, cat1', cat2') group is written as its
    // defining decomposition:
    //   L1: pure group-by over all four expanded columns — dedup of the
    //       (Key, cat1', cat2', Value) pairs (set semantics: erases the
    //       row multiplicities the pushed-down dedup changes),
    //   L2: count of Value per group over those unique pairs,
    //   L3: the rule's outer aggregation, grouped by Key
    static RelRN wrap(RelRN s) {
        RelRN e = expand(s);
        RelRN pairs = new RelRN.Aggregate(
            e,
            Seq.of(e.field(0), e.field(1), e.field(2), e.field(3)),
            Seq.empty());
        RelRN counts = new RelRN.Aggregate(
            pairs,
            Seq.of(pairs.field(0), pairs.field(1), pairs.field(2)),
            Seq.of(new RelRN.AggCall("count", false, CNT_T,
                Seq.of(pairs.field(3)))));
        return new RelRN.Aggregate(
            counts,
            Seq.of(counts.field(0)),
            Seq.of(new RelRN.AggCall("outeragg", false, AGG2_T,
                Seq.of(counts.field(3)))));
    }

    // the deduplication the rule pushes below the Expand: group by all
    // child columns referenced by the Expand, no aggregate calls
    static final RelRN dedup = new RelRN.Aggregate(
        child,
        Seq.of(child.field(0), child.field(1), child.field(2), child.field(3)),
        Seq.empty());

    @Override
    public RelRN before() {
        return wrap(child);
    }

    @Override
    public RelRN after() {
        return wrap(dedup);
    }
}