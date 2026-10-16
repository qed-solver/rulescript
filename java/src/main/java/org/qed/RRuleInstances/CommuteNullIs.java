package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;
import org.qed.RexRN.Pred;
import org.qed.RuleBuilder;

// ORIGIN: CockroachDB
// SCOPE: PARTIAL — only the Is (IS NOT DISTINCT FROM) branch of (Is | IsNot) is encoded; the structurally identical IsNot branch requires its own before()/after() pair
public record CommuteNullIs() implements RRule {
    // $right:^(Null): an arbitrary expression, modeled as a nullable column of
    // the shared uninterpreted type V. No nullability assumption is made: the
    // ^(Null) side-condition in the source rule only avoids a no-op rematch,
    // it is not a semantic precondition — IS/IS NOT are symmetric even when
    // both operands are NULL.
    static final RelType.VarType V = new RelType.VarType("V", true);
    static final RelRN source = RelRN.scan("Source", V, false);
    static final RexRN x = source.field(0);

    // $left:(Null): the NULL literal of type V.
    static final RexRN nullV = new RexRN() {
        @Override
        public RexNode semantics() {
            return RuleBuilder.create().getRexBuilder().makeNullLiteral(V);
        }
    };

    @Override
    public RelRN before() {
        // (Is $left:(Null) $right:^(Null))
        return source.filter(
            new Pred(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, Seq.of(nullV, x)));
    }

    @Override
    public RelRN after() {
        // ((OpName) $right $left) with OpName = Is: IS_NOT_DISTINCT_FROM is
        // symmetric, including when x is NULL
        // (NULL IS NOT DISTINCT FROM NULL = TRUE).
        return source.filter(
            new Pred(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, Seq.of(x, nullV)));
    }
}