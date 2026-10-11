package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — the aggregation argument is itself the (single-column)
// unique grouping key, so every group has at most one row and AggDistinct
// trivially coincides with the plain aggregate; this is a genuine, if
// narrow, instance of CanRemoveAggDistinctForKeys (uniqueness makes
// duplicate-argument-values impossible, not merely absent). The source
// rule's AggFilter wrapper is not modeled here — RelRN/AggCall has no
// FILTER-clause construct — but since $filter is identical and present
// unchanged on both sides of the source rule, and filtering rows out of an
// already-unique-keyed relation cannot introduce duplicates into what
// remains, the covered AggDistinct->plain identity extends unaffected to
// the AggFilter case; only the AggFilter wrapper itself goes unverified.
public record EliminateAggFilteredDistinctForKeys() implements RRule {
    static final RelRN source = RelRN.scan("S", RexRN.varType("S_Type", false), true);
    static final RexRN key = source.field(0);
    static final RelRN.AggCall distinctAgg = new RelRN.AggCall("f", true, RexRN.varType("F_Type", true), Seq.of(key));
    static final RelRN.AggCall plainAgg   = new RelRN.AggCall("f", false, RexRN.varType("F_Type", true), Seq.of(key));

    @Override
    public RelRN before() {
        return new RelRN.Aggregate(source, Seq.of(key), Seq.of(distinctAgg));
    }

    @Override
    public RelRN after() {
        return new RelRN.Aggregate(source, Seq.of(key), Seq.of(plainAgg));
    }
}
