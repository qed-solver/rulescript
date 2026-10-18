package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSimplifyZeroCardinalitySemiJoin(t *testing.T) {
	rule := loadRule(t, "SimplifyZeroCardinalitySemiJoin")
	e := newEnv(t)
	left, leftCols := e.scan("u", 1)
	right, rightCols := e.scan("t", 1)
	right = e.empty(rightCols...)
	on := e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.num(1)))
	before := e.f.ConstructSemiJoin(left, right, on, &memo.JoinPrivate{})
	want := e.empty(leftCols...)
	e.expectRewrite(rule, before, want)

	left2, leftCols2 := e.scan("u", 1)
	right2, rightCols2 := e.scan("t", 1)
	nearMiss := e.f.ConstructSemiJoin(left2, right2, e.filters(e.f.ConstructEq(e.v(leftCols2[0]), e.v(rightCols2[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)

}
