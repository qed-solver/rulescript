// SCOPE: PARTIAL — fixed-shape pattern: 2-column zero-row input with 2 passthrough columns and 1 uninterpreted projection, versus the original rule's arbitrary passthrough/projection lists.
package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;

public record EliminateZeroCardProject() implements RRule {
    static final RelType.VarType t0 = new RelType.VarType("Input_Type0", true);
    static final RelType.VarType t1 = new RelType.VarType("Input_Type1", true);
    static final RelType.VarType p  = new RelType.VarType("Proj_Type", true);
    // $input with (HasZeroRows $input): in QED's bag model, any zero-row input
    // is semantically the empty relation of its type; the IsLeakproof guard has
    // no counterpart in side-effect-free bag semantics.
    static final RelRN input = RelRN.scanMany("Input", Seq.of(t0, t1)).empty();

    @Override
    public RelRN before() {
        // Project(input, $passthrough=[c0,c1], $projections=[f(c0)])
        return input.project(Seq.of(
                input.field(0),
                input.field(1),
                input.field(0).proj("f", "Proj_Type")
        ));
    }

    @Override
    public RelRN after() {
        // ConstructEmptyValues(UnionCols $passthrough (ProjectionCols $projections))
        return RelRN.scanMany("Out", Seq.of(t0, t1, p)).empty();
    }
}