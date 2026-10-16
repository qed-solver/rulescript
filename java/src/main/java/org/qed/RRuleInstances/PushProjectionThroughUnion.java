package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// ORIGIN: Spark
// SCOPE: PARTIAL — two-branch UNION ALL, both inputs one shared fixed-width schema, single uninterpreted whole-row projection G (source fires on arbitrary arity, any deterministic projection list, and also SequentialStreamingUnion)
public record PushProjectionThroughUnion() implements RRule {
    // Both union branches share the same two-column schema (T0, T1), so the
    // UNION ALL is well-formed and column ordinals line up across branches.
    static final RelType.VarType t0 = new RelType.VarType("T0_Type", true);
    static final RelType.VarType t1 = new RelType.VarType("T1_Type", true);

    static final RelRN left = RelRN.scanMany("L", Seq.of(t0, t1));
    static final RelRN right = RelRN.scanMany("R", Seq.of(t0, t1));

    // G — one uninterpreted whole-row projection. Reusing the same symbol in
    // before() and in both after() branches is what tells QED they are the
    // same projection, mirroring Spark reusing projectList per branch.
    static final SqlOperator g = RuleBuilder.create()
            .genericProjectionOp("G", new RelType.VarType("G_Type", true));

    static final RelRN unionAll = left.union(true, right);

    static final RexRN gOverUnion = unionAll.proj(g);
    static final RexRN gOverLeft = left.proj(g);
    static final RexRN gOverRight = right.proj(g);

    @Override
    public RelRN before() {
        return unionAll.project(gOverUnion);
    }

    @Override
    public RelRN after() {
        return left.project(gOverLeft).union(true, right.project(gOverRight));
    }
}
