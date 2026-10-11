package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — INNER join only; the hoisted subquery is an uncorrelated
// EXISTS referencing only its own input table (S), so it can be moved from the
// join's ON condition into a filter on the right input (E is a fixed Boolean
// per row pair and its value is independent of the left row).
public record HoistJoinSubquery() implements RRule {
    // A bare EXISTS subquery over an arbitrary (self-contained) relation.
    record ExistsSub(RelRN body) implements RexRN {
        @Override
        public RexNode semantics() {
            return RexSubQuery.exists(body.semantics());
        }
    }

    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");
    // The subquery's own input table, with a predicate on it.
    static final RelRN sub = RelRN.scan("S", "S_Type");

    static final SqlOperator onOp = RuleBuilder.create().genericPredicateOp("on_cond", true);
    static final SqlOperator sOp = RuleBuilder.create().genericPredicateOp("sub_pred", true);

    static final RexRN lRef = left.joinField(0, right);
    static final RexRN rRef = left.joinField(1, right);
    static final RexRN onCond = new RexRN.Pred(onOp, Seq.of(lRef, rRef));

    static final RexRN subFilter = sub.pred(sOp);
    static final ExistsSub exists = new ExistsSub(sub.filter(subFilter));

    @Override
    public RelRN before() {
        // InnerJoin(L, R, P(l,r) AND EXISTS(Filter(S, s)))
        return left.join(JoinRelType.INNER, RexRN.and(onCond, exists), right);
    }

    @Override
    public RelRN after() {
        // InnerJoin(L, Filter(R, EXISTS(Filter(S, s))), P(l,r))
        return left.join(JoinRelType.INNER, onCond, right.filter(exists));
    }
}
