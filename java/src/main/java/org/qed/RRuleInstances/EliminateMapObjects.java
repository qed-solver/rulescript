// ORIGIN: Spark
// SCOPE: PARTIAL — the identity projection is over a fixed single-column input; the original rule applies to any input arity (and, like the source, assumes non-nullable primitive types)
package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;

public record EliminateMapObjects() implements RRule {
    // A single-column, non-nullable input standing in for $inputData
    // (uninterpreted, any arity in the original rule). Non-nullable matches
    // the source premise LambdaVariable(_, _, false, _): the lambda maps a
    // non-nullable primitive to itself, so an identity map.
    static final RelRN input = RelRN.scanMany("Input", Seq.of(
            new RelType.VarType("Input_Type0", false)));

    @Override
    public RelRN before() {
        // MapObjects with an identity lambda (no synthesized/serialized
        // columns): it projects each input column straight through, adding
        // and removing nothing — i.e. an identity projection.
        return input.project(Seq.of(input.field(0)));
    }

    @Override
    public RelRN after() {
        // ... which is just the input data.
        return input;
    }
}
