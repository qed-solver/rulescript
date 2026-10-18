package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — one filter with one attribute mapped to a constant via an equality conjunct (modeled as an INNER join a = c on a unique single-column relation, since the DSL has no typed constant literals); the constant is inlined into exactly one other uninterpreted conjunct, and the equality conjunct itself is kept on both sides.
public record ConstantPropagation() implements RRule {
    // Scan of a table with one variable column `a`.
    static final RelRN source = RelRN.scan("Source", "Source_Type");

    // The propagated constant modeled as a unique single-column relation `c`
    // (a constant value occupies at most one row, so the column is a key).
    static final RelRN constRel = RelRN.scan("Const", RexRN.varType("Const_Type", false), true);

    // Field references in the join output: 0 = a (variable), 1 = c (constant).
    static final RexRN aRef = source.joinField(0, constRel);
    static final RexRN cRef = source.joinField(1, constRel);

    // The equality conjunct a = c — this is the propagated mapping
    // (attribute => constant) that Spark's rule reads off the filter condition.
    static final RexRN.Pred eqCond = new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(aRef, cRef));

    // The filter's input: every row of the join satisfies a = c, so `c` is a
    // constant value equal to `a` on all rows.
    static final RelRN input = source.join(JoinRelType.INNER, eqCond, constRel);

    // Uninterpreted predicate standing in for another conjunct of the
    // condition that references the variable column.
    static final SqlOperator gOp = RuleBuilder.create().genericPredicateOp("g", true);

    // Before: the other conjunct references the variable column `a`, and the
    // equality conjunct is still present.
    static final RexRN.Pred gOnA = new RexRN.Pred(gOp, Seq.of(aRef));
    static final RexRN beforeCond = RexRN.and(eqCond, gOnA);

    // After: the constant column `c` has been inlined into that conjunct.
    static final RexRN.Pred gOnC = new RexRN.Pred(gOp, Seq.of(cRef));
    static final RexRN afterCond = RexRN.and(eqCond, gOnC);

    @Override
    public RelRN before() {
        return input.filter(beforeCond);
    }

    @Override
    public RelRN after() {
        return input.filter(afterCond);
    }
}
