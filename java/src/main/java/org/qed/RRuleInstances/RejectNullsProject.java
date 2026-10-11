package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — the projection consists of a single synthesized column `-a` (concrete UNARY_MINUS over the one nullable input column `a`) plus one passthrough column, and the parent select filter is exactly IS NOT NULL on the synthesized column; the general null-transmission analysis of the original rule (arbitrary projections, RejectNullCols metadata) is not modeled.
public record RejectNullsProject() implements RRule {
    // Input: one nullable column `a` (the candidate null-transmitting column).
    static final RelRN source = RelRN.scan("Source", "INTEGER");
    static final RexRN a = source.field(0);
    // Synthesized column c = -a; UNARY_MINUS is null-strict, so c is NULL iff a is NULL.
    static final RexRN c = new RexRN.Proj(SqlStdOperatorTable.UNARY_MINUS, Seq.of(a));

    @Override
    public RelRN before() {
        RelRN proj = source.project(Seq.of(c, a));
        // Parent Select rejects nulls on the synthesized (projection) column c.
        return proj.filter(proj.field(0).pred(SqlStdOperatorTable.IS_NOT_NULL));
    }

    @Override
    public RelRN after() {
        // The "a IS NOT NULL" null-rejecting filter is added to the input of the
        // project; the original parent filter is retained.
        RelRN proj = source
            .filter(a.pred(SqlStdOperatorTable.IS_NOT_NULL))
            .project(Seq.of(c, a));
        return proj.filter(proj.field(0).pred(SqlStdOperatorTable.IS_NOT_NULL));
    }
}
