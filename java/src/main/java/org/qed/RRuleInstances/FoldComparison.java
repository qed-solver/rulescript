package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;

// SCOPE: PARTIAL — restricts FoldComparison to the equality comparison of the constant literals TRUE = FALSE folded to the constant FALSE in a filter position, the only typed-constant comparison QED has semantics for (the DSL can only express boolean literals and QED cannot evaluate operators over arbitrary typed constants).
public record FoldComparison() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");

    @Override
    public RelRN before() {
        return source.filter(new RexRN.Pred(SqlStdOperatorTable.EQUALS,
                Seq.of(RexRN.trueLiteral(), RexRN.falseLiteral())));
    }

    @Override
    public RelRN after() {
        return source.filter(RexRN.falseLiteral());
    }
}