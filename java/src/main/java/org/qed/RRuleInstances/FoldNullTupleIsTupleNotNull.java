package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — the tuple is fixed to arity 2 with exactly one element being the constant NULL (satisfying the side condition HasNullElement) and the other element an arbitrary nullable value.
public record FoldNullTupleIsTupleNotNull() implements RRule {
    // The constant NULL element of the tuple. The source rule's side condition
    // (HasNullElement $input) requires at least one element to be the constant
    // NULL; with the tuple fixed to arity 2, element 0 is that constant NULL,
    // encoded as the shared NULL literal exactly as FoldNullTupleIsTupleNull
    // encodes a (Null) tuple element.
    static final RexRN nullLit = new RexRN() {
        @Override
        public RexNode semantics() {
            var b = RuleBuilder.create();
            return b.getRexBuilder().makeNullLiteral(b.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        }
    };

    // Relational home for the scalar fold; its single nullable column is the
    // tuple's other element, left completely arbitrary (the side condition only
    // pins one element down to the constant NULL).
    static final RelRN source = RelRN.scan("Source", "Source_Type");

    // IsTupleNotNull(x) holds iff no element of the tuple x is null — the
    // operator's defining semantics is the conjunction of its elements' IS NOT
    // NULL predicates (as in FoldNonNullTupleIsTupleNotNull). Element 0 is the
    // constant NULL, so its IS NOT NULL conjunct is constant False, which is
    // precisely the constant fold this rule performs.
    static RexRN isTupleNotNull() {
        return RexRN.and(
                new RexRN.Pred(SqlStdOperatorTable.IS_NOT_NULL, Seq.of(nullLit)),
                source.field(0).pred(SqlStdOperatorTable.IS_NOT_NULL));
    }

    @Override
    public RelRN before() {
        // (IsTupleNotNull $input & HasNullElement $input)
        return source.filter(isTupleNotNull());
    }

    @Override
    public RelRN after() {
        // (False)
        return source.filter(RexRN.falseLiteral());
    }
}