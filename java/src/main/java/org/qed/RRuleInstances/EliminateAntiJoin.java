package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;

// SCOPE: PARTIAL — covers the non-correlated AntiJoin operator; the correlated AntiJoinApply variant of the source rule is not modeled.
public record EliminateAntiJoin() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    // $right & (HasZeroRows $right): the right input produces no rows.
    static final RelRN right = RelRN.scan("R", "R_Type").empty();

    @Override
    public RelRN before() {
        // L ANTI-JOIN R ON cond, with R known to be empty (zero rows).
        return left.join(JoinRelType.ANTI, "cond", right);
    }

    @Override
    public RelRN after() {
        // An anti-join with an empty right side returns every left row.
        return left;
    }
}
