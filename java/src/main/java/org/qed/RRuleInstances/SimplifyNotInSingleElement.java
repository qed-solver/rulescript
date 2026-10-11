package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;

// SCOPE: FULL
public record SimplifyNotInSingleElement() implements RRule {
    // V: the (uninterpreted) type shared by the left operand and the single tuple element.
    static final RelType.VarType V = new RelType.VarType("V", true);
    static final RelRN source = RelRN.scanMany("Source", Seq.of(V, V));

    // left = $left of the NotIn; right = the single element $right of the tuple.
    static final RexRN left = source.field(0);
    static final RexRN right = source.field(1);

    static final RexRN eq = new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(left, right));
    static final RexRN neq = new RexRN.Pred(SqlStdOperatorTable.NOT_EQUALS, Seq.of(left, right));

    @Override
    public RelRN before() {
        // left NOT IN (right): the single-element tuple In is expanded
        // definitionally into a one-argument disjunction, then negated.
        return source.filter(new RexRN.Not(new RexRN.Or(Seq.of(eq))));
    }

    @Override
    public RelRN after() {
        // Ne left right
        return source.filter(neq);
    }
}