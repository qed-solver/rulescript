package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RexRN.Proj;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — the input's inlinable constant column is modeled as an INNER equality join a = c (concrete EQUALS) against a unique single-column relation (the DSL has no typed constant literals), and one occurrence of the variable column in one uninterpreted projection item is inlined to the constant column.
public record InlineProjectConstants() implements RRule {
    // Scan of a table with one variable column `a`.
    static final RelRN source = RelRN.scan("Source", "Source_Type");

    // The inlinable constant modeled as a unique single-column relation `c`
    // (a constant value occupies at most one row, so the column is a key).
    static final RelRN constRel = RelRN.scan("Const", RexRN.varType("Const_Type", false), true);

    // Field references in the join input: 0 = a (variable), 1 = c (constant).
    static final RexRN aRef = source.joinField(0, constRel);
    static final RexRN cRef = source.joinField(1, constRel);

    // The equality join condition a = c restricting `a` to the constant `c`.
    static final RexRN.Pred eqCond = new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(aRef, cRef));

    // The project's input: field 1 is the inlinable constant, and field 0 is
    // the variable that equals it on every row of the join output.
    static final RelRN input = source.join(JoinRelType.INNER, eqCond, constRel);

    // Uninterpreted projection function standing in for an expression over the
    // variable column (and its type).
    static final SqlOperator fOp = RuleBuilder.create().genericProjectionOp("f", RexRN.varType("f_type", true));

    // Before: the projection item references the variable column `a`.
    static final Proj onA = new Proj(fOp, Seq.of(aRef));

    // After: the reference has been inlined to the constant column `c`.
    static final Proj onC = new Proj(fOp, Seq.of(cRef));

    @Override
    public RelRN before() {
        return input.project(onA);
    }

    @Override
    public RelRN after() {
        return input.project(onC);
    }
}
