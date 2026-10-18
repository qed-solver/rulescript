package org.qed.RRuleInstances;

import com.google.common.collect.ImmutableSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — the correlated `Filter(EXISTS(sub))` -> LEFT SEMI join branch on a single equality correlation key over single-column relations; the NOT EXISTS / IN / NOT IN (null-aware) branches, multi-key conditions and domain-join handling are not encoded
public record RewritePredicateSubquery() implements RRule {
    static final RelRN left = RelRN.scan("L", "K_type");
    static final RelRN right = RelRN.scan("R", "K_type");

    private record ExistsFilter(RelRN outer, RelRN inner) implements RelRN {
        @Override
        public RelNode semantics() {
            var b = RuleBuilder.create();
            var l = outer.semantics();
            var r = inner.semantics();
            var rb = b.getRexBuilder();
            var corrId = b.getCluster().createCorrel();
            var corrVar = rb.makeCorrel(l.getRowType(), corrId);
            RexNode cond = rb.makeCall(SqlStdOperatorTable.EQUALS, rb.makeFieldAccess(corrVar, 0),
                    rb.makeInputRef(r.getRowType().getFieldList().get(0).getType(), 0));
            RelNode sub = LogicalFilter.create(r, cond);
            return LogicalFilter.create(l, RexSubQuery.exists(sub), ImmutableSet.of(corrId));
        }
    }

    @Override
    public RelRN before() {
        return new ExistsFilter(left, right);
    }

    @Override
    public RelRN after() {
        return left.join(JoinRelType.SEMI, left.joinPred(SqlStdOperatorTable.EQUALS, right), right);
    }
}
