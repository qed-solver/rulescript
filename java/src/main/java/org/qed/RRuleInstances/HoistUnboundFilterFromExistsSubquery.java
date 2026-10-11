// SCOPE: PARTIAL — the outer-bound condition is modeled as a global (0-ary) boolean symbol rather than a genuinely correlated reference into the outer row (RuleScript has no correlated-subquery-inside-EXISTS construct), which is a faithful narrower instance since the identity EXISTS(sigma_{c AND phi}(R)) = c AND EXISTS(sigma_phi(R)) holds for any c not depending on R's rows, regardless of what c itself depends on
package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexSubQuery;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public record HoistUnboundFilterFromExistsSubquery() implements RRule {
    record Exists(RelRN input) implements RexRN {
        @Override
        public RexNode semantics() {
            return RexSubQuery.exists(input.semantics());
        }
    }

    static final RelRN outer = RelRN.scan("Outer", "Outer_Type");
    static final RelRN innerInput = RelRN.scan("Inner", "Inner_Type");

    // "unboundCond": a condition that depends only on the outer row, modeled
    // as a global (0-ary) boolean symbol — the same symbol either way it's
    // used, whether folded into the subquery's filter or pulled out to the
    // outer filter.
    static final RexRN unboundCond = new RexRN.Pred(
            RuleBuilder.create().genericPredicateOp("unbound_cond", false), Seq.empty());
    static final RexRN otherPred = innerInput.pred("other_cond");

    @Override
    public RelRN before() {
        RelRN filteredInner = innerInput.filter(RexRN.and(unboundCond, otherPred));
        return outer.filter(new Exists(filteredInner));
    }

    @Override
    public RelRN after() {
        RelRN filteredInner = innerInput.filter(otherPred);
        return outer.filter(RexRN.and(new Exists(filteredInner), unboundCond));
    }
}
