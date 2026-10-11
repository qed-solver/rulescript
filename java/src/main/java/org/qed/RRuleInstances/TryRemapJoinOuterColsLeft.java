package org.qed.RRuleInstances;
// SCOPE: PARTIAL — the InnerJoinApply case only: both inputs are single-column scans of one shared uninterpreted type, ON is the genuine equality l.c0 = r.c0, and the remapped predicate is an uninterpreted 1-ary h over the outer column, swapped to the equal right column
import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public record TryRemapJoinOuterColsLeft() implements RRule {
    static final RelRN L = RelRN.scan("L", RexRN.varType("T", true), false);
    static final RelRN R = RelRN.scan("R", RexRN.varType("T", true), false);
    static final SqlOperator h = RuleBuilder.create().genericPredicateOp("h", true);
    static final RelRN join = L.correlate(JoinRelType.INNER, SqlStdOperatorTable.EQUALS, R);

    @Override
    public RelRN before() {
        return join.filter(new RexRN.Pred(h, Seq.of(join.field(0))));
    }

    @Override
    public RelRN after() {
        return join.filter(new RexRN.Pred(h, Seq.of(join.field(1))));
    }
}
