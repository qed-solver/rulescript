package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Calcite
// SCOPE: FULL
public record FilterToCalc() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN cond = source.pred("cond");

    @Override
    public RelRN before() {
        return source.filter(cond);
    }

    @Override
    public RelRN after() {
        return source.filter(cond).project(source.fields());
    }
}
