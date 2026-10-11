package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — CockroachDB's Range scalar is a transparent wrapper around an And; it is modeled as a no-op conjunct (input AND true), which is equivalent to its input in the boolean/filter context where this rule operates, rather than as a fully transparent scalar operator.
public record SimplifyRange() implements RRule {

    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN input = source.pred("input");

    @Override
    public RelRN before() {
        return source.filter(RexRN.and(input, RexRN.trueLiteral()));
    }

    @Override
    public RelRN after() {
        return source.filter(input);
    }
}
