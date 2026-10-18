package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinConditionPush(t *testing.T) {
	rule := loadRule(t, "JoinConditionPush")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	leftCond := e.f.ConstructEq(e.v(lc[0]), e.num(1))
	rightCond := e.f.ConstructEq(e.v(rc[0]), e.num(2))
	crossCond := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	on := e.filters(leftCond, rightCond, crossCond)
	before := e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(
		e.f.ConstructSelect(l, e.filters(leftCond)),
		e.f.ConstructSelect(r, e.filters(rightCond)),
		e.filters(crossCond),
		&memo.JoinPrivate{},
	)
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructInnerJoin(l, r, e.filters(leftCond, crossCond), &memo.JoinPrivate{})
	e.expectRewrite(rule, nearMiss, e.f.ConstructInnerJoin(
		e.f.ConstructSelect(l, e.filters(leftCond)), r, e.filters(crossCond), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}))
}
