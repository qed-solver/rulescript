package org.qed.RRuleInstances;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: CockroachDB
// SCOPE: FULL
public record EliminateUnaryMinus() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");

    @Override
    public RelRN before() {
        RexRN inner = source.field(0).proj(SqlStdOperatorTable.UNARY_MINUS);
        return source.project(inner.proj(SqlStdOperatorTable.UNARY_MINUS));
    }

    @Override
    public RelRN after() {
        return source;
    }
}
