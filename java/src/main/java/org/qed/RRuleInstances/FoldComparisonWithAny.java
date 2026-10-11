package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — encodes only the constant instance TRUE = ANY (FALSE, TRUE) ⟹ TRUE, as OR(EQ(TRUE,FALSE), EQ(TRUE,TRUE)) ⟹ TRUE, since the DSL has no tuple/ANY node and no NULL literal.
public record FoldComparisonWithAny() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN trueLit = RexRN.trueLiteral();
    static final RexRN falseLit = RexRN.falseLiteral();
    static final RexRN eqTrueFalse = new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(trueLit, falseLit));
    static final RexRN eqTrueTrue = new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(trueLit, trueLit));

    @Override
    public RelRN before() {
        return source.filter(new RexRN.Or(Seq.of(eqTrueFalse, eqTrueTrue)));
    }

    @Override
    public RelRN after() {
        return source.filter(trueLit);
    }
}
