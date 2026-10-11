package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — left join (source rule also covers inner-join and the apply variants) with a 1-column left input and 2-column right input, the passthrough project swapping the right's two columns, and the on-clause an uninterpreted predicate over (left, right) columns
public record HoistJoinProjectRight() implements RRule {
    // Left input: one column (l0).
    static final RelRN left = RelRN.scan("L", "L_Type");
    // Right input (the Project's input): two columns (r0, r1).
    static final RelRN input = RelRN.scanMany("R", Seq.of(
            new RelType.VarType("R0_Type", true),
            new RelType.VarType("R1_Type", true)));

    // The passthrough (remapping) project on the right input: it simply
    // remaps input columns to a different order, output = (r1, r0).
    static final RelRN projInput = input.project(Seq.of(input.field(1), input.field(0)));

    // Shared uninterpreted on-clause symbol C, over the values (l0, r1, r0);
    // after hoisting the same symbol is reused with its right-side column
    // references rebound to the un-remapped input columns.
    static final SqlOperator cOp = RuleBuilder.create().genericPredicateOp("C", true);

    // Before: LeftJoin(L, Project(R, (r1, r0)), C(l0, r1, r0)).
    // Join output ordinals: 0 = l0, 1 = r1, 2 = r0.
    static final RexRN condBefore = new RexRN.Pred(cOp, Seq.of(
            left.joinField(0, projInput),
            left.joinField(1, projInput),
            left.joinField(2, projInput)));
    static final RelRN joinBefore = left.join(JoinRelType.LEFT, condBefore, projInput);

    // After: LeftJoin(L, R, C(l0, r1, r0) with references unbound to the raw
    // input columns), with the same remap projected above the join: (l0, r1, r0).
    // Join output ordinals: 0 = l0, 1 = r0, 2 = r1.
    static final RexRN condAfter = new RexRN.Pred(cOp, Seq.of(
            left.joinField(0, input),
            left.joinField(2, input),
            left.joinField(1, input)));
    static final RelRN joinAfter = left.join(JoinRelType.LEFT, condAfter, input);
    static final RelRN after = joinAfter.project(Seq.of(
            joinAfter.field(0), joinAfter.field(2), joinAfter.field(1)));

    @Override
    public RelRN before() {
        return joinBefore;
    }

    @Override
    public RelRN after() {
        return after;
    }
}
