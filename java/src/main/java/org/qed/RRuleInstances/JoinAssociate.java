package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Calcite
public record JoinAssociate() implements RRule {
    static final RelRN A = RelRN.scan("A", "A_Type");
    static final RelRN B = RelRN.scan("B", "B_Type");
    static final RelRN C = RelRN.scan("C", "C_Type");

    static final SqlOperator pab = RuleBuilder.create().genericPredicateOp("pab", true);
    static final SqlOperator pb = RuleBuilder.create().genericPredicateOp("pb", true);
    static final SqlOperator pabc = RuleBuilder.create().genericPredicateOp("pabc", true);
    static final SqlOperator pbc = RuleBuilder.create().genericPredicateOp("pbc", true);
    static final SqlOperator pc = RuleBuilder.create().genericPredicateOp("pc", true);

    static final RelRN abJoin = A.join(JoinRelType.INNER, RexRN.and(
            new RexRN.Pred(pab, Seq.of(A.joinField(0, B), A.joinField(1, B))),
            new RexRN.Pred(pb, Seq.of(A.joinField(1, B)))), B);

    static final RelRN beforeJoin = abJoin.join(JoinRelType.INNER, RexRN.and(
            new RexRN.Pred(pabc, Seq.of(abJoin.joinField(0, C), abJoin.joinField(1, C), abJoin.joinField(2, C))),
            new RexRN.Pred(pbc, Seq.of(abJoin.joinField(1, C), abJoin.joinField(2, C))),
            new RexRN.Pred(pc, Seq.of(abJoin.joinField(2, C)))), C);

    static final RelRN bcJoin = B.join(JoinRelType.INNER, RexRN.and(
            new RexRN.Pred(pb, Seq.of(B.joinField(0, C))),
            new RexRN.Pred(pbc, Seq.of(B.joinField(0, C), B.joinField(1, C))),
            new RexRN.Pred(pc, Seq.of(B.joinField(1, C)))), C);

    static final RelRN afterJoin = A.join(JoinRelType.INNER, RexRN.and(
            new RexRN.Pred(pab, Seq.of(A.joinField(0, bcJoin), A.joinField(1, bcJoin))),
            new RexRN.Pred(pabc, Seq.of(A.joinField(0, bcJoin), A.joinField(1, bcJoin), A.joinField(2, bcJoin)))), bcJoin);

    @Override
    public RelRN before() {
        return beforeJoin;
    }

    @Override
    public RelRN after() {
        return afterJoin;
    }
}
