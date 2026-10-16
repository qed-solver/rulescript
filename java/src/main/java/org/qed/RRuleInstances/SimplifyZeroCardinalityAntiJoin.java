package org.qed.RRuleInstances;

import com.google.common.collect.ImmutableList;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RRule;
import org.qed.RuleBuilder;

// ORIGIN: CockroachDB
// SCOPE: PARTIAL — the ^(CanHaveZeroRows $right) precondition is encoded as a concrete one-row VALUES relation (single INTEGER column) rather than an arbitrary non-empty relation, and the correlated AntiJoinApply variant is not modeled.
public record SimplifyZeroCardinalityAntiJoin() implements RRule {
    /**
     * A relation known to be non-empty: a VALUES relation holding exactly one
     * row. Column type/contents are irrelevant for an ON-TRUE anti-join, so a
     * single-row VALUES stands in for the "^ CanHaveZeroRows" guarantee.
     */
    record OneRow() implements RelRN {
        @Override
        public RelNode semantics() {
            var builder = RuleBuilder.create();
            var rex = builder.getRexBuilder();
            var colType = rex.getTypeFactory().createSqlType(SqlTypeName.INTEGER);
            var row = ImmutableList.of(rex.makeLiteral(0, colType));
            var rowType = rex.getTypeFactory().builder().add("col0", colType).build();
            return builder.values(ImmutableList.of(row), rowType).build();
        }
    }

    static final RelRN left = RelRN.scan("L", "L_Type");
    // $right & ^(CanHaveZeroRows $right): a right input that never returns zero rows,
    // encoded here as a concrete 1-row VALUES relation.
    static final RelRN right = new OneRow();

    @Override
    public RelRN before() {
        // L ANTI-JOIN R ON true (the source rule's empty on-list), with R non-empty.
        return left.join(JoinRelType.ANTI, RexRN.trueLiteral(), right);
    }

    @Override
    public RelRN after() {
        // Every left row is excluded, so the result is the empty relation over L's columns.
        return left.empty();
    }
}
