package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSemiJoinFilterTranspose(t *testing.T) {
	rule := loadRule(t, "SemiJoinFilterTranspose")
	t.Run("transposes left-only filter", func(t *testing.T) {
		e := newEnv(t)
		x, xc := e.scan("t", 2)
		y, yc := e.scan("u", 2)
		filter := e.f.ConstructEq(e.v(xc[1]), e.num(7))
		cond := e.f.ConstructEq(e.v(xc[0]), e.v(yc[0]))
		before := e.f.ConstructSemiJoin(
			e.f.ConstructSelect(x, e.filters(filter)), y, e.filters(cond), &memo.JoinPrivate{},
		)
		want := e.f.ConstructSelect(
			e.f.ConstructSemiJoin(x, y, e.filters(cond), &memo.JoinPrivate{}), e.filters(filter),
		)
		e.expectRewrite(rule, before, want)
	})
	t.Run("rejects anti join", func(t *testing.T) {
		e := newEnv(t)
		x, xc := e.scan("t", 2)
		y, yc := e.scan("u", 2)
		filter := e.f.ConstructEq(e.v(xc[1]), e.num(7))
		cond := e.f.ConstructEq(e.v(xc[0]), e.v(yc[0]))
		before := e.f.ConstructAntiJoin(
			e.f.ConstructSelect(x, e.filters(filter)), y, e.filters(cond), &memo.JoinPrivate{},
		)
		e.expectNoMatch(rule, before)
	})
}
