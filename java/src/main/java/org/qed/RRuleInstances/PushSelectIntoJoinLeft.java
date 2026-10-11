package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — LEFT join only (not the also-eligible INNER/SEMI/ANTI or the Apply variants); one left-bound and one unbound filter conjunct; one ON condition; single-column inputs
public record PushSelectIntoJoinLeft() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");

    // f: the left-bound filter conjunct (references only L's column, no outer
    //    columns) — the condition the rule pushes into the left input.
    // u: an unbound filter conjunct (may reference both sides) — stays in a
    //    Select above the join, per ExtractUnboundConditions.
    // g: the ON condition of the join, unchanged by the rewrite.
    static final SqlOperator fOp = RuleBuilder.create().genericPredicateOp("left_bound_filter", true);
    static final SqlOperator uOp = RuleBuilder.create().genericPredicateOp("unbound_filter", true);
    static final SqlOperator gOp = RuleBuilder.create().genericPredicateOp("join_on", true);

    // Join row of L join R: column 0 = L.c0, column 1 = R.c0.
    static final RexRN lJoinRef = left.joinField(0, right);
    static final RexRN rJoinRef = left.joinField(1, right);

    // All three conditions in the join-row context (used by the Selects above
    // the join on both sides of the rewrite).
    static final RexRN fJoinCond = new RexRN.Pred(fOp, Seq.of(lJoinRef));
    static final RexRN uJoinCond = new RexRN.Pred(uOp, Seq.of(lJoinRef, rJoinRef));
    static final RexRN onCond    = new RexRN.Pred(gOp, Seq.of(lJoinRef, rJoinRef));

    // Row-scoped occurrence of the pushed-down conjunct f, over L's own row.
    static final RexRN fRowCond = left.field(0).pred(fOp);

    @Override
    public RelRN before() {
        // Select(LeftJoin(L, R, g), f AND u)
        return left.join(JoinRelType.LEFT, onCond, right)
                .filter(RexRN.and(fJoinCond, uJoinCond));
    }

    @Override
    public RelRN after() {
        // Select(LeftJoin(Select(L, f), R, g), u) — the left-bound conjunct is
        // pushed into the left input; the unbound conjunct remains above.
        return left.filter(fRowCond)
                .join(JoinRelType.LEFT, onCond, right)
                .filter(uJoinCond);
    }
}
