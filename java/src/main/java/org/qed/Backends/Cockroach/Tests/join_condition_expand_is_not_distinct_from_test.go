package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinConditionExpandIsNotDistinctFrom(t *testing.T) {
	rule := loadRule(t, "JoinConditionExpandIsNotDistinctFrom")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	before := e.f.ConstructInnerJoin(l, r, e.filters(
		e.f.ConstructIs(e.v(lc[0]), e.v(rc[0])),
	), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructOr(
		e.f.ConstructAnd(
			e.f.ConstructIs(e.v(lc[0]), memo.NullSingleton),
			e.f.ConstructIs(e.v(rc[0]), memo.NullSingleton),
		),
		e.f.ConstructIs(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])), memo.TrueSingleton),
	)), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(
		e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])),
	), &memo.JoinPrivate{}))

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, e.filters(
		e.f.ConstructIs(e.v(lc[0]), e.v(rc[0])),
	), &memo.JoinPrivate{}))

	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(
		e.f.ConstructIs(e.f.ConstructPlus(e.v(lc[0]), e.num(1)), e.v(rc[0])),
	), &memo.JoinPrivate{}))
}
