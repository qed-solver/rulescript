package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — the mutation's input has exactly two columns of which only the first is needed, and the mutation's per-row behavior is an uninterpreted function of exactly that needed column (its other children — unique checks, fk checks, private data — are held fixed)
public record PruneMutationInputCols() implements RRule {
    static final RelRN input = RelRN.scanMany("I", Seq.of(
            new RelType.VarType("I0_Type", true),
            new RelType.VarType("I1_Type", true)));
    static final SqlOperator mOp = RuleBuilder.create()
            .genericProjectionOp("M", new RelType.VarType("M_Type", true));
    static final RelRN beforeRel =
            input.project(new RexRN.Proj(mOp, Seq.of(input.field(0))));
    static final RelRN prunedInput = input.project(input.field(0));
    static final RelRN afterRel =
            prunedInput.project(new RexRN.Proj(mOp, Seq.of(prunedInput.field(0))));

    @Override
    public RelRN before() {
        return beforeRel;
    }

    @Override
    public RelRN after() {
        return afterRel;
    }
}
