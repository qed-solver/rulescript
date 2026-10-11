package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — a 2-element statically constructed array in projection position (a ProjectMany building two arbitrary scalar expressions over a base), with a constant 1-based indirection index 2 (0-based ordinal 1, the in-range case of Cockroach's FoldIndirection), replaced by projecting the referenced element directly.
public record FoldIndirection() implements RRule {
    static final RelRN base = RelRN.scan("Source", "Source_Type");

    // Array elements: arbitrary (uninterpreted) scalar expressions over the base row,
    // e.g. the `i` and `i+1` in Cockroach's `ARRAY[i, i+1][1]`.
    static final RexRN elem0 = base.proj("e0", "Elem0_Type");
    static final RexRN elem1 = base.proj("e1", "Elem1_Type");

    // The statically constructed array: a row of the two elements.
    static final RelRN arr = base.project(Seq.of(elem0, elem1));

    // Indirection on the array: reference the 2nd element (1-based index 2,
    // 0-based ordinal 1) of the constructed array.
    static final RelRN beforeIndirection = arr.project(arr.field(1));

    @Override
    public RelRN before() {
        return beforeIndirection;
    }

    // Folds the indirection into the referenced array element.
    @Override
    public RelRN after() {
        return base.project(elem1);
    }
}
