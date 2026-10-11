package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — assumes the tuple's non-null constant element is the first element (second element arbitrary, nullable)
public record FoldNonNullTupleIsTupleNull() implements RRule {
    // The tuple $input is represented by its elements as columns of the source
    // relation: element 0 is the non-null constant (encodes the source rule's
    // side condition (HasNonNullElement $input), as a non-nullable column type,
    // exactly as FoldNonNullIsNull encodes (IsNeverNull)), element 1 is an
    // arbitrary element of the tuple (any value, null or not).
    static final RelRN source = RelRN.scanMany("Source", Seq.of(
            RexRN.varType("Elem0_Type", false),
            RexRN.varType("Elem1_Type", true)));

    // IsTupleNull(x) holds iff every element of the tuple x is null.
    static RexRN isTupleNull(RelRN t) {
        return RexRN.and(
                t.field(0).pred(SqlStdOperatorTable.IS_NULL),
                t.field(1).pred(SqlStdOperatorTable.IS_NULL));
    }

    @Override
    public RelRN before() {
        // (IsTupleNull $input) as the whole filter condition.
        return source.filter(isTupleNull(source));
    }

    @Override
    public RelRN after() {
        // (False)
        return source.filter(RexRN.falseLiteral());
    }
}
