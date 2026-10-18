package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — no grouping expressions and the distinct aggregate's argument is a literal constant (the only foldable case modeled)
public record RewriteDistinctAggregates() implements RRule {
    static final RelRN child = RelRN.scanMany("Child", Seq.of(
            new RelType.VarType("C1_type", true)));

    // Foldable constant argument of the distinct aggregate (literal 0 of BIGINT).
    static final RexRN constExpr = new RexRN.Const(RelType.fromString("BIGINT", true));
    // Grouping-id literal column added by the rewrite (literal 0 of INTEGER).
    static final RexRN gidExpr = new RexRN.Const(RelType.fromString("INTEGER", true));
    static final RelType cntType = new RelType.VarType("CNT_type", true);

    @Override
    public RelRN before() {
        // Aggregate(no groups, COUNT(DISTINCT const))
        return new RelRN.Aggregate(child, Seq.<RexRN>empty(), Seq.of(new RelRN.AggCall(
                "count", true, cntType, Seq.of(constExpr))));
    }

    @Override
    public RelRN after() {
        // Project (const, gid) onto the child, dedup by both columns (one row
        // per distinct pair — i.e. at most one row), then count the constant
        // column non-distinct over the result.
        var dedup = new RelRN.Aggregate(
                child.project(Seq.of(constExpr, gidExpr)),
                Seq.of(constExpr, gidExpr),
                Seq.<RelRN.AggCall>empty());
        return new RelRN.Aggregate(dedup, Seq.<RexRN>empty(), Seq.of(new RelRN.AggCall(
                "count", false, cntType, Seq.of(constExpr))));
    }
}