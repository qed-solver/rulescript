package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — only the predicate-pushdown part (no column pruning), exactly two CTE references, both with pushable predicates
public record PushdownPredicatesAndPruneColumnsForCTEDef() implements RRule {
    // The CTE definition as one uninterpreted table; each reference is just another use of the same symbol.
    static final RelRN cte = RelRN.scan("Cte", "Cte_Type");
    static final RexRN pred1 = cte.pred("ref1Pred");
    static final RexRN pred2 = cte.pred("ref2Pred");

    @Override
    public RelRN before() {
        // Two references to the CTE, each carrying its own pushed-down filter.
        return cte.filter(pred1).union(true, cte.filter(pred2));
    }

    @Override
    public RelRN after() {
        // The disjunction of all reference predicates is pushed into the definition,
        // and each reference still applies its own predicate on top.
        RelRN pushedDef = cte.filter(new RexRN.Or(Seq.of(pred1, pred2)));
        return pushedDef.filter(pred1).union(true, pushedDef.filter(pred2));
    }
}