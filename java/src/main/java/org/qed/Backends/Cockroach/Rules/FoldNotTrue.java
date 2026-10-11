package org.qed.Backends.Cockroach.Rules;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: FULL
public record FoldNotTrue() implements RRule {

    static final RelRN source = RelRN.scan("Source", "Source_Type");

    @Override
    public RelRN before() {
        return source.filter(new RexRN.Not(RexRN.trueLiteral()));
    }

    @Override
    public RelRN after() {
        return source.filter(RexRN.falseLiteral());
    }
}
