// ORIGIN: Spark
// SCOPE: PARTIAL — EXISTS branch only, first application (pre-existing conditions empty): one outer-bound conjunct plus one local conjunct in a single subquery filter, rewritten to record the conjunct in the expression's conditions slot (modeled as c AND EXISTS(inner)); the correlated conjunct is a 0-ary symbol since the DSL has no outer-row reference inside a subquery; ScalarSubquery/LateralSubquery branches, output reshaping, and name dedup are out of scope
package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexSubQuery;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public record PullupCorrelatedPredicates() implements RRule {
    // Spark's Exists subquery expression: (innerPlan, ..., conditions).
    // The value for the enclosing row is cond AND (any row in inner).
    record Exists(RelRN input, RexRN cond) implements RexRN {
        @Override
        public RexNode semantics() {
            return RuleBuilder.create().and(Seq.of(
                    cond.semantics(), RexSubQuery.exists(input.semantics())));
        }
    }

    static final RelRN outer = RelRN.scan("Outer", "Outer_Type");
    static final RelRN inner = RelRN.scan("Inner", "Inner_Type");

    // Outer-bound (correlated) conjunct; 0-ary since the DSL has no
    // outer-row reference inside a subquery.
    static final RexRN c = new RexRN.Pred(
            RuleBuilder.create().genericPredicateOp("pulled_up_cond", false), Seq.empty());
    // Local conjunct over inner rows; stays in the subquery.
    static final RexRN local = inner.pred("local_cond");
    // Unrelated conjunct of the enclosing filter.
    static final RexRN outerCond = outer.pred("outer_cond");

    @Override
    public RelRN before() {
        // Correlated filter in the subquery; conditions slot empty (True).
        return outer.filter(RexRN.and(outerCond,
                new Exists(inner.filter(RexRN.and(c, local)), RexRN.trueLiteral())));
    }

    @Override
    public RelRN after() {
        // Correlated conjunct recorded in the conditions slot; inner uncorrelated.
        return outer.filter(RexRN.and(outerCond,
                new Exists(inner.filter(local), c)));
    }
}
