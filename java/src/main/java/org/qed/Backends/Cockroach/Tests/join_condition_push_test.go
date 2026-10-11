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
	leftConj := e.f.ConstructGt(e.v(lc[0]), e.num(1))
	rightConj := e.f.ConstructLt(e.v(rc[0]), e.num(5))
	crossConj := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	cond := e.f.ConstructAnd(e.f.ConstructAnd(leftConj, rightConj), crossConj)
	before := e.f.ConstructInnerJoin(l, r, e.filters(cond), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(
		e.f.ConstructSelect(l, e.filters(leftConj)),
		e.f.ConstructSelect(r, e.filters(rightConj)),
		e.filters(crossConj),
		&memo.JoinPrivate{},
	)
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, e.filters(cond), &memo.JoinPrivate{}))
	disjunction := e.f.ConstructOr(e.f.ConstructOr(leftConj, rightConj), crossConj)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(disjunction), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(leftConj), &memo.JoinPrivate{}))
}
