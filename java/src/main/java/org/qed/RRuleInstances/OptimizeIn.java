package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — assumes the IN list is the minimal dedup-to-one case (exactly two occurrences of the same expression e), so In(v, [e, e]) is encoded as (v = e) OR (v = e) reduced to v = e, omitting the empty-list, longer-list, and InSet-threshold branches.
public record OptimizeIn() implements RRule {
    // V: the (uninterpreted) type shared by the left operand v and the list element e.
    static final RelType.VarType V = new RelType.VarType("V", true);
    static final RelRN source = RelRN.scanMany("Source", Seq.of(V, V));

    // v = the In left side; e = the expression that appears twice in the list.
    static final RexRN v = source.field(0);
    static final RexRN e = source.field(1);

    // v = e: the single shared disjunct — both list elements are the same expression e,
    // which is exactly the precondition under which the rule's list dedup shrinks to length 1.
    static final RexRN eq = new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(v, e));

    @Override
    public RelRN before() {
        // In(v, [e, e]): a two-element IN list whose elements are identical,
        // which is definitionally the disjunction (v = e) OR (v = e).
        return source.filter(new RexRN.Or(Seq.of(eq, eq)));
    }

    @Override
    public RelRN after() {
        // The rule dedups the list to [e] (length 1) and rewrites In(v, [e])
        // to EqualTo(v, e).
        return source.filter(eq);
    }
}
