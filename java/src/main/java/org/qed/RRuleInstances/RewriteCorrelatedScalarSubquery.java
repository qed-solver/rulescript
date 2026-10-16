package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;

// ORIGIN: Spark
// SCOPE: PARTIAL — single-column relations on one equality correlation key with a single aggregate grouped by the outer key (no HAVING, projections, or multiple subqueries), with the aggregate kept above the join on both sides
public record RewriteCorrelatedScalarSubquery() implements RRule {
    static final RelRN left = RelRN.scan("L", "K_type");
    static final RelRN right = RelRN.scan("R", "K_type");

    @Override
    public RelRN before() {
        RelRN corr = left.correlate(JoinRelType.LEFT, SqlStdOperatorTable.EQUALS, right);
        return new RelRN.Aggregate(corr, Seq.of(corr.field(0)),
                Seq.of(new RelRN.AggCall("agg", false, new RelType.VarType("agg_type", true), Seq.of(corr.field(1)))));
    }

    @Override
    public RelRN after() {
        RelRN join = left.join(JoinRelType.LEFT, left.joinPred(SqlStdOperatorTable.EQUALS, right), right);
        return new RelRN.Aggregate(join, Seq.of(join.field(0)),
                Seq.of(new RelRN.AggCall("agg", false, new RelType.VarType("agg_type", true), Seq.of(join.field(1)))));
    }
}
