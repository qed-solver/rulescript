// SCOPE: PARTIAL — the zero-rows precondition is encoded as a literal .empty() relation (a statically known-empty shape), matching the source rule's HasZeroRows guard
package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;

public record EliminateZeroCardSelect() implements RRule {
    static final RelRN zeroInput = RelRN.scan("Input", "Input_Type").empty();

    @Override
    public RelRN before() {
        return zeroInput.filter("f1").filter("f2");
    }

    @Override
    public RelRN after() {
        return zeroInput;
    }
}