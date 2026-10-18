package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestFilterJoin(t *testing.T) {
	rule := loadRule(t, "FilterJoin")
	e := newEnv(t)
	l, lc := e.scan("t", 3)
	r, rc := e.scan("u", 2)
	joinCond := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	aboveFilter := e.f.ConstructGt(e.v(lc[1]), e.num(5))
	before := e.f.ConstructSelect(
		e.f.ConstructInnerJoin(l, r, e.filters(joinCond), &memo.JoinPrivate{}),
		e.filters(aboveFilter),
	)
	want := e.f.ConstructInnerJoin(
		l, r,
		e.filters(joinCond, aboveFilter),
		&memo.JoinPrivate{},
	)
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructLeftJoin(l, r, e.filters(joinCond), &memo.JoinPrivate{}),
		e.filters(aboveFilter),
	))
	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructSemiJoin(l, r, e.filters(joinCond), &memo.JoinPrivate{}),
		e.filters(aboveFilter),
	))
	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructInnerJoinApply(l, r, e.filters(joinCond), &memo.JoinPrivate{}),
		e.filters(aboveFilter),
	))
	e.expectNoMatch(rule, e.f.ConstructSelect(l, e.filters(aboveFilter)))
}
