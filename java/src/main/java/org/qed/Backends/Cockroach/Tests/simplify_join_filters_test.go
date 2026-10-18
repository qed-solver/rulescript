package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSimplifyJoinFilters(t *testing.T) {
	rule := loadRule(t, "SimplifyJoinFilters")

	t.Run("removes true after equality", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("t", 2)
		right, rc := e.scan("u", 2)
		eq := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
		before := e.f.ConstructInnerJoin(left, right, e.filters(e.f.ConstructAnd(eq, e.f.ConstructTrue())), &memo.JoinPrivate{})
		want := e.f.ConstructInnerJoin(left, right, e.filters(eq), &memo.JoinPrivate{})
		e.expectRewrite(rule, before, want)
	})

	t.Run("removes true after inequality", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("u", 2)
		right, rc := e.scan("t", 2)
		gt := e.f.ConstructGt(e.v(lc[1]), e.v(rc[1]))
		before := e.f.ConstructInnerJoin(left, right, e.filters(e.f.ConstructAnd(gt, e.f.ConstructTrue())), &memo.JoinPrivate{})
		want := e.f.ConstructInnerJoin(left, right, e.filters(gt), &memo.JoinPrivate{})
		e.expectRewrite(rule, before, want)
	})

	t.Run("does not match without true", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("t", 2)
		right, rc := e.scan("u", 2)
		eq := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
		plan := e.f.ConstructInnerJoin(left, right, e.filters(eq), &memo.JoinPrivate{})
		e.expectNoMatch(rule, plan)
	})
}
