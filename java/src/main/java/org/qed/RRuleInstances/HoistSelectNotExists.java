// SCOPE: PARTIAL — the hoisted NOT EXISTS subquery is uncorrelated (references only its own input S), so the empty AntiJoinApply condition is an ANTI join under a constant-true predicate.
package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.SqlOperator;

public record HoistSelectNotExists() implements RRule {
    record ExistsSub(RelRN body) implements RexRN {
        @Override
        public RexNode semantics() {
            return RexSubQuery.exists(body.semantics());
        }
    }

    static final RelRN input = RelRN.scan("L", "L_Type");
    static final RelRN sub = RelRN.scan("S", "S_Type");
    static final SqlOperator subOp = RuleBuilder.create().genericPredicateOp("sub_pred", true);
    static final RexRN subFilter = sub.pred(subOp);
    static final RexRN notExists = new RexRN.Not(new ExistsSub(sub.filter(subFilter)));
    static final RexRN rest = input.pred("rest");

    @Override
    public RelRN before() {
        // Select(L, ... NOT EXISTS(Filter(S, sub_pred)) ... rest)
        return input.filter(RexRN.and(notExists, rest));
    }

    @Override
    public RelRN after() {
        // Select(AntiJoinApply(L, Filter(S, sub_pred), []), ... rest)
        return input.join(JoinRelType.ANTI, RexRN.trueLiteral(), sub.filter(subFilter)).filter(rest);
    }
}
