// SCOPE: PARTIAL — the identity project is over a fixed two-column input; the original rule applies to any input arity
package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;

public record EliminateProject() implements RRule {
    // A two-column input standing in for $input (uninterpreted, any arity in
    // the original rule).
    static final RelRN input = RelRN.scanMany("Input", Seq.of(
            new RelType.VarType("Input_Type0", true),
            new RelType.VarType("Input_Type1", true)));

    @Override
    public RelRN before() {
        // A Project with no synthesized columns ($projections:[]) whose
        // passthrough is exactly the input's output columns
        // (ColsAreEqual $passthrough (OutputCols $input)): it projects each
        // input column straight through, adding and removing nothing.
        return input.project(Seq.of(input.field(0), input.field(1)));
    }

    @Override
    public RelRN after() {
        return input;
    }
}