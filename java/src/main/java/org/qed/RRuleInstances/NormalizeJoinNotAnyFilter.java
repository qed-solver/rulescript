package org.qed.RRuleInstances;

import com.google.common.collect.ImmutableSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RuleBuilder;

// ORIGIN: CockroachDB
// SCOPE: PARTIAL — an inner join whose ON clause is the single conjunct NOT (x = ANY(sub)), with x the first left column and sub a single-column relation
public record NormalizeJoinNotAnyFilter() implements RRule {
    static final RelRN left = RelRN.scan("Left", "V");
    static final RelRN right = RelRN.scan("Right", "W");
    static final RelRN sub = RelRN.scan("Sub", "V");

    private record NotAnyJoin(RelRN l, RelRN r, RelRN inner, boolean exists) implements RelRN {
        @Override
        public RelNode semantics() {
            var b = RuleBuilder.create();
            RelNode ln = l.semantics();
            RelNode rn = r.semantics();
            RelNode sn = inner.semantics();
            var rb = b.getRexBuilder();
            if (!exists) {
                RexNode in = b.in(sn, java.util.List.of(rb.makeInputRef(ln, 0)));
                return LogicalJoin.create(ln, rn, java.util.List.of(), rb.makeCall(SqlStdOperatorTable.NOT, in),
                        ImmutableSet.of(), JoinRelType.INNER);
            }
            var joined = LogicalJoin.create(ln, rn, java.util.List.of(), rb.makeLiteral(true), ImmutableSet.of(), JoinRelType.INNER);
            var corrId = b.getCluster().createCorrel();
            var corrVar = rb.makeCorrel(joined.getRowType(), corrId);
            RexNode cond = rb.makeCall(SqlStdOperatorTable.EQUALS, rb.makeFieldAccess(corrVar, 0),
                    rb.makeInputRef(sn.getRowType().getFieldList().get(0).getType(), 0));
            RexNode notFalse = rb.makeCall(SqlStdOperatorTable.OR, cond, rb.makeCall(SqlStdOperatorTable.IS_NULL, cond));
            RexNode ex = RexSubQuery.exists(org.apache.calcite.rel.logical.LogicalFilter.create(sn, notFalse));
            return LogicalJoin.create(ln, rn, java.util.List.of(), rb.makeCall(SqlStdOperatorTable.NOT, ex),
                    ImmutableSet.of(corrId), JoinRelType.INNER);
        }
    }

    @Override
    public RelRN before() {
        return new NotAnyJoin(left, right, sub, false);
    }

    @Override
    public RelRN after() {
        return new NotAnyJoin(left, right, sub, true);
    }
}
