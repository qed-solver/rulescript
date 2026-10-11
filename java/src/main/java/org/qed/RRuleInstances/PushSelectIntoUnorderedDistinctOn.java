package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — identity unordered DistinctOn only (every input column is a grouping column and there are no aggregate outputs, so every filter is bound by the input columns and the kept/unbound remainder is vacuous); the rule's ConstAgg/FirstAgg-column case is unmodelable because QED's aggregates are uninterpreted.
public record PushSelectIntoUnorderedDistinctOn() implements RRule {
    // col 0 = a, col 1 = b: both are grouping columns of the identity DistinctOn
    static final RelRN base = RelRN.scanMany("Input",
            Seq.of(RexRN.varType("A_Type", false), RexRN.varType("B_Type", false)));
    // The two filters of the Select above the DistinctOn, over all input columns
    // (in the identity case, "any column of the input" == "any grouping column").
    static final RexRN predP = base.pred("P");
    static final RexRN predR = base.pred("R");
    // Identity unordered DistinctOn: group by all input columns, no aggregate outputs.
    static final RelRN distinctOn = new RelRN.Aggregate(
            base, Seq.of(base.field(0), base.field(1)), Seq.empty());
    // The same two filter symbols, now read over the DistinctOn's output row.
    static final RexRN predPUp = distinctOn.pred("P");
    static final RexRN predRUp = distinctOn.pred("R");

    @Override
    public RelRN before() {
        return distinctOn.filter(RexRN.and(predPUp, predRUp));
    }

    @Override
    public RelRN after() {
        // All filter conjuncts are bound by the input columns, so
        // ExtractUnboundConditions is empty and the outer Select is vacuous.
        return new RelRN.Aggregate(
                base.filter(RexRN.and(predP, predR)), Seq.of(base.field(0), base.field(1)), Seq.empty());
    }
}
