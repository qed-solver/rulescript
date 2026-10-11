// SCOPE: PARTIAL — restricts FoldBinary to the boolean AND operator over the constant literals TRUE and FALSE in a filter position, the only typed-constant/operator combination QED has semantics for (it cannot evaluate arbitrary uninterpreted operators over typed constants).
package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

public record FoldBinary() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");

    @Override
    public RelRN before() {
        return source.filter(RexRN.and(RexRN.trueLiteral(), RexRN.falseLiteral()));
    }

    @Override
    public RelRN after() {
        return source.filter(RexRN.falseLiteral());
    }
}