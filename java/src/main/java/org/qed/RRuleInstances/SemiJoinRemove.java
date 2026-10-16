package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — the matched semi-join must be the left input of an inner join that shares both the same join condition and the same right relation, i.e. (X SEMI⋈_C Y) INNER⋈_C Y ⇒ X INNER⋈_C Y; this local redundancy condition stands in for the source rule's global "all SemiJoins in the plan are advisory" side condition, which has no form as a local pattern constraint.
public record SemiJoinRemove() implements RRule {
    static final RelRN X = RelRN.scan("X", "X_Type");
    static final RelRN Y = RelRN.scan("Y", "Y_Type");

    // The uninterpreted condition over the joined row (X, Y), shared by
    // both joins.
    static final RexRN cond = X.joinPred("cond", Y);

    // before: (X SEMI⋈_C Y) INNER⋈_C Y — a semi-join root whose left input
    // is an inner join over the same condition and the same right relation;
    // the rule matches any semi-join here and transforms to its left input.
    @Override
    public RelRN before() {
        return X.join(JoinRelType.SEMI, cond, Y)
                .join(JoinRelType.INNER, cond, Y);
    }

    // after: X INNER⋈_C Y — the semi-join is removed. Every row that
    // survives the inner join already satisfies C, so the semi-join above
    // it accepts all left rows and filters nothing (and, per semi-join
    // semantics, contributes no extra output rows either).
    @Override
    public RelRN after() {
        return X.join(JoinRelType.INNER, cond, Y);
    }
}
