package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;

// ORIGIN: Spark
// SCOPE: PARTIAL — single equi-key INNER join; bloom filter modeled as exact semi-join (no false positives)
public record InjectRuntimeFilter() implements RRule {
    static final RelRN L = RelRN.scan("L", "KeyType");
    static final RelRN R = RelRN.scan("R", "KeyType");
    static final RexRN eq = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
        Seq.of(L.joinField(0, R), L.joinField(1, R)));
    static final RelRN semi = L.join(JoinRelType.SEMI, eq, R);
    static final RexRN eq2 = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
        Seq.of(semi.joinField(0, R), semi.joinField(1, R)));

    @Override
    public RelRN before() {
        return L.join(JoinRelType.INNER, eq, R);
    }

    @Override
    public RelRN after() {
        return semi.join(JoinRelType.INNER, eq2, R);
    }
}
