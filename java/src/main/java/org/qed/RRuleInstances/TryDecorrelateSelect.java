package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — INNER join only, single-column L/R, generic uninterpreted
// ON and right-side-filter predicates. The source rule's guard requires
// $right to have outer columns (i.e. it fires only inside correlated/Apply
// subtrees), but that guard is a scope-limiting heuristic for when the
// optimizer chooses to fire the rule, not a soundness precondition: the
// identity Join(L, Select(R, pred), on) = Join(L, R, on AND pred) holds for
// any right-side predicate regardless of whether it references outer
// columns, because "pred" is evaluated per-row of R either way (as a
// pre-filter, or folded into the join's row-level ON test) and the set of R
// rows that survive to be matched against a given L row is identical in
// both formulations.
public record TryDecorrelateSelect() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");

    static final SqlOperator onOp = RuleBuilder.create().genericPredicateOp("on_cond", true);
    static final SqlOperator predOp = RuleBuilder.create().genericPredicateOp("select_filter", true);

    static final RexRN lJoinRef = left.joinField(0, right);
    static final RexRN rJoinRef = left.joinField(1, right);

    static final RexRN onJoinCond = new RexRN.Pred(onOp, Seq.of(lJoinRef, rJoinRef));
    static final RexRN predJoinCond = new RexRN.Pred(predOp, Seq.of(rJoinRef));
    static final RexRN predRowCond = right.pred(predOp);

    @Override
    public RelRN before() {
        // InnerJoin(L, Select(R, pred), on)
        return left.join(JoinRelType.INNER, onJoinCond, right.filter(predRowCond));
    }

    @Override
    public RelRN after() {
        // InnerJoin(L, R, on AND pred) — the select's filter is absorbed
        // into the join's ON condition, so R no longer needs to be
        // pre-filtered before the join.
        return left.join(JoinRelType.INNER, RexRN.and(onJoinCond, predJoinCond), right);
    }
}
