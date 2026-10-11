package org.qed.RRuleInstances;

import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

public record ProjectSetOpTranspose() implements RRule {
    static final RelRN left = RelRN.scan("L", "T");
    static final RelRN right = RelRN.scan("R", "T");

    static final SqlOperator fOp =
            RuleBuilder.create().genericProjectionOp("F", new RelType.VarType("T", true));

    @Override
    public RelRN before() {
        final RelRN unionAll = left.union(true, right);
        return unionAll.project(unionAll.field(0).proj(fOp));
    }

    @Override
    public RelRN after() {
        return left.project(left.field(0).proj(fOp))
                .union(true, right.project(right.field(0).proj(fOp)));
    }
}