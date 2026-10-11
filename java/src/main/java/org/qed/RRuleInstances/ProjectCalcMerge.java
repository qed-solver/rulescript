package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// ORIGIN: Calcite
public record ProjectCalcMerge() implements RRule {
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

    static final RelRN bottom = source.filter(bottomCond).project(Seq.of(e0, e1));
    static final RexRN c0 = bottom.field(0);
    static final RexRN c1 = bottom.field(1);

    static final SqlOperator p0Op =
            RuleBuilder.create().genericProjectionOp("P0", new RelType.VarType("P0_Type", true));
    static final SqlOperator p1Op =
            RuleBuilder.create().genericProjectionOp("P1", new RelType.VarType("P1_Type", true));
    static final RexRN p0 = new RexRN.Proj(p0Op, Seq.of(c0, c1));
    static final RexRN p1 = new RexRN.Proj(p1Op, Seq.of(c0, c1));

    @Override
    public RelRN before() {
        return bottom.project(Seq.of(p0, p1));
    }

    static final RexRN merged0 = new RexRN.Proj(p0Op, Seq.of(e0, e1));
    static final RexRN merged1 = new RexRN.Proj(p1Op, Seq.of(e0, e1));

    @Override
    public RelRN after() {
        return source.filter(bottomCond).project(Seq.of(merged0, merged1));
    }
}
