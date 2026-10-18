package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPushDownPredicates(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PushDownPredicates")
	left, leftCols := e.scan("t", 1)
	right, rightCols := e.scan("u", 1)
	joinCond := e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0])))
	join := e.f.ConstructInnerJoin(left, right, joinCond, &memo.JoinPrivate{})
	before := e.f.ConstructSelect(join, e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.num(7))))
	wantLeft := e.f.ConstructSelect(left, e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.num(7))))
	want := e.f.ConstructInnerJoin(wantLeft, right, joinCond, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructSelect(join, e.filters(e.f.ConstructEq(e.v(rightCols[0]), e.num(7))))
	e.expectNoMatch(rule, nearMiss)
}
