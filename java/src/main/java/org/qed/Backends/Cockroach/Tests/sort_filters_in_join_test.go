package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSortFiltersInJoin(t *testing.T) {
	rule := loadRule(t, "SortFiltersInJoin")
	t.Run("reorders_two_join_predicates", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("u", 2)
		right, rc := e.scan("t", 2)
		a := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
		b := e.f.ConstructEq(e.v(lc[1]), e.v(rc[1]))
		before := e.f.ConstructInnerJoin(left, right, e.filters(a, b), &memo.JoinPrivate{})
		want := e.f.ConstructInnerJoin(left, right, e.filters(b, a), &memo.JoinPrivate{})
		e.expectRewrite(rule, before, want)
	})
	t.Run("rejects_single_join_predicate", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("u", 1)
		right, rc := e.scan("t", 1)
		cond := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
		plan := e.f.ConstructInnerJoin(left, right, e.filters(cond), &memo.JoinPrivate{})
		e.expectNoMatch(rule, plan)
	})
}
