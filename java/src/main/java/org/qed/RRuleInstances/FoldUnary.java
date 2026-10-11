// SCOPE: PARTIAL — restricts FoldUnary to the boolean NOT operator over the constant literal TRUE in filter position, the only typed-constant/operator combination QED has semantics for (it cannot evaluate arbitrary uninterpreted unary operators over typed constants).
package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

public record FoldUnary() implements RRule {
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