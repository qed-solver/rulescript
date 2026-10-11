// SCOPE: PARTIAL — fixed-shape pattern: a 2-column left and a 2-column zero-row right (both uninterpreted scans), versus the source rule's arbitrary-width $left/$right.
package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public record SimplifyLeftJoinWithZeroRowsRight() implements RRule {
    static final RelType.VarType l0 = new RelType.VarType("Left_Type0", true);
    static final RelType.VarType l1 = new RelType.VarType("Left_Type1", true);
    static final RelType.VarType r0 = new RelType.VarType("Right_Type0", true);
    static final RelType.VarType r1 = new RelType.VarType("Right_Type1", true);

    static final RelRN left = RelRN.scanMany("Left", Seq.of(l0, l1));
    // $right with (HasZeroRows $right): in QED's bag model a zero-row input is
    // exactly the empty relation of its type.
    static final RelRN right = RelRN.scanMany("Right", Seq.of(r0, r1)).empty();

    // NULL literals standing in for each right column, typed by that column's
    // type so they match the left-join's null-extension.
    static final RexRN nullR0 = new RexRN() {
        @Override
        public RexNode semantics() {
            var b = RuleBuilder.create();
            return b.getRexBuilder().makeNullLiteral(r0);
        }
    };
    static final RexRN nullR1 = new RexRN() {
        @Override
        public RexNode semantics() {
            var b = RuleBuilder.create();
            return b.getRexBuilder().makeNullLiteral(r1);
        }
    };

    @Override
    public RelRN before() {
        // LeftJoin($left, $right) with $right empty: the join condition can
        // never match, so every left row is emitted null-extended over the
        // right columns.
        return left.join(JoinRelType.LEFT, "join", right);
    }

    @Override
    public RelRN after() {
        // Project($left, OutputCols($left) ++ MakeNullProjections($right)):
        // pass through the left columns, emit NULL for each right column.
        return left.project(Seq.of(
                left.field(0),
                left.field(1),
                nullR0,
                nullR1
        ));
    }
}
