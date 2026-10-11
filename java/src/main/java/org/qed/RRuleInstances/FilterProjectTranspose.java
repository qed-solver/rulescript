package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Calcite
public record FilterProjectTranspose() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN projExpr = source.proj("Proj", "Proj_Type");
    static final RelRN projected = source.project(projExpr);
    static final RexRN beforeCond = projected.field(0).pred("F");
    static final RexRN afterCond = projExpr.pred("F");

    @Override
    public RelRN before() {
        return projected.filter(beforeCond);
    }

    @Override
    public RelRN after() {
        return source.filter(afterCond).project(projExpr);
    }
}
