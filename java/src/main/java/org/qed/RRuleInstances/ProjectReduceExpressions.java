package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — the project expression is FALSE AND an uninterpreted predicate, reduced to the constant FALSE
public record ProjectReduceExpressions() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");

    @Override
    public RelRN before() {
        return source.project(RexRN.and(RexRN.falseLiteral(), source.pred("P")));
    }

    @Override
    public RelRN after() {
        return source.project(RexRN.falseLiteral());
    }
}
