package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;

// SCOPE: PARTIAL — encodes only the In branch with a fixed-shape tuple (a, b, a, b) over two distinct values modeled as source columns; the NotIn branch and arbitrary arities follow the same disjunction-idempotency argument.
public record NormalizeInConst() implements RRule {
    // V: the (uninterpreted) type of the left operand and of the tuple elements.
    static final RelType.VarType V = new RelType.VarType("V", true);
    static final RelRN source = RelRN.scanMany("Source", Seq.of(V, V, V));

    // x = the left operand of In; a, b = the two distinct constant values of the tuple.
    static final RexRN x = source.field(0);
    static final RexRN a = source.field(1);
    static final RexRN b = source.field(2);

    static final RexRN eqA = new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(x, a));
    static final RexRN eqB = new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(x, b));

    @Override
    public RelRN before() {
        // x IN (a, b, a, b): duplicated tuple elements, i.e. the
        // (NeedSortedUniqueList $elems) case of the rule.
        return source.filter(new RexRN.Or(Seq.of(eqA, eqB, eqA, eqB)));
    }

    @Override
    public RelRN after() {
        // x IN (a, b): the tuple with ConstructSortedUniqueList applied.
        return source.filter(new RexRN.Or(Seq.of(eqA, eqB)));
    }
}
