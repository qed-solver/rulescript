package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// ORIGIN: Calcite
public record ProjectToCalc() implements RRule {
    static final RelRN source = RelRN.scanMany("S", Seq.of(
            RexRN.varType("X_Type", true),
            RexRN.varType("Y_Type", true)));

    static final RexRN x = source.field(0);
    static final RexRN y = source.field(1);

    static final SqlOperator e1Op =
            RuleBuilder.create().genericProjectionOp("E1", new RelType.VarType("E1_Type", true));
    static final SqlOperator e2Op =
            RuleBuilder.create().genericProjectionOp("E2", new RelType.VarType("E2_Type", true));

    static final RexRN e1 = new RexRN.Proj(e1Op, Seq.of(x, y));
    static final RexRN e2 = new RexRN.Proj(e2Op, Seq.of(x, y));

    @Override
    public RelRN before() {
        return source.project(Seq.of(e1, e2));
    }

    @Override
    public RelRN after() {
        return source.filter(RexRN.trueLiteral()).project(Seq.of(e1, e2));
    }
}
