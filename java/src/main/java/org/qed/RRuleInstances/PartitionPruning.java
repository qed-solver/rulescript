package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — assumes the inner join's (uninterpreted) condition is exactly the partition-column equi-join, so the DPP pruning filter is the semi-join under that same condition
public record PartitionPruning() implements RRule {
    static final RelRN build = RelRN.scan("Build", "Build_Type");
    static final RelRN probe = RelRN.scan("Probe", "Probe_Type");
    static final String cond = "join_cond";

    @Override
    public RelRN before() {
        return build.join(JoinRelType.INNER, cond, probe);
    }

    @Override
    public RelRN after() {
        RelRN pruned = build.join(JoinRelType.SEMI, cond, probe);
        return pruned.join(JoinRelType.INNER, cond, probe);
    }
}
