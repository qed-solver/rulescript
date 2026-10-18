package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// ORIGIN: Calcite
// SCOPE: PARTIAL — the input is fixed at 2 columns, the bottom Calc is assumed to have exactly one uninterpreted filter predicate over both columns and exactly two uninterpreted projection expressions over both columns, and the top is assumed to be a pure Filter with a single uninterpreted predicate over the bottom's two output columns
public record FilterCalcMerge() implements RRule {
    static final RelRN source = RelRN.scanMany("S", Seq.of(
            RexRN.varType("X_Type", true),
            RexRN.varType("Y_Type", true)));
    static final RexRN x = source.field(0);
    static final RexRN y = source.field(1);

    static final SqlOperator bOp = RuleBuilder.create().genericPredicateOp("B", true);
    static final SqlOperator e0Op =
            RuleBuilder.create().genericProjectionOp("E0", new RelType.VarType("E0_Type", true));
    static final SqlOperator e1Op =
            RuleBuilder.create().genericProjectionOp("E1", new RelType.VarType("E1_Type", true));
    static final RexRN bottomCond = new RexRN.Pred(bOp, Seq.of(x, y));
    static final RexRN e0 = new RexRN.Proj(e0Op, Seq.of(x, y));
    static final RexRN e1 = new RexRN.Proj(e1Op, Seq.of(x, y));

    static final SqlOperator tOp = RuleBuilder.create().genericPredicateOp("T", true);

    static final RelRN bottom = source.filter(bottomCond).project(Seq.of(e0, e1));
    static final RexRN tCond = new RexRN.Pred(tOp, Seq.of(bottom.field(0), bottom.field(1)));

    @Override
    public RelRN before() {
        return bottom.filter(tCond);
    }

    static final RexRN afterCond = new RexRN.Pred(tOp, Seq.of(e0, e1));

    @Override
    public RelRN after() {
        return source.filter(RexRN.and(afterCond, bottomCond)).project(Seq.of(e0, e1));
    }
}
