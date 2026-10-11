package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestExpandDisjunctionForJoinInputs(t *testing.T) {
	rule := loadRule(t, "ExpandDisjunctionForJoinInputs")
	e := newEnv(t)
	l, lc := e.scan("t", 2)
	r, rc := e.scan("u", 2)

	pl := e.f.ConstructEq(e.v(lc[0]), e.num(1))
	rl := e.f.ConstructEq(e.v(lc[1]), e.num(2))
	pr := e.f.ConstructEq(e.v(rc[0]), e.num(3))
	sr := e.f.ConstructEq(e.v(rc[1]), e.num(4))
	cond := e.f.ConstructOr(e.f.ConstructAnd(pl, pr), e.f.ConstructAnd(rl, sr))
	before := e.f.ConstructInnerJoin(l, r, e.filters(cond), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(l, r, e.filters(
		cond,
		e.f.ConstructOr(pl, rl),
		e.f.ConstructOr(pr, sr),
	), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, e.filters(cond), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(
		e.f.ConstructAnd(e.f.ConstructAnd(pl, pr), e.f.ConstructAnd(rl, sr)),
	), &memo.JoinPrivate{}))
	crossSide := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(
		e.f.ConstructOr(e.f.ConstructAnd(crossSide, pr), e.f.ConstructAnd(rl, sr)),
	), &memo.JoinPrivate{}))
}
