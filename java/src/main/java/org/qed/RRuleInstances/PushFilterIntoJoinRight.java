package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — INNER join only (no Semi/Left/Full/Anti), one right-bound conjunct with no outer columns, one unbound conjunct, single-column inputs
public record PushFilterIntoJoinRight() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");

    // Shared uninterpreted symbols: f is the right-bound conjunct (references
    // only R's column, no outer columns); g is the unbound conjunct (may
    // reference either side) that stays in the ON clause.
    static final SqlOperator fOp = RuleBuilder.create().genericPredicateOp("right_bound_conj", true);
    static final SqlOperator gOp = RuleBuilder.create().genericPredicateOp("unbound_conj", true);

    // Join row of L ⋈ R: column 0 = L.c0, column 1 = R.c0.
    static final RexRN lJoinRef = left.joinField(0, right);
    static final RexRN rJoinRef = left.joinField(1, right);

    // f and g in the join-row context (the ON clause of before()).
    static final RexRN fJoinCond = new RexRN.Pred(fOp, Seq.of(rJoinRef));
    static final RexRN gJoinCond = new RexRN.Pred(gOp, Seq.of(lJoinRef, rJoinRef));

    // Row-scoped occurrence of the pushed-down conjunct f, over R's own row.
    static final RexRN fRowCond = right.field(0).pred(fOp);

    @Override
    public RelRN before() {
        // InnerJoin(L, R, f(R.c0) AND g(L.c0, R.c0))
        return left.join(JoinRelType.INNER, RexRN.and(fJoinCond, gJoinCond), right);
    }

    @Override
    public RelRN after() {
        // InnerJoin(L, Select(R, f(R.c0)), g(L.c0, R.c0)) — the right-bound
        // conjunct is pushed into the right side; the unbound conjunct
        // remains in the ON clause.
        return left.join(JoinRelType.INNER, gJoinCond, right.filter(fRowCond));
    }
}
