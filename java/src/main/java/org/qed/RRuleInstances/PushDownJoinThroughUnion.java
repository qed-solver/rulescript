package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — LeftOuter join, two-branch UNION ALL, single-column scans, one shared join-condition symbol C (source rule also covers INNER and arbitrary union arity; broadcast guards are cost-only)

public record PushDownJoinThroughUnion() implements RRule {
    // X and Y both have one column of the same (uninterpreted) type T, so
    // their union-all is well-formed; O is the right-side scan of type S.
    static final RelRN X = RelRN.scan("X", "T");
    static final RelRN Y = RelRN.scan("Y", "T");
    static final RelRN O = RelRN.scan("O", "S");

    // Shared uninterpreted predicate symbol C over (left column, O column);
    // reusing the same operator in before() and after() tells QED the
    // occurrences are the same symbol, mirroring Spark reusing join.condition
    // rewritten identically for every branch.
    static final SqlOperator C = RuleBuilder.create().genericPredicateOp("C", true);

    static final RelRN U = X.union(true, Y);

    static final RexRN condU = U.joinPred(C, O);
    static final RexRN condX = X.joinPred(C, O);
    static final RexRN condY = Y.joinPred(C, O);

    @Override
    public RelRN before() {
        return U.join(JoinRelType.LEFT, condU, O);
    }

    @Override
    public RelRN after() {
        return X.join(JoinRelType.LEFT, condX, O)
                .union(true,
                       Y.join(JoinRelType.LEFT, condY, O));
    }
}
