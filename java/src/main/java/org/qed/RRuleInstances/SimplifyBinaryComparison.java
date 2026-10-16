package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import kala.collection.Seq;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;

// ORIGIN: Spark
// SCOPE: PARTIAL — only the `a = a` (EqualTo) case, simplified to the true literal under the condition that a is non-nullable
public record SimplifyBinaryComparison() implements RRule {
    static final RelRN S = RelRN.scan("S", RexRN.varType("T", false), false);
    static final RexRN a = S.field(0);

    @Override
    public RelRN before() {
        return S.filter(new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(a, a)));
    }

    @Override
    public RelRN after() {
        return S.filter(RexRN.trueLiteral());
    }
}
