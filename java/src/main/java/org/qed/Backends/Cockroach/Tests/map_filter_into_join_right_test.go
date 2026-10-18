package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestMapFilterIntoJoinRight(t *testing.T) {
	rule := loadRule(t, "MapFilterIntoJoinRight")

	t.Run("maps left key predicate to equivalent right key", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("nn", 1)
		right, rc := e.scan("nn", 1)
		keyEq := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
		filter := e.f.ConstructIsNot(e.v(lc[0]), memo.NullSingleton)
		before := e.f.ConstructInnerJoin(left, right, e.filters(keyEq, filter), &memo.JoinPrivate{})
		want := e.f.ConstructInnerJoin(left, right, e.filters(keyEq, e.f.ConstructIsNot(e.v(rc[0]), memo.NullSingleton)), &memo.JoinPrivate{})
		e.expectRewrite(rule, before, want)
	})

	t.Run("rejects predicate on a non-key left column", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("nn", 1)
		right, rc := e.scan("nn", 1)
		keyEq := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
		filter := e.f.ConstructIsNot(e.v(lc[0]), memo.NullSingleton)
		before := e.f.ConstructLeftJoin(left, right, e.filters(keyEq, filter), &memo.JoinPrivate{})
		e.expectNoMatch(rule, before)
	})
}
