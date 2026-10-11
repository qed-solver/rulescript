package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// ORIGIN: Calcite
// SCOPE: PARTIAL — fixed shape: 2-column input; each Calc has one uninterpreted filter predicate over both input columns and exactly two uninterpreted projection expressions, each over both columns
public record CalcMerge() implements RRule {
    static final RelRN source = RelRN.scanMany("S", Seq.of(
            RexRN.varType("X_Type", true),
            RexRN.varType("Y_Type", true)));
    static final RexRN x = source.field(0);
    static final RexRN y = source.field(1);

    static final SqlOperator bCondOp = RuleBuilder.create().genericPredicateOp("BCond", true);
    static final SqlOperator b1Op =
            RuleBuilder.create().genericProjectionOp("B1", new RelType.VarType("B1_Type", true));
    static final SqlOperator b2Op =
            RuleBuilder.create().genericProjectionOp("B2", new RelType.VarType("B2_Type", true));
    static final RexRN bottomCond = new RexRN.Pred(bCondOp, Seq.of(x, y));
    static final RexRN b1 = new RexRN.Proj(b1Op, Seq.of(x, y));
    static final RexRN b2 = new RexRN.Proj(b2Op, Seq.of(x, y));

    static final SqlOperator tCondOp = RuleBuilder.create().genericPredicateOp("TCond", true);
    static final SqlOperator t1Op =
            RuleBuilder.create().genericProjectionOp("T1", new RelType.VarType("T1_Type", true));
    static final SqlOperator t2Op =
            RuleBuilder.create().genericProjectionOp("T2", new RelType.VarType("T2_Type", true));

    static final RelRN bottom = source.filter(bottomCond).project(Seq.of(b1, b2));
    static final RexRN c0 = bottom.field(0);
    static final RexRN c1 = bottom.field(1);
    static final RexRN topCond = new RexRN.Pred(tCondOp, Seq.of(c0, c1));
    static final RexRN top1 = new RexRN.Proj(t1Op, Seq.of(c0, c1));
    static final RexRN top2 = new RexRN.Proj(t2Op, Seq.of(c0, c1));

    static final RexRN mergedCond = new RexRN.Pred(tCondOp, Seq.of(b1, b2));
    static final RexRN merged1 = new RexRN.Proj(t1Op, Seq.of(b1, b2));
    static final RexRN merged2 = new RexRN.Proj(t2Op, Seq.of(b1, b2));

    @Override
    public RelRN before() {
        return bottom.filter(topCond).project(Seq.of(top1, top2));
    }

    @Override
    public RelRN after() {
        return source.filter(RexRN.and(bottomCond, mergedCond)).project(Seq.of(merged1, merged2));
    }
}
