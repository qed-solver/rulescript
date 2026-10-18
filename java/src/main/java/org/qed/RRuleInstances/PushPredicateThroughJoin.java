package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — INNER join only; the filter splits into exactly one left-only, one right-only, and one common conjunct; single-column inputs
public record PushPredicateThroughJoin() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");

    // One uninterpreted predicate per filter conjunct category: left-only,
    // right-only, and common (refers to both sides). The same operator
    // instance (hence the same name) is reused in before() and after() so
    // QED treats the occurrences as the same predicate symbol.
    static final SqlOperator leftOp = RuleBuilder.create().genericPredicateOp("left_conj", true);
    static final SqlOperator rightOp = RuleBuilder.create().genericPredicateOp("right_conj", true);
    static final SqlOperator commonOp = RuleBuilder.create().genericPredicateOp("common_conj", true);
    static final SqlOperator joinOp = RuleBuilder.create().genericPredicateOp("join_cond", true);

    // Join row of L join R (single-column inputs): column 0 = L.c0, column 1 = R.c0.
    static final RexRN lJoinRef = left.joinField(0, right);
    static final RexRN rJoinRef = left.joinField(1, right);

    // Join-row occurrences of each conjunct (all bound into the join row).
    static final RexRN leftJoinRefCond = new RexRN.Pred(leftOp, Seq.of(lJoinRef));
    static final RexRN rightJoinRefCond = new RexRN.Pred(rightOp, Seq.of(rJoinRef));
    static final RexRN commonCond = new RexRN.Pred(commonOp, Seq.of(lJoinRef, rJoinRef));
    static final RexRN joinCond = new RexRN.Pred(joinOp, Seq.of(lJoinRef, rJoinRef));

    // Row-scoped occurrences of the two single-side conjuncts, pushed into
    // the respective inputs.
    static final RexRN leftRowCond = left.field(0).pred(leftOp);
    static final RexRN rightRowCond = right.field(0).pred(rightOp);

    @Override
    public RelRN before() {
        // Filter(P_l AND P_r AND P_c, Join(L, R, INNER, J))
        return left.join(JoinRelType.INNER, joinCond, right)
                .filter(RexRN.and(leftJoinRefCond, rightJoinRefCond, commonCond));
    }

    @Override
    public RelRN after() {
        // Join(Filter(P_l, L), Filter(P_r, R), INNER, J AND P_c)
        return left.filter(leftRowCond)
                .join(JoinRelType.INNER, RexRN.and(joinCond, commonCond), right.filter(rightRowCond));
    }
}