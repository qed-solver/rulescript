package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestExpandDisjunctionForJoinInputs(t *testing.T) {
	rule := loadRule(t, "ExpandDisjunctionForJoinInputs")
	e := newEnv(t)
	l, lc := e.scan("u", 2)
	r, rc := e.scan("t", 2)
	pl := e.f.ConstructGt(e.v(lc[0]), e.num(0))
	rl := e.f.ConstructGt(e.v(lc[1]), e.num(1))
	pr := e.f.ConstructLt(e.v(rc[0]), e.num(5))
	sr := e.f.ConstructLt(e.v(rc[1]), e.num(7))
	cond := e.f.ConstructOr(e.f.ConstructAnd(pl, pr), e.f.ConstructAnd(rl, sr))
	before := e.f.ConstructInnerJoin(l, r, e.filters(cond), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(l, r, e.filters(
		cond,
		e.f.ConstructOr(pl, rl),
		e.f.ConstructOr(pr, sr),
	), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, e.filters(cond), &memo.JoinPrivate{}))
	m := e.f.ConstructLt(e.v(lc[1]), e.num(9))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(
		e.f.ConstructOr(e.f.ConstructAnd(pl, m), e.f.ConstructAnd(rl, sr)),
	), &memo.JoinPrivate{}))
}
