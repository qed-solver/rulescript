package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestFilterRemoveIsNotDistinctFrom(t *testing.T) {
	rule := loadRule(t, "FilterRemoveIsNotDistinctFrom")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	join := e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructTrue()), &memo.JoinPrivate{})
	before := e.f.ConstructSelect(join, e.filters(e.f.ConstructIs(e.v(lc[0]), e.v(rc[0]))))
	want := e.f.ConstructSelect(join, e.filters(e.f.ConstructOr(
		e.f.ConstructAnd(
			e.f.ConstructIs(e.v(lc[0]), memo.NullSingleton),
			e.f.ConstructIs(e.v(rc[0]), memo.NullSingleton),
		),
		e.f.ConstructIs(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])), memo.TrueSingleton),
	)))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructSelect(join, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))))

	left := e.f.ConstructLeftJoin(l, r, e.filters(e.f.ConstructTrue()), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(left, e.filters(e.f.ConstructIs(e.v(lc[0]), e.v(rc[0])))))

	e.expectNoMatch(rule, e.f.ConstructSelect(l, e.filters(e.f.ConstructIs(e.v(lc[0]), e.v(lc[0])))))
}
