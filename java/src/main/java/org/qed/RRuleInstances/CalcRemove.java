package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — the trivial Calc's identity projection is over a fixed 2-column input; the original rule applies to any input arity
public record CalcRemove() implements RRule {
    static final RelRN source = RelRN.scanMany("Source",
            Seq.of(RexRN.varType("Source_Type_0", true), RexRN.varType("Source_Type_1", true)));

    static final RelRN trivialCalc = source.project(source.fields());

    @Override
    public RelRN before() {
        return trivialCalc;
    }

    @Override
    public RelRN after() {
        return source;
    }
}
