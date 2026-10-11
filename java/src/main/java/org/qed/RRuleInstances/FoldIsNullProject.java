package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;

// SCOPE: PARTIAL — fixed to a single "x IS NULL" projection on a NOT NULL column x (plus a nullable passthrough column); the source rule folds an arbitrary non-empty set of such projections.
public record FoldIsNullProject() implements RRule {
    // Input: column 0 (c) is NOT NULL — the column folded by the rule; column 1 (d) is a nullable passthrough.
    static final RelRN input = RelRN.scanMany("Source", Seq.of(
            new RelType.VarType("C_Type", false),
            new RelType.VarType("D_Type", true)));

    // "c IS NULL" projection — folds to false since c is NOT NULL.
    static final RexRN isNullC = new RexRN.Pred(SqlStdOperatorTable.IS_NULL, Seq.of(input.field(0)));

    // Nullable passthrough column.
    static final RexRN passthrough = input.field(1);

    @Override
    public RelRN before() {
        return input.project(Seq.of(isNullC, passthrough));
    }

    @Override
    public RelRN after() {
        return input.project(Seq.of(RexRN.falseLiteral(), passthrough));
    }
}
