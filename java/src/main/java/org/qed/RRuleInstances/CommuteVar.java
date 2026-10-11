package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RexRN.Pred;

// SCOPE: PARTIAL — only the Eq variant is covered: on a single-column scan, the Variable operand is the column x and the non-Variable operand is the uninterpreted expression e of the same type, proving the universal law e = x <-> x = e under three-valued semantics; the Ne/Is/IsNot/Plus/Mult/Bit*/Vector* variants remain out of reach because QED cannot know commutativity for uninterpreted operators
public record CommuteVar() implements RRule {
    static final RelRN source = RelRN.scan("T", "V");

    // x is the Variable operand (a column reference); e is an uninterpreted
    // non-Variable expression of the same type over the same row.
    static final RexRN x = source.field(0);
    static final RexRN e = x.proj("e", "V");

    @Override
    public RelRN before() {
        // Filter(e = x, T) — non-Variable on the left (rule's before shape)
        return source.filter(new Pred(SqlStdOperatorTable.EQUALS, Seq.of(e, x)));
    }

    @Override
    public RelRN after() {
        // Filter(x = e, T) — Variable on the left (rule's after shape)
        return source.filter(new Pred(SqlStdOperatorTable.EQUALS, Seq.of(x, e)));
    }
}
