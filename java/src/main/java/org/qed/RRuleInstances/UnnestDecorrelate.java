package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Calcite
public record UnnestDecorrelate() implements RRule {
    static final RelRN left = RelRN.scanMany("L", Seq.of(
            RexRN.varType("L_Array_Type", true),
            RexRN.varType("L1_Type", true)));

    static final RelRN elements = RelRN.scan("E", "E_Type");

    static final SqlOperator mOp = RuleBuilder.create().genericPredicateOp("M", true);
    static final SqlOperator pOp = RuleBuilder.create()
            .genericProjectionOp("P", RexRN.varType("Result_Type", true));

    static RelRN uncollect(RelRN input) {
        int elemOrdinal = input.semantics().getRowType().getFieldCount();
        RexRN cond = new RexRN.Pred(mOp,
                Seq.of(input.joinField(0, elements), input.joinField(elemOrdinal, elements)));
        return input.join(JoinRelType.INNER, cond, elements);
    }

    @Override
    public RelRN before() {
        RelRN joined = uncollect(left);
        return joined.project(new RexRN.Proj(pOp, Seq.of(joined.field(2))));
    }

    @Override
    public RelRN after() {
        RelRN leftArray = left.project(Seq.of(left.field(0)));
        RelRN joined = uncollect(leftArray);
        return joined.project(new RexRN.Proj(pOp, Seq.of(joined.field(1))));
    }
}
