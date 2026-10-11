// SCOPE: PARTIAL — the zero-rows precondition is encoded as a literal .empty() relation (a statically known-empty shape), matching the source rule's HasZeroRows guard
package org.qed.RRuleInstances;

import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexSubQuery;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

public record EliminateExistsZeroRows() implements RRule {
    record Exists(RelRN input) implements RexRN {
        @Override
        public RexNode semantics() {
            return RexSubQuery.exists(input.semantics());
        }
    }

    static final RelRN outer = RelRN.scan("Outer", "Outer_Type");
    static final RelRN inputShape = RelRN.scan("Input", "Input_Type");
    static final RelRN zeroRows = inputShape.empty();

    @Override
    public RelRN before() {
        return outer.filter(new Exists(zeroRows));
    }

    @Override
    public RelRN after() {
        return outer.filter(RexRN.falseLiteral());
    }
}
