package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinReduceExpressions(t *testing.T) {
	rule := loadRule(t, "JoinReduceExpressions")
	e := newEnv(t)
	left, leftCols := e.scan("u", 1)
	right, _ := e.scan("t", 1)
	predicate := e.f.ConstructEq(e.v(leftCols[0]), e.num(7))
	before := e.f.ConstructInnerJoin(left, right, e.filters(e.f.ConstructAnd(predicate, e.f.ConstructFalse())), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(left, right, e.filters(e.f.ConstructFalse()), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	left2, leftCols2 := e.scan("u", 1)
	right2, _ := e.scan("t", 1)
	nearMiss := e.f.ConstructInnerJoin(left2, right2, e.filters(e.f.ConstructAnd(e.f.ConstructEq(e.v(leftCols2[0]), e.num(7)), e.f.ConstructTrue())), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
