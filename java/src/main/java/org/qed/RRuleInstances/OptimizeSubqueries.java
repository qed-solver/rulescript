package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;
import kala.collection.Seq;

// ORIGIN: Spark
// SCOPE: PARTIAL — only the projection-insertion step of the Exists case: prune subquery output to the columns referenced in the EXISTS join condition
public record OptimizeSubqueries() implements RRule {

    static final RelRN L = RelRN.scanMany("L", Seq.of(RexRN.varType("L_type", true)));
    static final RelRN R = RelRN.scanMany("R", Seq.of(
            RexRN.varType("R0_type", true),
            RexRN.varType("R1_type", true),
            RexRN.varType("R2_type", true)));
    static final RelRN R2 = R.project(R.fields(0, 2));

    @Override
    public RelRN before() {
        return L.join(JoinRelType.SEMI, new RexRN.Pred(
                RuleBuilder.create().genericPredicateOp("exists_cond", true),
                Seq.of(L.joinField(0, R), L.joinField(1, R), L.joinField(3, R))), R);
    }

    @Override
    public RelRN after() {
        return L.join(JoinRelType.SEMI, new RexRN.Pred(
                RuleBuilder.create().genericPredicateOp("exists_cond", true),
                Seq.of(L.joinField(0, R2), L.joinField(1, R2), L.joinField(2, R2))), R2);
    }
}