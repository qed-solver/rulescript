package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import kala.collection.Seq;

// ORIGIN: CockroachDB
// SCOPE: PARTIAL — input Values is modeled as an uninterpreted relation and the casted values rows as an equivalent inner projection layer (core language has no expression-row Values), with the other projection a constant.
public record PushAssignmentCastsIntoValues() implements RRule {
    // The Values input (single column), uninterpreted.
    static final RelRN values = RelRN.scan("Values", "Values_Type");
    // The assignment cast pushed from the project into the values rows.
    static final RexRN cast = values.proj("cast", "Cast_Type");
    // An independent projection (like 'foo' in the rule's example).
    static final RexRN other = RexRN.trueLiteral();

    @Override
    public RelRN before() {
        return values.project(Seq.of(cast, other));
    }

    @Override
    public RelRN after() {
        RelRN pushed = values.project(cast);
        return pushed.project(Seq.of(pushed.field(0), other));
    }
}
