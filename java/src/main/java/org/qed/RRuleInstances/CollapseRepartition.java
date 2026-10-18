package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — only Case 1's "keep the top node" branch is covered (a Repartition whose child is a Repartition/RepartitionByExpression collapses to the outer repartition over the grandchild); Cases 2-3 require eliminating a Sort child, which QED cannot model (no list/ordering semantics), and Case 1's coalesce branch's numPartitions guard is a physical condition, not a bag-level fact.
public record CollapseRepartition() implements RRule {
    // The grandchild of the two stacked repartitions: an arbitrary relation.
    static final RelRN source = RelRN.scanMany("Source", Seq.of(
            RexRN.varType("C0_Type", true),
            RexRN.varType("C1_Type", true),
            RexRN.varType("C2_Type", true)));

    // A Repartition / RepartitionByExpression redistributes the input's rows
    // across partitions without changing the rows or their multiplicities, so
    // its bag semantics is exactly an identity projection of its input — same
    // schema, same multiplicities (the shuffle is invisible at the bag level).
    static RelRN repartition(RelRN input) {
        return input.project(input.fields());
    }

    @Override
    public RelRN before() {
        // Repartition(R2, Repartition(R1, Source))
        return repartition(repartition(source));
    }

    @Override
    public RelRN after() {
        // r.copy(child = child.child): keep the top node, splice in the grandchild
        return repartition(source);
    }
}
