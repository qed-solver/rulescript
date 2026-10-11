package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Calcite
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
