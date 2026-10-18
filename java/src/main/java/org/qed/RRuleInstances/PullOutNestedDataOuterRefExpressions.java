package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;
import kala.collection.Seq;

// ORIGIN: Spark
// SCOPE: PARTIAL — one uninterpreted GetMapValue extraction over an outer column, used in one uninterpreted comparison with an inner column; the correlation context is modeled as an INNER cross-join of the outer and inner scans
public record PullOutNestedDataOuterRefExpressions() implements RRule {
    static final RelRN outer = RelRN.scan("Outer", "M");
    static final RelRN inner = RelRN.scan("Inner", "V");
    static final RelRN base = outer.join(JoinRelType.INNER, RexRN.trueLiteral(), inner);
    static final RexRN o = base.field(0);
    static final RexRN i = base.field(1);
    static final SqlOperator gmvOp = RuleBuilder.create()
            .genericProjectionOp("Gmv", new RelType.VarType("V", true));
    static final SqlOperator cmpOp = RuleBuilder.create().genericPredicateOp("Cmp", true);
    static final RexRN gmvO = new RexRN.Proj(gmvOp, Seq.of(o));

    @Override
    public RelRN before() {
        return base.filter(new RexRN.Pred(cmpOp, Seq.of(gmvO, i)));
    }

    @Override
    public RelRN after() {
        RelRN projected = base.project(Seq.of(o, i, gmvO));
        RelRN filtered = projected.filter(new RexRN.Pred(cmpOp, Seq.of(projected.field(2), projected.field(1))));
        return filtered.project(Seq.of(filtered.field(0), filtered.field(1)));
    }
}
