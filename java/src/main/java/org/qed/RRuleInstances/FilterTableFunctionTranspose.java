package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Calcite
public record FilterTableFunctionTranspose() implements RRule {
    static final RelRN input = RelRN.scanMany("S", Seq.of(
            RexRN.varType("S0_Type", true),
            RexRN.varType("S1_Type", true)));

    static final RelRN aux = RelRN.scanMany("T", Seq.of(
            RexRN.varType("T_Type", true)));

    static final SqlOperator jOp = RuleBuilder.create().genericPredicateOp("J", true);
    static final SqlOperator pOp = RuleBuilder.create().genericPredicateOp("P", true);

    static RelRN tableFunction(RelRN left) {
        return left.join(JoinRelType.INNER, left.joinPred(jOp, aux), aux)
                .project(Seq.of(left.joinField(0, aux), left.joinField(1, aux)));
    }

    @Override
    public RelRN before() {
        RelRN tfs = tableFunction(input);
        return tfs.filter(new RexRN.Pred(pOp, Seq.of(tfs.field(0), tfs.field(1))));
    }

    @Override
    public RelRN after() {
        RelRN filtered = input.filter(new RexRN.Pred(pOp, Seq.of(input.field(0), input.field(1))));
        return tableFunction(filtered);
    }
}
