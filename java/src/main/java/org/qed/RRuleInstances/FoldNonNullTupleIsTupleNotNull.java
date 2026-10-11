package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — the tuple is fixed to arity 2 and both of its elements are encoded as non-nullable (standing in for the constant, non-null elements required by the side condition HasAllNonNullElements).
public record FoldNonNullTupleIsTupleNotNull() implements RRule {
    // The tuple $input is represented by its elements as columns of the source
    // relation: both element 0 and element 1 are non-nullable (encodes the
    // source rule's side condition (HasAllNonNullElements $input) — every
    // element is a constant, non-null value — exactly as FoldNonNullIsNull
    // encodes (IsNeverNull)).
    static final RelRN source = RelRN.scanMany("Source", Seq.of(
            RexRN.varType("Elem0_Type", false),
            RexRN.varType("Elem1_Type", false)));

    // IsTupleNotNull(x) holds iff every element of the tuple x is non-null.
    static RexRN isTupleNotNull(RelRN t) {
        return RexRN.and(
                t.field(0).pred(SqlStdOperatorTable.IS_NOT_NULL),
                t.field(1).pred(SqlStdOperatorTable.IS_NOT_NULL));
    }

    @Override
    public RelRN before() {
        // (IsTupleNotNull $input) as the whole filter condition.
        return source.filter(isTupleNotNull(source));
    }

    @Override
    public RelRN after() {
        // (True)
        return source.filter(RexRN.trueLiteral());
    }
}