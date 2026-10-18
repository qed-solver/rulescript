package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSemiJoinJoinTranspose(t *testing.T) {
	rule := loadRule(t, "SemiJoinJoinTranspose")
	t.Run("transposes", func(t *testing.T) {
		e := newEnv(t)
		x, xc := e.scan("u", 1)
		y, yc := e.scan("t", 1)
		z, zc := e.scan("nn", 1)
		c := e.filters(e.f.ConstructEq(e.v(xc[0]), e.v(yc[0])))
		joined := e.f.ConstructInnerJoin(x, y, c, &memo.JoinPrivate{})
		s := e.filters(e.f.ConstructEq(e.v(xc[0]), e.v(zc[0])))
		before := e.f.ConstructSemiJoin(joined, z, s, &memo.JoinPrivate{})
		xz := e.f.ConstructSemiJoin(x, z, s, &memo.JoinPrivate{})
		want := e.f.ConstructInnerJoin(xz, y, c, &memo.JoinPrivate{})
		e.expectRewrite(rule, before, want)
	})
	t.Run("near-miss", func(t *testing.T) {
		e := newEnv(t)
		x, xc := e.scan("u", 1)
		y, yc := e.scan("t", 1)
		z, zc := e.scan("nn", 1)
		c := e.filters(e.f.ConstructEq(e.v(xc[0]), e.v(yc[0])))
		joined := e.f.ConstructInnerJoin(x, y, c, &memo.JoinPrivate{})
		s := e.filters(e.f.ConstructEq(e.v(yc[0]), e.v(zc[0])))
		before := e.f.ConstructSemiJoin(joined, z, s, &memo.JoinPrivate{})
		e.expectNoMatch(rule, before)
	})
}

