package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — the tuple is fixed to arity 2 (both of its elements are the constant NULL, standing in for the side condition HasAllNullElements).
public record FoldNullTupleIsTupleNull() implements RRule {
    // A constant NULL value (type-agnostic: IS NULL on NULL holds regardless of the
    // element type). Stands in for the tuple's elements: the source rule's side
    // condition (HasAllNullElements $input) says every element of the tuple is the
    // constant NULL, exactly as FoldIsNull encodes a bare (Null) literal. Reusing the
    // same symbol for both elements expresses that the two elements are the identical
    // constant NULL.
    static final RexRN nullLit = new RexRN() {
        @Override
        public RexNode semantics() {
            var b = RuleBuilder.create();
            return b.getRexBuilder().makeNullLiteral(b.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        }
    };

    // Relational home for the scalar fold (the condition references no column, as in
    // the pure-scalar FoldIsNull; the scan is just the surrounding bag context).
    static final RelRN source = RelRN.scan("Source", "Source_Type");

    // IsTupleNull(x) holds iff every element of the tuple x is null; with both
    // elements the constant NULL this is the conjunction below.
    static RexRN isTupleNull() {
        return RexRN.and(
                new RexRN.Pred(SqlStdOperatorTable.IS_NULL, Seq.of(nullLit)),
                new RexRN.Pred(SqlStdOperatorTable.IS_NULL, Seq.of(nullLit)));
    }

    @Override
    public RelRN before() {
        // (IsTupleNull $input & HasAllNullElements $input)
        return source.filter(isTupleNull());
    }

    @Override
    public RelRN after() {
        // (True)
        return source.filter(RexRN.trueLiteral());
    }
}