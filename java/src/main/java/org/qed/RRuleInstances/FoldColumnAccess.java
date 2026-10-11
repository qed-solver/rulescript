package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — a 2-element statically constructed tuple in projection position: a ProjectMany building two arbitrary scalar expressions over a base, with a single-column field access on that tuple, replaced by projecting the accessed element directly.
public record FoldColumnAccess() implements RRule {
    static final RelRN base = RelRN.scan("Source", "Source_Type");

    // Tuple elements: arbitrary (uninterpreted) scalar expressions over the base row,
    // e.g. the `i` and `i+1` in Cockroach's `(((i, i+1) as foo, bar)).bar`.
    static final RexRN elem0 = base.proj("e0", "Elem0_Type");
    static final RexRN elem1 = base.proj("e1", "Elem1_Type");

    // The statically constructed tuple: a row of the two elements.
    static final RelRN tuple = base.project(Seq.of(elem0, elem1));

    // Column access on the tuple: pick the 2nd element (index 1) of the constructed row.
    static final RelRN beforeAccess = tuple.project(tuple.field(1));

    @Override
    public RelRN before() {
        return beforeAccess;
    }

    // Folds the column access into the referenced tuple element.
    @Override
    public RelRN after() {
        return base.project(elem1);
    }
}