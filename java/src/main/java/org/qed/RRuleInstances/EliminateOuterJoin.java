package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — only the Filter-over-FullOuter-join → Inner-join branch of the rule's join-type case is modeled, with the null-rejecting predicates restricted to explicit IS_NOT_NULL on each side's column; the Aggregate and Project join-elimination cases and the other join-type branches are omitted
public record EliminateOuterJoin() implements RRule {
    static final RelRN left = RelRN.scan("L", "V");
    static final RelRN right = RelRN.scan("R", "V");

    // g: the join's ON condition, unchanged by the rewrite.
    static final RexRN on = left.joinPred("on", right);

    // q: an arbitrary remaining conjunct of the filter, over the joined row.
    static final RexRN q = left.joinPred("q", right);

    // The null-rejecting conjuncts, seen from above the join: IS_NOT_NULL on
    // each side's column as it appears in the join row (field 0 = left, field 1 = right).
    // In a full outer join, the rows beyond an inner join's are exactly the
    // NULL-extended unmatched rows, so these two conjuncts remove precisely them.
    static final RexRN rejL = new RexRN.Pred(SqlStdOperatorTable.IS_NOT_NULL,
            Seq.of(left.joinField(0, right)));
    static final RexRN rejR = new RexRN.Pred(SqlStdOperatorTable.IS_NOT_NULL,
            Seq.of(left.joinField(1, right)));
    static final RexRN p = RexRN.and(q, rejL, rejR);

    @Override
    public RelRN before() {
        // Filter(q AND IS_NOT_NULL(l) AND IS_NOT_NULL(r), FullOuterJoin(L, R, on))
        return left.join(JoinRelType.FULL, on, right).filter(p);
    }

    @Override
    public RelRN after() {
        // Filter(same condition, InnerJoin(L, R, on)) — only the join kind is
        // flipped (FULL -> INNER); the identical filter remains above, exactly
        // as in the source rule (j.copy(joinType = newJoinType)).
        return left.join(JoinRelType.INNER, on, right).filter(p);
    }
}
