package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestOptimizeJoinCondition(t *testing.T) {
	rule := loadRule(t, "OptimizeJoinCondition")
	e := newEnv(t)
	left, leftCols := e.scan("t", 1)
	right, rightCols := e.scan("u", 1)
	leftValue := e.v(leftCols[0])
	rightValue := e.v(rightCols[0])
	equal := e.f.ConstructEq(leftValue, rightValue)
	bothNull := e.f.ConstructAnd(
		e.f.ConstructIs(leftValue, memo.NullSingleton),
		e.f.ConstructIs(rightValue, memo.NullSingleton),
	)
	before := e.f.ConstructInnerJoin(left, right, e.filters(e.f.ConstructOr(equal, bothNull)), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(left, right, e.filters(e.f.ConstructIs(leftValue, rightValue)), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructInnerJoin(left, right, e.filters(e.f.ConstructAnd(equal, bothNull)), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}

