package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;

// SCOPE: PARTIAL — self semi-join in which both inputs are the same scan and the join filter is constant true, so every left row matches whenever the relation is non-empty
public record EliminateSemiJoin() implements RRule {
    // The relation joined with itself. Sharing the same scan on both sides is
    // what encodes the "join filters match all left rows" precondition of
    // EliminateSemiJoin for the constant-true filter: each left row matches
    // its own copy in the identical right input whenever the right input is
    // non-empty, and when it is empty both sides of the rewrite are empty, so
    // the semi-join still equals $left in every case.
    static final RelRN tbl = RelRN.scan("T", "T_Type");

    @Override
    public RelRN before() {
        return tbl.join(JoinRelType.SEMI, RexRN.trueLiteral(), tbl);
    }

    @Override
    public RelRN after() {
        return tbl;
    }
}
