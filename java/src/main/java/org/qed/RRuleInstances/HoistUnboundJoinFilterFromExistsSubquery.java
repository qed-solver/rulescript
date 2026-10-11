// SCOPE: PARTIAL — INNER join only, the outer-bound condition is modeled as a global (0-ary) boolean symbol rather than a genuinely correlated reference into the outer row (RuleScript has no correlated-subquery-inside-EXISTS construct), which is a faithful narrower instance of the same identity as HoistUnboundFilterFromExistsSubquery, applied to a join's filter list instead of a plain Select's
package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexSubQuery;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public record HoistUnboundJoinFilterFromExistsSubquery() implements RRule {
    record Exists(RelRN input) implements RexRN {
        @Override
        public RexNode semantics() {
            return RexSubQuery.exists(input.semantics());
        }
    }

    static final RelRN outer = RelRN.scan("Outer", "Outer_Type");
    static final RelRN left = RelRN.scan("Left", "Left_Type");
    static final RelRN right = RelRN.scan("Right", "Right_Type");

    // "unboundCond": a condition that depends only on the outer row, modeled
    // as a global (0-ary) boolean symbol — same symbol whether folded into
    // the join's own filter (before) or pulled out to the outer filter (after).
    static final RexRN unboundCond = new RexRN.Pred(
            RuleBuilder.create().genericPredicateOp("unbound_cond", false), Seq.empty());
    static final RexRN otherJoinPred = left.joinPred("other_join_cond", right);

    @Override
    public RelRN before() {
        RelRN join = left.join(JoinRelType.INNER, RexRN.and(unboundCond, otherJoinPred), right);
        return outer.filter(new Exists(join));
    }

    @Override
    public RelRN after() {
        RelRN join = left.join(JoinRelType.INNER, otherJoinPred, right);
        return outer.filter(RexRN.and(new Exists(join), unboundCond));
    }
}
