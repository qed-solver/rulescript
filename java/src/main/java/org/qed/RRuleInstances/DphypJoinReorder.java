package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Calcite
// SCOPE: PARTIAL — a single concrete DpHyp-style reordering of a fully-connected 3-way inner-join tree (left-deep (A⋈B)⋈C to right-deep A⋈(B⋈C)) with single-column inputs and uninterpreted conjunctive join conditions, rather than an arbitrary cost-driven reordering of an arbitrary join tree
public record DphypJoinReorder() implements RRule {
    static final RelRN A = RelRN.scan("A", "A_Type");
    static final RelRN B = RelRN.scan("B", "B_Type");
    static final RelRN C = RelRN.scan("C", "C_Type");

    static final SqlOperator pAB = RuleBuilder.create().genericPredicateOp("pAB", true);
    static final SqlOperator pAC = RuleBuilder.create().genericPredicateOp("pAC", true);
    static final SqlOperator pBC = RuleBuilder.create().genericPredicateOp("pBC", true);

    static final RexRN abCond = new RexRN.Pred(pAB, Seq.of(A.joinField(0, B), A.joinField(1, B)));
    static final RelRN abJoin = A.join(JoinRelType.INNER, abCond, B);

    static final RexRN beforeCond = RexRN.and(
            new RexRN.Pred(pAC, Seq.of(abJoin.joinField(0, C), abJoin.joinField(2, C))),
            new RexRN.Pred(pBC, Seq.of(abJoin.joinField(1, C), abJoin.joinField(2, C))));
    static final RelRN beforeJoin = abJoin.join(JoinRelType.INNER, beforeCond, C);

    static final RexRN bcCond = new RexRN.Pred(pBC, Seq.of(B.joinField(0, C), B.joinField(1, C)));
    static final RelRN bcJoin = B.join(JoinRelType.INNER, bcCond, C);

    static final RexRN afterCond = RexRN.and(
            new RexRN.Pred(pAC, Seq.of(A.joinField(0, bcJoin), A.joinField(2, bcJoin))),
            new RexRN.Pred(pAB, Seq.of(A.joinField(0, bcJoin), A.joinField(1, bcJoin))));
    static final RelRN afterJoin = A.join(JoinRelType.INNER, afterCond, bcJoin);

    @Override
    public RelRN before() {
        return beforeJoin;
    }

    @Override
    public RelRN after() {
        return afterJoin;
    }
}
