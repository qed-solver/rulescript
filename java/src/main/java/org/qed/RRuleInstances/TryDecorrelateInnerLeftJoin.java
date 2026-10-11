package org.qed.RRuleInstances;
// SCOPE: PARTIAL — plain (non-Apply) InnerJoin/LeftJoin over single-column scans; on and innerOn are uninterpreted 2-ary predicates
import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public record TryDecorrelateInnerLeftJoin() implements RRule {
  static final RelRN L  = RelRN.scan("L",  RexRN.varType("T", true), false);
  static final RelRN IL = RelRN.scan("IL", RexRN.varType("T", true), false);
  static final RelRN IR = RelRN.scan("IR", RexRN.varType("T", true), false);
  static final SqlOperator on      = RuleBuilder.create().genericPredicateOp("on", true);
  static final SqlOperator innerOn = RuleBuilder.create().genericPredicateOp("innerOn", true);

  static final RelRN inB = IL.join(JoinRelType.LEFT,
      new RexRN.Pred(innerOn, IL.joinFields(IR)), IR);
  static final RelRN inA = L.join(JoinRelType.INNER,
      new RexRN.Pred(on, L.joinFields(IL)), IL);

  @Override public RelRN before() {
    return L.join(JoinRelType.INNER,
        new RexRN.Pred(on, L.joinFields(inB, 0, 1)), inB);
  }
  @Override public RelRN after() {
    return inA.join(JoinRelType.LEFT,
        new RexRN.Pred(innerOn, Seq.of(inA.joinField(1, IR), inA.joinField(2, IR))), IR);
  }
}
