package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — each join input has exactly one column, and the join condition is a single uninterpreted predicate over the join's two-column row
public record CommuteRightJoin() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");
    // One shared uninterpreted predicate symbol P for the join's ON condition,
    // always applied to (L column, R column) — the same $on in both forms.
    static final SqlOperator joinCond = RuleBuilder.create().genericPredicateOp("join_cond", true);

    // P over Join(L, R): L's column is field 0, R's is field 1.
    static final RexRN beforeCond = new RexRN.Pred(joinCond, Seq.of(
            left.joinField(0, right),
            left.joinField(1, right)));

    // In the swapped Join(R, L), the join row is (R || L), so L's column is at
    // position 1 and R's at position 0; the same P is still applied to
    // (L column, R column), i.e. argument positions (1, 0).
    static final RexRN lCol = right.joinField(1, left);
    static final RexRN rCol = right.joinField(0, left);
    static final RexRN afterCond = new RexRN.Pred(joinCond, Seq.of(lCol, rCol));

    @Override
    public RelRN before() {
        return left.join(JoinRelType.RIGHT, beforeCond, right);
    }

    @Override
    public RelRN after() {
        // LeftJoin with the inputs swapped; the projection restores the
        // original (L || R) column order.
        return right.join(JoinRelType.LEFT, afterCond, left)
                .project(Seq.of(lCol, rCol));
    }
}
