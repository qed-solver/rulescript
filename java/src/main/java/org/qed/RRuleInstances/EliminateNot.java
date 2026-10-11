package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: FULL
public record EliminateNot() implements RRule {

    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN input = source.pred("input");

    @Override
    public RelRN before() {
        return source.filter(new RexRN.Not(new RexRN.Not(input)));
    }

    @Override
    public RelRN after() {
        return source.filter(input);
    }
}
