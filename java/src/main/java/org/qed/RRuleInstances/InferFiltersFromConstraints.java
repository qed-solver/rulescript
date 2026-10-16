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
// SCOPE: PARTIAL — inner joins only, over two one-column scans with no pre-existing constraints, whose join condition is an equality between the left key and the right key plus one conjunct over the left key only; the rule adds the inferred left filter (left-only conjunct AND left key IS NOT NULL) and right filter (right key IS NOT NULL), leaving the join condition unchanged
public record InferFiltersFromConstraints() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Key_Type");
    static final RelRN right = RelRN.scan("R", "R_Key_Type");

    // The two columns of the join row (L's column, R's column).
    static final RexRN lcol = left.joinField(0, right);
    static final RexRN rcol = left.joinField(1, right);

    // The equality conjunct L.col = R.col (null-rejecting: holds only when both
    // sides are non-null, so it implies IS NOT NULL on each key).
    static final RexRN eq = new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(lcol, rcol));

    // A conjunct over the left key only, as in Spark's `a > 5` example; it
    // references only L's output, so Spark infers it as a filter on the left
    // side while keeping it in the join condition.
    static final SqlOperator g = RuleBuilder.create().genericPredicateOp("g", true);
    static final RexRN leftOnly = new RexRN.Pred(g, Seq.of(lcol));
    static final RexRN cond = RexRN.and(eq, leftOnly);

    // IS NOT NULL constraints Spark constructs from the null-rejecting equality,
    // one per side (references restricted to that side's output).
    static final RexRN lNotNull =
            new RexRN.Pred(SqlStdOperatorTable.IS_NOT_NULL, Seq.of(left.field(0)));
    static final RexRN rNotNull =
            new RexRN.Pred(SqlStdOperatorTable.IS_NOT_NULL, Seq.of(right.field(0)));

    // The filter added above the left input: the left-only conjunct (L's own
    // column 0, the same symbol as lcol in the join row) plus the inferred
    // IS NOT NULL; the filter added above the right input: the inferred
    // IS NOT NULL.
    static final RexRN leftFilter =
            RexRN.and(new RexRN.Pred(g, Seq.of(left.field(0))), lNotNull);

    @Override
    public RelRN before() {
        return left.join(JoinRelType.INNER, cond, right);
    }

    @Override
    public RelRN after() {
        RelRN newLeft = left.filter(leftFilter);
        RelRN newRight = right.filter(rNotNull);
        return newLeft.join(JoinRelType.INNER, cond, newRight);
    }
}