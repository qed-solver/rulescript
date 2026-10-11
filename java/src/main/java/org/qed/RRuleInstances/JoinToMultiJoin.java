package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public record JoinToMultiJoin() implements RRule {
    static final RelRN X = RelRN.scan("X", "X_Type");
    static final RelRN Y = RelRN.scan("Y", "Y_Type");
    static final RelRN Z = RelRN.scan("Z", "Z_Type");

    static final SqlOperator Fm = RuleBuilder.create().genericPredicateOp("Fm", true);
    static final SqlOperator C = RuleBuilder.create().genericPredicateOp("C", true);

    static final RelRN yz = Y.join(JoinRelType.INNER,
            new RexRN.Pred(Fm, Seq.of(Y.joinField(0, Z), Y.joinField(1, Z))), Z);
    static final RelRN beforeJoin = X.join(JoinRelType.INNER,
            new RexRN.Pred(C, Seq.of(X.joinField(0, yz), X.joinField(1, yz), X.joinField(2, yz))), yz);

    static final RelRN yzFree = Y.join(JoinRelType.INNER, RexRN.trueLiteral(), Z);
    static final RelRN afterJoin = X.join(JoinRelType.INNER, RexRN.and(
            new RexRN.Pred(Fm, Seq.of(X.joinField(1, yzFree), X.joinField(2, yzFree))),
            new RexRN.Pred(C, Seq.of(X.joinField(0, yzFree), X.joinField(1, yzFree), X.joinField(2, yzFree)))), yzFree);

    @Override
    public RelRN before() {
        return beforeJoin;
    }

    @Override
    public RelRN after() {
        return afterJoin;
    }
}