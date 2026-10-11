package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// ORIGIN: Calcite
// SCOPE: PARTIAL — the input is fixed at 2 columns and the project list is exactly two uninterpreted expressions over them (the original rule applies to any input arity and any expression list); the Calc's absent condition is represented by the constant-TRUE filter, per this DSL's Calc convention
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
