package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — single-column input, one grouping key that is a scalar deterministic PythonUDF g applied to that column, and one non-distinct aggregate f over the same column.
public record ExtractGroupingPythonUDFFromAggregate() implements RRule {
    static final RelRN source = RelRN.scan("R", "R_Type");

    // The scalar PythonUDF applied to the input's single column x.
    // Model it as an uninterpreted deterministic projection "g".
    // Group key before extraction: g(x).
    static final RexRN groupKey = source.groupBy("g");

    // aggregate f(x)
    static final RelRN.AggCall agg = source.aggCall("f");

    static final RelRN before = new RelRN.Aggregate(source, Seq.of(groupKey), Seq.of(agg));

    // After: extract g(x) into a projection below the aggregate.
    // Project([g(x), x], R) -> cols: 0 = g(x) [g_type], 1 = x [R_Type]
    static final RexRN gOfX = new RexRN.Proj(
            RuleBuilder.create().genericProjectionOp("g", RexRN.varType("g_type", true)),
            Seq.of(source.field(0)));
    static final RelRN extracted = source.project(Seq.of(gOfX, source.field(0)));
    static final RelRN after = new RelRN.Aggregate(extracted,
            Seq.of(extracted.field(0)),
            Seq.of(extracted.field(1).aggCall("f")));

    @Override public RelRN before() { return before; }
    @Override public RelRN after() { return after; }
}
