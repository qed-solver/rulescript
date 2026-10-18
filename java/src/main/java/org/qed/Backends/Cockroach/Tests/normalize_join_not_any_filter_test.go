package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestNormalizeJoinNotAnyFilter(t *testing.T) {
	rule := loadRule(t, "NormalizeJoinNotAnyFilter")
	t.Run("match", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("t", 1)
		right, _ := e.scan("u", 1)
		sub, sc := e.scan("nn", 1)
		before := e.f.ConstructInnerJoin(left, right, e.filters(
			e.f.ConstructNot(e.f.ConstructAny(sub, e.v(lc[0]), &memo.SubqueryPrivate{Cmp: opt.EqOp})),
		), &memo.JoinPrivate{})
		cond := e.f.ConstructEq(e.v(lc[0]), e.v(sc[0]))
		notFalse := e.f.ConstructOr(cond, e.f.ConstructIs(cond, memo.NullSingleton))
		exists := e.exists(e.f.ConstructSelect(sub, e.filters(notFalse)))
		want := e.f.ConstructInnerJoin(left, right, e.filters(e.f.ConstructNot(exists)), &memo.JoinPrivate{})
		e.expectRewrite(rule, before, want)
	})
	t.Run("rejects_multiple_join_conjuncts", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("t", 1)
		right, _ := e.scan("u", 1)
		sub, _ := e.scan("nn", 1)
		before := e.f.ConstructInnerJoin(left, right, e.filters(
			e.f.ConstructNot(e.f.ConstructAny(sub, e.v(lc[0]), &memo.SubqueryPrivate{Cmp: opt.EqOp})),
			e.f.ConstructEq(e.v(lc[0]), e.num(7)),
		), &memo.JoinPrivate{})
		e.expectNoMatch(rule, before)
	})
}
