package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestTryDecorrelateSemiJoin(t *testing.T) {
	rule := loadRule(t, "TryDecorrelateSemiJoin")
	e := newEnv(t)

	left, leftCols := e.scan("u", 1)
	right, rightCols := e.scan("t", 1)
	condition := e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0]))
	before := e.f.ConstructSemiJoinApply(left, right, e.filters(condition), memo.EmptyJoinPrivate)
	inner := e.f.ConstructInnerJoinApply(left, right, e.filters(condition), memo.EmptyJoinPrivate)
	want := e.f.ConstructGroupBy(inner, memo.EmptyAggregationsExpr, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(leftCols[0])})
	e.expectRewrite(rule, before, want)

	left, leftCols = e.scan("u", 1)
	right, rightCols = e.scan("t", 1)
	condition = e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0]))
	nearMiss := e.f.ConstructSemiJoin(left, right, e.filters(condition), memo.EmptyJoinPrivate)
	e.expectNoMatch(rule, nearMiss)
}
