package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — Explain's input has exactly two columns of which only the first is needed by the fixed explain physical properties, and Explain's output rows are uninterpreted functions of exactly that needed column
public record PruneExplainCols() implements RRule {
    // Explain's input: two columns — col 0 is the column the required
    // physical properties need (NeededExplainCols), col 1 is the column the
    // rule prunes away.
    static final RelRN input = RelRN.scanMany("I", Seq.of(
            new RelType.VarType("I0_Type", true),
            new RelType.VarType("I1_Type", true)));

    // E — the Explain output row, an uninterpreted function of exactly the
    // needed input column. The explain private properties (which determine
    // the needed columns) are shared by both sides and hence fixed.
    static final SqlOperator eOp = RuleBuilder.create()
            .genericProjectionOp("E", new RelType.VarType("E_Type", true));

    // Before: Explain over the full input; the pruned column i1 flows
    // through but is never referenced by the output row.
    static final RelRN beforeRel =
            input.project(new RexRN.Proj(eOp, Seq.of(input.field(0))));

    // After: PruneCols pushes a projection of the needed column below the
    // Explain, which produces the same output row from the pruned input.
    static final RelRN prunedInput = input.project(input.field(0));
    static final RelRN afterRel =
            prunedInput.project(new RexRN.Proj(eOp, Seq.of(prunedInput.field(0))));

    @Override
    public RelRN before() {
        return beforeRel;
    }

    @Override
    public RelRN after() {
        return afterRel;
    }
}
