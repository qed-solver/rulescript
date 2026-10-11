package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

public record ProjectTableScan() implements RRule {
    static final RelRN table = RelRN.scanMany("T", Seq.of(
            RexRN.varType("T0_Type", true),
            RexRN.varType("T1_Type", true),
            RexRN.varType("T2_Type", true)));

    static final SqlOperator fOp =
            RuleBuilder.create().genericProjectionOp("F", new RelType.VarType("F_Type", true));

    static final RexRN out0Before = new RexRN.Proj(fOp, Seq.of(table.field(0), table.field(2)));
    static final RexRN out1Before = table.field(2);

    static final RelRN pushedScan = table.project(Seq.of(table.field(0), table.field(2)));

    static final RexRN out0After = new RexRN.Proj(fOp, Seq.of(pushedScan.field(0), pushedScan.field(1)));
    static final RexRN out1After = pushedScan.field(1);

    @Override
    public RelRN before() {
        return table.project(Seq.of(out0Before, out1Before));
    }

    @Override
    public RelRN after() {
        return pushedScan.project(Seq.of(out0After, out1After));
    }
}
