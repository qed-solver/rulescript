package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;

// ORIGIN: CockroachDB
// SCOPE: PARTIAL — 2-input, single-column, same row type, identity column mapping, with the left input a base scan declared unique (has a strict key).
public record SimplifyExcept() implements RRule {
    // Left has a strict key (unique); right does not.
    static final RelRN left = RelRN.scan("L", RexRN.varType("T", false), true);
    static final RelRN right = RelRN.scan("R", RexRN.varType("T", false), false);

    // Anti-join condition: L.col IS NOT DISTINCT FROM R.col
    static final RexRN cond = new RexRN.Pred(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM,
            Seq.of(left.joinField(0, right), left.joinField(1, right)));

    @Override
    public RelRN before() {
        // EXCEPT (set semantics, all = false)
        return left.minus(false, right);
    }

    @Override
    public RelRN after() {
        // Because L has a strict key, each left value has multiplicity 1, so
        // EXCEPT ALL reduces to L ANTI-JOIN R; no de-duplication step needed.
        return left.join(JoinRelType.ANTI, cond, right);
    }
}
