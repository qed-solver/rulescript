package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestMapFilterIntoJoinLeft(t *testing.T) {
	rule := loadRule(t, "MapFilterIntoJoinLeft")

	t.Run("maps right key reference to left key", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("nn", 2)
		right, rc := e.scan("t", 1)
		keyEq := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
		filter := e.f.ConstructEq(e.v(rc[0]), e.v(lc[1]))
		before := e.f.ConstructInnerJoin(left, right, e.filters(keyEq, filter), &memo.JoinPrivate{})
		want := e.f.ConstructInnerJoin(left, right, e.filters(keyEq, e.f.ConstructEq(e.v(lc[0]), e.v(lc[1]))), &memo.JoinPrivate{})
		e.expectRewrite(rule, before, want)
	})

	t.Run("rejects predicate using right non-key", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("u", 2)
		right, rc := e.scan("t", 2)
		keyEq := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
		filter := e.f.ConstructEq(e.v(rc[1]), e.v(lc[1]))
		before := e.f.ConstructInnerJoin(left, right, e.filters(keyEq, filter), &memo.JoinPrivate{})
		e.expectNoMatch(rule, before)
	})
}
