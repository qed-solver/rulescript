package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSimplifyRightJoin(t *testing.T) {
	t.Run("matching non-nullable self join", func(t *testing.T) {
		e := newEnv(t)
		rule := loadRule(t, "SimplifyRightJoin")
		left, lc := e.scan("nn", 1)
		right, rc := e.scan("nn", 1)
		cond := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
		before := e.f.ConstructFullJoin(left, right, cond, &memo.JoinPrivate{})
		want := e.f.ConstructLeftJoin(left, right, cond, &memo.JoinPrivate{})
		e.expectRewrite(rule, before, want)
	})

	t.Run("non-equality self join", func(t *testing.T) {
		e := newEnv(t)
		rule := loadRule(t, "SimplifyRightJoin")
		left, lc := e.scan("nn", 1)
		right, rc := e.scan("nn", 1)
		cond := e.filters(e.f.ConstructNe(e.v(lc[0]), e.v(rc[0])))
		plan := e.f.ConstructFullJoin(left, right, cond, &memo.JoinPrivate{})
		e.expectNoMatch(rule, plan)
	})
}
