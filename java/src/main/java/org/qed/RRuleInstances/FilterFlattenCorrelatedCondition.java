package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

import kala.collection.Seq;

// ORIGIN: Calcite
public record FilterFlattenCorrelatedCondition() implements RRule {
    static final RelRN outer = RelRN.scan("Outer", "T");
    static final RelRN inner = RelRN.scan("Inner", "T2");

    static final RelRN base = outer.join(JoinRelType.INNER, RexRN.trueLiteral(), inner);
    static final RexRN o = base.field(0);
    static final RexRN i = base.field(1);

    static final SqlOperator cmpOp = RuleBuilder.create().genericPredicateOp("Cmp", true);
    static final SqlOperator fOp = RuleBuilder.create()
            .genericProjectionOp("Expr", new RelType.VarType("T", true));

    static final RexRN exprI = new RexRN.Proj(fOp, Seq.of(i));

    static final RexRN cond = new RexRN.Pred(cmpOp, Seq.of(o, exprI));

    @Override
    public RelRN before() {
        return base.filter(cond);
    }

    @Override
    public RelRN after() {
        RelRN projected = base.project(Seq.of(o, i, exprI));
        RexRN condAfter = new RexRN.Pred(cmpOp, Seq.of(projected.field(0), projected.field(2)));
        RelRN filtered = projected.filter(condAfter);
        return filtered.project(Seq.of(filtered.field(0), filtered.field(1)));
    }
}
