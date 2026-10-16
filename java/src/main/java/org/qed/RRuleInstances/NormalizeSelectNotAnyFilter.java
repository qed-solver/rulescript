package org.qed.RRuleInstances;

import com.google.common.collect.ImmutableSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: CockroachDB
// SCOPE: PARTIAL — the ANY comparison is fixed to equality (NOT (x = ANY(sub)), i.e. NOT IN) over a single-column subquery, and the filter holds only the NOT ANY conjunct
public record NormalizeSelectNotAnyFilter() implements RRule {
    static final RelRN input = RelRN.scan("Input", "V");
    static final RelRN sub = RelRN.scan("Sub", "V");

    private record AnyFilter(RelRN outer, RelRN inner) implements RelRN {
        @Override
        public RelNode semantics() {
            var b = RuleBuilder.create();
            RelNode l = outer.semantics();
            RexNode in = b.in(inner.semantics(), java.util.List.of(b.getRexBuilder().makeInputRef(l, 0)));
            return LogicalFilter.create(l, b.getRexBuilder().makeCall(SqlStdOperatorTable.NOT, in));
        }
    }

    private record ExistsFilter(RelRN outer, RelRN inner) implements RelRN {
        @Override
        public RelNode semantics() {
            var b = RuleBuilder.create();
            RelNode l = outer.semantics();
            RelNode r = inner.semantics();
            var rb = b.getRexBuilder();
            var corrId = b.getCluster().createCorrel();
            var corrVar = rb.makeCorrel(l.getRowType(), corrId);
            RexNode cond = rb.makeCall(SqlStdOperatorTable.EQUALS, rb.makeFieldAccess(corrVar, 0),
                    rb.makeInputRef(r.getRowType().getFieldList().get(0).getType(), 0));
            RexNode notFalse = rb.makeCall(SqlStdOperatorTable.OR, cond, rb.makeCall(SqlStdOperatorTable.IS_NULL, cond));
            RexNode exists = RexSubQuery.exists(LogicalFilter.create(r, notFalse));
            return LogicalFilter.create(l, rb.makeCall(SqlStdOperatorTable.NOT, exists), ImmutableSet.of(corrId));
        }
    }

    @Override
    public RelRN before() {
        return new AnyFilter(input, sub);
    }

    @Override
    public RelRN after() {
        return new ExistsFilter(input, sub);
    }
}
