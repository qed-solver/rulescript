package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — the FinishAnalysis composite's bag-equivalent member rule PullOutGroupingExpressions, restricted to a two-column scan, one group key that is an uninterpreted scalar function g applied to column 0 (not a plain field) and one non-distinct aggregate f applied to column 1, with g depending only on column 0 and f only on column 1.
public record FinishAnalysis() implements RRule {
    // Two-column base relation: col 0 = a (feeds the group key), col 1 = b (feeds the aggregate).
    static final RelRN scan = RelRN.scanMany("R", Seq.of(
            RexRN.varType("a_type", true),
            RexRN.varType("b_type", true)));

    // The complex grouping expression: an uninterpreted deterministic scalar g(a) over col 0,
    // which is NOT a plain output field of the base relation, so it must be materialized.
    static final RexRN gOfA = new RexRN.Proj(
            RuleBuilder.create().genericProjectionOp("g", RexRN.varType("g_type", true)),
            Seq.of(scan.field(0)));

    // The non-distinct aggregate f(b) over col 1.
    static final RelRN.AggCall fOfB = new RelRN.AggCall(
            "f",
            RuleBuilder.create().genericAggregateOp("f", RexRN.varType("f_type", true)),
            false,
            RexRN.varType("f_type", true),
            Seq.of(scan.field(1)));

    // BEFORE: Aggregate(GROUP BY g(a), f(b)) directly over the two-column base.
    // Output row: [g(a), f(b)].
    static final RelRN before = new RelRN.Aggregate(
            scan,
            Seq.of(gOfA),
            Seq.of(fOfB));

    // AFTER: pull the complex grouping expression g(a) into a Project below the Aggregate,
    // keeping both the raw aggregate argument b and the computed key g(a).
    // Project -> col 0 = b [b_type], col 1 = g(a) [g_type].
    static final RelRN extracted = scan.project(Seq.of(
            scan.field(1),      // b: still available as the aggregate argument
            gOfA));             // g(a): the extracted grouping expression

    // Same aggregate, but the group key is now the projected field (col 1 = g(a)) and the
    // aggregate argument is the projected field (col 0 = b). Output row: [g(a), f(b)].
    static final RelRN after = new RelRN.Aggregate(
            extracted,
            Seq.of(extracted.field(1)),
            Seq.of(new RelRN.AggCall(
                    "f",
                    RuleBuilder.create().genericAggregateOp("f", RexRN.varType("f_type", true)),
                    false,
                    RexRN.varType("f_type", true),
                    Seq.of(extracted.field(0)))));

    @Override
    public RelRN before() {
        return before;
    }

    @Override
    public RelRN after() {
        return after;
    }
}
