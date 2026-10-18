package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestRewriteLateralSubquery(t *testing.T) {
	rule := loadRule(t, "RewriteLateralSubquery")

	t.Run("rewrite left apply", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("u", 2)
		right, rc := e.scan("t", 2)
		cond := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
		before := e.f.ConstructLeftJoinApply(left, right, cond, &memo.JoinPrivate{})
		want := e.f.ConstructLeftJoin(left, right, cond, &memo.JoinPrivate{})
		e.expectRewrite(rule, before, want)
	})

	t.Run("ordinary left join is unchanged", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("u", 2)
		right, rc := e.scan("t", 2)
		cond := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
		plan := e.f.ConstructLeftJoin(left, right, cond, &memo.JoinPrivate{})
		e.expectNoMatch(rule, plan)
	})
}
