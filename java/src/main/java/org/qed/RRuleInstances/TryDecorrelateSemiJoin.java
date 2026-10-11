package org.qed.RRuleInstances;
// SCOPE: PARTIAL — left is a fully-keyed (unique) single-column scan (modeling EnsureKey whose key is the column), right is a single-column scan, the correlate condition is an uninterpreted 2-ary predicate, and left has no non-key columns (so no ConstAgg output columns)
import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public record TryDecorrelateSemiJoin() implements RRule {
    // EnsureKey($left): single column, fully keyed so KeyCols = {c0} and NonKeyCols = {}
    static final RelRN L = RelRN.scan("L", RexRN.varType("TL", true), true);
    // $right: single-column scan, CanHaveZeroRows, rows may repeat
    static final RelRN R = RelRN.scan("R", RexRN.varType("TR", true), false);
    // Uninterpreted binary correlate condition on(left.c0, right.c0)
    static final SqlOperator on = RuleBuilder.create().genericPredicateOp("on", true);

    @Override
    public RelRN before() {
        return L.correlate(JoinRelType.SEMI, on, R); // SemiJoin($left, $right, $on)
    }

    @Override
    public RelRN after() {
        // GroupBy( InnerJoin(L, R, on) , grouping by KeyCols(L)={c0}, no aggs ).
        // Plain field(0) (not an uninterpreted groupBy op) keeps the group-output column
        // type equal to L's, and the source's outer Project(OutputCols $left) is the
        // identity here, so we return the group directly.
        RelRN inner = L.correlate(JoinRelType.INNER, on, R);
        return new RelRN.Aggregate(inner, Seq.of(inner.field(0)), Seq.empty());
    }
}
