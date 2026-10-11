package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// ORIGIN: Calcite
// SCOPE: PARTIAL — fixed-shape whole-expressions mode: 2-column input, projection (E(x,y), F(x)) pushed through filter P(E(x,y), y) by hoisting E and F into an intermediate projection
public record ProjectFilterTranspose() implements RRule {
    static final RelRN input = RelRN.scanMany("S", Seq.of(
            RexRN.varType("X_Type", true),
            RexRN.varType("Y_Type", true)));

    static final RexRN x = input.field(0);
    static final RexRN y = input.field(1);

    static final SqlOperator eOp =
            RuleBuilder.create().genericProjectionOp("E", new RelType.VarType("E_Type", true));
    static final SqlOperator fOp =
            RuleBuilder.create().genericProjectionOp("F", new RelType.VarType("F_Type", true));
    static final SqlOperator pOp =
            RuleBuilder.create().genericPredicateOp("P", true);

    static final RexRN e = new RexRN.Proj(eOp, Seq.of(x, y));
    static final RexRN f = new RexRN.Proj(fOp, Seq.of(x));
    static final RexRN beforeCond = new RexRN.Pred(pOp, Seq.of(e, y));

    static final RelRN lowered = input.project(Seq.of(x, y, e, f));
    static final RexRN afterCond = new RexRN.Pred(pOp, Seq.of(lowered.field(2), lowered.field(1)));
    static final RelRN after = lowered.filter(afterCond).project(Seq.of(lowered.field(2), lowered.field(3)));

    @Override
    public RelRN before() {
        return input.filter(beforeCond).project(Seq.of(e, f));
    }

    @Override
    public RelRN after() {
        return after;
    }
}
