package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// ORIGIN: CockroachDB
// SCOPE: PARTIAL — the input row has exactly 4 columns and the mutation returns exactly 4 computed columns (each an uninterpreted function of all 4 input columns), one of which (a non-PK return column) is unused and pruned, one of which (a PK return column) is unused but retained, and the outer projection uses one return column through one computed expression plus one passthrough
public record PruneMutationReturnCols() implements RRule {
    static final RelRN input = RelRN.scanMany("I", Seq.of(
            new RelType.VarType("I0_Type", true),
            new RelType.VarType("I1_Type", true),
            new RelType.VarType("I2_Type", true),
            new RelType.VarType("I3_Type", true)));
    static final Seq<RexRN> in = Seq.of(input.field(0), input.field(1), input.field(2), input.field(3));

    static final RexRN r0 = new RexRN.Proj(
            RuleBuilder.create().genericProjectionOp("M0", new RelType.VarType("M0_Type", true)), in);
    static final RexRN r1 = new RexRN.Proj(
            RuleBuilder.create().genericProjectionOp("M1", new RelType.VarType("M1_Type", true)), in);
    static final RexRN r2 = new RexRN.Proj(
            RuleBuilder.create().genericProjectionOp("M2", new RelType.VarType("M2_Type", true)), in);
    static final RexRN r3 = new RexRN.Proj(
            RuleBuilder.create().genericProjectionOp("M3", new RelType.VarType("M3_Type", true)), in);

    static final RelRN beforeMid = input.project(Seq.of(r0, r1, r2, r3));
    static final RelRN afterMid = input.project(Seq.of(r0, r2, r3));

    static final SqlOperator pOp = RuleBuilder.create()
            .genericProjectionOp("P", new RelType.VarType("P_Type", true));
    static final RelRN beforeRel = beforeMid.project(
            Seq.of(new RexRN.Proj(pOp, Seq.of(beforeMid.field(0))), beforeMid.field(3)));
    static final RelRN afterRel = afterMid.project(
            Seq.of(new RexRN.Proj(pOp, Seq.of(afterMid.field(0))), afterMid.field(2)));

    @Override
    public RelRN before() {
        return beforeRel;
    }

    @Override
    public RelRN after() {
        return afterRel;
    }
}