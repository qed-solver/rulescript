package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — encodes only the In branch over a fixed 2-element non-empty tuple; NotIn and other non-empty arities are structurally identical but each needs its own before()/after() pair
public record FoldNullInNonEmpty() implements RRule {
    static final RelType.VarType V = new RelType.VarType("V", true);
    static final RelRN source = RelRN.scanMany("Source", Seq.of(V, V));
    static final RexRN a = source.field(0);
    static final RexRN b = source.field(1);

    static final RexRN nullV = new RexRN() {
        @Override
        public RexNode semantics() {
            return RuleBuilder.create().getRexBuilder().makeNullLiteral(V);
        }
    };

    static final RexRN nullBool = new RexRN() {
        @Override
        public RexNode semantics() {
            var b = RuleBuilder.create();
            return b.getRexBuilder().makeNullLiteral(
                b.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        }
    };

    @Override
    public RelRN before() {
        // In(Null, (a, b)) expands definitionally to (Null = a) OR (Null = b).
        return source.filter(new RexRN.Or(Seq.of(
            new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(nullV, a)),
            new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(nullV, b))
        )));
    }

    @Override
    public RelRN after() {
        // With a non-empty tuple the membership is unknown (null), so the filter
        // collapses to the null-boolean predicate — no row passes.
        return source.filter(nullBool);
    }
}
