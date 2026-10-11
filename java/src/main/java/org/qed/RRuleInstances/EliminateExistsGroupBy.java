package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RRule;

// SCOPE: PARTIAL — uncorrelated EXISTS only (modeled as a left-semi join on a constant-true condition), instantiated with a single uninterpreted group key and no aggregate calls
public record EliminateExistsGroupBy() implements RRule {
    // The outer relation whose rows each test the EXISTS subquery. An
    // uncorrelated EXISTS is a no-op except for observing the subquery's
    // non-emptiness, so it is modeled as a left-semi join with a
    // constant-true condition: every outer row is kept iff the subquery is
    // non-empty.
    static final RelRN outer = RelRN.scan("Outer", "Outer_Type");

    // The subquery's base input.
    static final RelRN input = RelRN.scan("Input", "Input_Type");

    // A non-scalar group by of that input: one uninterpreted group key, no
    // aggregate calls. Like any non-scalar group by (or DistinctOn) it may
    // change row cardinality, but it returns a non-empty set iff its input
    // is non-empty, so it is a no-op for EXISTS.
    static final RelRN groupBy = new RelRN.Aggregate(input,
            Seq.of(input.groupBy("g")), Seq.empty());

    @Override
    public RelRN before() {
        return outer.join(JoinRelType.SEMI, RexRN.trueLiteral(), groupBy);
    }

    @Override
    public RelRN after() {
        return outer.join(JoinRelType.SEMI, RexRN.trueLiteral(), input);
    }
}
