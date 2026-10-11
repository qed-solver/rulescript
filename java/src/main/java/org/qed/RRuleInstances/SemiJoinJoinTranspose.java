package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public record SemiJoinJoinTranspose() implements RRule {
    static final RelRN X = RelRN.scan("X", "Key_Type");
    static final RelRN Y = RelRN.scan("Y", "Key_Type");
    static final RelRN Z = RelRN.scan("Z", "Key_Type");

    static final SqlOperator cOp = RuleBuilder.create().genericPredicateOp("C", true);
    static final SqlOperator sOp = RuleBuilder.create().genericPredicateOp("S", true);

    static final RexRN C = new RexRN.Pred(cOp,
            Seq.of(X.joinField(0, Y), X.joinField(1, Y)));
    static final RelRN J = X.join(JoinRelType.INNER, C, Y);

    static final RexRN S = new RexRN.Pred(sOp,
            Seq.of(J.joinField(0, Z), J.joinField(2, Z)));

    @Override
    public RelRN before() {
        return J.join(JoinRelType.SEMI, S, Z);
    }

    @Override
    public RelRN after() {
        RelRN xz = X.join(JoinRelType.SEMI,
                new RexRN.Pred(sOp, Seq.of(X.joinField(0, Z), X.joinField(1, Z))), Z);
        return xz.join(JoinRelType.INNER,
                new RexRN.Pred(cOp, Seq.of(xz.joinField(0, Y), xz.joinField(1, Y))), Y);
    }
}